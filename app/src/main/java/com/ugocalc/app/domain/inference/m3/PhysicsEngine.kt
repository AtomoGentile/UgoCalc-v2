package com.ugocalc.app.domain.inference.m3

import com.ugocalc.app.domain.inference.m2.StrategyRouter
import com.ugocalc.app.domain.inference.model.CalculationChain
import com.ugocalc.app.domain.inference.model.CalculationSource
import com.ugocalc.app.domain.inference.model.ConfidenceInterval
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.OcclusionDegree
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.ConfidenceScore
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * M3 – Motore fisico (piano V2 §5, §11). Converte le misure grezze di un alimento in grammi con
 * formule deterministiche, secondo la [EstimationStrategy] assegnata da M2. È il cuore della
 * precisione del sistema e il modulo con la copertura di test più estesa.
 *
 * Principio (§2): l'IA non fornisce mai i grammi; qui si calcolano da dimensioni, conteggi e
 * riempimenti su tabelle locali ([PhysicalData]). Ogni risultato porta con sé la catena di calcolo
 * completa (formula, valori, fonti) per essere spiegabile, e un intervallo di confidenza sempre
 * visibile (§9), tanto più ampio quanto più la stima ha dovuto ripiegare su valori di default,
 * misure mancanti, occlusione o scala debole.
 */
class PhysicsEngine(private val data: PhysicalData = PhysicalData.INSTANCE) {

    /**
     * [biasFactor], se presente, è il fattore appreso dal segmento (modalità, strategia, modello) in
     * M8 (§8): moltiplica il peso grezzo e resta dichiarato nella catena di calcolo, mai applicato in
     * silenzio. Null quando non c'è ancora un fattore stabile per il segmento, o il consenso non è
     * dato — in quel caso il peso resta quello grezzo, come prima di questo collegamento.
     */
    fun estimate(
        food: PerceivedFood,
        strategy: EstimationStrategy,
        scale: ScaleContext,
        biasFactor: Double? = null,
    ): WeightEstimate {
        val raw = when (strategy) {
            EstimationStrategy.CONTABILE -> countable(food)
            EstimationStrategy.DISTESA_SUL_PIATTO -> spread(food, scale)
            EstimationStrategy.A_CONTENITORE -> container(food, scale)
            EstimationStrategy.BLOCCO_MISURABILE -> block(food)
            EstimationStrategy.COMPOSTO_OCCLUSO -> composite(food)
            EstimationStrategy.TRASCURABILE -> negligible()
        }
        val confidenceScore = confidenceScore(food, strategy, scale, raw)
        val peso = if (biasFactor != null) raw.pesoG * biasFactor else raw.pesoG
        val chain = if (biasFactor != null) {
            raw.chain.copy(
                valori = raw.chain.valori + ("fattore_correzione" to biasFactor),
                fonti = raw.chain.fonti + CalculationSource("fattore_correzione", biasFactor, "bias appreso (§8)"),
                sintesi = "${raw.chain.sintesi} — corretto ×$biasFactor da bias appreso",
            )
        } else {
            raw.chain
        }
        // Conversione cotto → crudo: divide per la resa di cottura dell'alimento.
        // TRASCURABILE non cuoce mai; per le strategie che usano già densità "a crudo" (unitWeights)
        // la resa sarà 1.0 (default). Il peso crudo è quello confrontabile col ground truth utente.
        val resa = if (strategy == EstimationStrategy.TRASCURABILE) {
            PhysicalValue(1.0, "trascurabile", isDefault = true)
        } else {
            data.cookingYield(food.nome)
        }
        val pesoCrudo = peso / resa.valore
        val chainConResa = if (resa.valore != 1.0) {
            chain.copy(
                valori = chain.valori + ("resa_cottura" to resa.valore),
                fonti = chain.fonti + CalculationSource("resa_cottura", resa.valore, resa.fonte),
                sintesi = "${chain.sintesi} → ${g(pesoCrudo)} g crudo (÷${resa.valore} resa)",
            )
        } else {
            chain
        }
        val uncertaintyFromScore = 1.0 - (confidenceScore.percent / 100.0)
        val interval = ConfidenceInterval(
            minG = (pesoCrudo * (1 - uncertaintyFromScore)).coerceAtLeast(0.0),
            maxG = pesoCrudo * (1 + uncertaintyFromScore),
        )
        return WeightEstimate(
            nome = food.nome,
            ragionamento = food.ragionamento,
            strategia = strategy,
            pesoG = peso,
            pesoCrudoG = pesoCrudo,
            intervallo = interval,
            catenaCalcolo = chainConResa,
            confidenza = confidenceScore,
            usoDefaultFisico = raw.usoDefaultFisico,
        )
    }

    // --- Formule per strategia ---------------------------------------------------------------

    private fun countable(food: PerceivedFood): RawEstimate {
        val conteggio = food.misureGrezze.conteggio
        val count = conteggio ?: 1
        val uw = data.unitWeight(food.nome)
        val peso = count * uw.valore
        return RawEstimate(
            pesoG = peso,
            chain = CalculationChain(
                formula = "conteggio × peso_unitario",
                valori = mapOf("conteggio" to count.toDouble(), "peso_unitario_g" to uw.valore),
                fonti = listOf(CalculationSource("peso_unitario", uw.valore, uw.fonte)),
                sintesi = "${g(peso)} g — $count × ${g(uw.valore)} g (${uw.fonte})",
            ),
            usoDefaultFisico = uw.isDefault,
            usoFallbackMisura = conteggio == null,
        )
    }

    private fun spread(food: PerceivedFood, scale: ScaleContext): RawEstimate {
        val m = food.misureGrezze
        val frazione = m.frazionePiatto ?: FALLBACK_FRAZIONE_PIATTO
        val altezza = m.altezzaCm ?: FALLBACK_ALTEZZA_CM
        val d = scale.plateDiameterCm
        val dens = data.density(food.nome)
        val fontePiatto = if (scale.plateDiameterIsDefault) "piatto di default" else "ancora piatto"

        // Calcolo del volume in base alla forma del piatto
        val (baseVolume, volumeFormula) = when (scale.plateShapeType.lowercase()) {
            "conico" -> {
                val dBottom = scale.plateBottomDiameterCm ?: d
                val rTop = d / 2.0
                val rBottom = dBottom / 2.0
                // Se nota la profondità calibrata del piatto, interpola il raggio superiore effettivo alla quota altezza
                val rTopEff = if (scale.plateHeightCm != null && scale.plateHeightCm > 0.0) {
                    val ratio = (altezza / scale.plateHeightCm).coerceIn(0.0, 1.0)
                    rBottom + (rTop - rBottom) * ratio
                } else {
                    rTop
                }
                // Volume di tronco di cono moltiplicato per la frazione di piatto occupata
                val v = frazione * (altezza / 3.0) * PI * (rTopEff * rTopEff + rTopEff * rBottom + rBottom * rBottom)
                v to "tronco_di_cono"
            }
            else -> {
                // Cilindro (default)
                val area = PI * (d / 2.0) * (d / 2.0)
                (frazione * area * altezza) to "cilindro"
            }
        }

        val profileFactor = moundProfileFactor(m.profiloCumulo)
        val volume = baseVolume * profileFactor
        val profileLabel = if (profileFactor != 1.0) " × profilo(${m.profiloCumulo}=$profileFactor)" else ""
        val extraValori = if (m.profiloCumulo != null) mapOf("profilo_cumulo_fattore" to profileFactor) else emptyMap()
        val shapeDesc = if (volumeFormula == "tronco_di_cono") "${pct(frazione)} di piatto conico" else "${pct(frazione)} di piatto"

        // Per densità BULK (pasta, riso, cereali) il valore CREA include già gli spazi d'aria tra i
        // pezzi: applicare anche "riempimento" conterebbe la porosità due volte.
        return if (dens.isBulkDensity) {
            val peso = volume * dens.valore
            RawEstimate(
                pesoG = peso,
                chain = CalculationChain(
                    formula = when (volumeFormula) {
                        "tronco_di_cono" -> "frazione × volume_tronco_cono$profileLabel × densità_bulk"
                        else -> "frazione × π·(Ø/2)² × altezza$profileLabel × densità_bulk"
                    },
                    valori = mapOf(
                        "frazione_piatto" to frazione,
                        "diametro_piatto_cm" to d,
                        "altezza_cm" to altezza,
                        "volume_cm3" to volume,
                        "densità" to dens.valore,
                    ) + extraValori,
                    fonti = listOf(
                        CalculationSource("densità_bulk", dens.valore, dens.fonte),
                        CalculationSource("diametro_piatto", d, fontePiatto),
                    ),
                    sintesi = "${g(peso)} g — $shapeDesc Ø${g(d)}, alt ${cm(altezza)} cm, ${dens.fonte} (bulk, no riempimento)",
                ),
                usoDefaultFisico = dens.isDefault,
                usoFallbackMisura = (m.frazionePiatto == null) || (m.altezzaCm == null),
            )
        } else {
            val riemp = m.riempimento ?: FALLBACK_RIEMPIMENTO
            val peso = volume * riemp * dens.valore
            RawEstimate(
                pesoG = peso,
                chain = CalculationChain(
                    formula = when (volumeFormula) {
                        "tronco_di_cono" -> "frazione × volume_tronco_cono$profileLabel × riempimento × densità"
                        else -> "frazione × π·(Ø/2)² × altezza$profileLabel × riempimento × densità"
                    },
                    valori = mapOf(
                        "frazione_piatto" to frazione,
                        "diametro_piatto_cm" to d,
                        "altezza_cm" to altezza,
                        "volume_cm3" to volume,
                        "riempimento" to riemp,
                        "densità" to dens.valore,
                    ) + extraValori,
                    fonti = listOf(
                        CalculationSource("densità", dens.valore, dens.fonte),
                        CalculationSource("diametro_piatto", d, fontePiatto),
                    ),
                    sintesi = "${g(peso)} g — $shapeDesc Ø${g(d)}, alt ${cm(altezza)} cm, ${dens.fonte}",
                ),
                usoDefaultFisico = dens.isDefault,
                usoFallbackMisura = (m.frazionePiatto == null) || (m.altezzaCm == null) || (m.riempimento == null),
            )
        }
    }

    /** Fattore geometrico di forma del cumulo rispetto al cilindro circoscritto; null mantiene 1.0 per retrocompatibilità. */
    private fun moundProfileFactor(profilo: String?): Double = when (profilo?.trim()?.lowercase()) {
        "cono" -> 0.45
        "cupola" -> 0.65
        "piatto" -> 1.0
        else -> 1.0
    }

    private fun container(food: PerceivedFood, scale: ScaleContext): RawEstimate {
        val m = food.misureGrezze
        val cap = resolveContainerCapacity(m, scale)
        val frazione = m.frazioneRiempimento ?: FALLBACK_FRAZIONE_RIEMPIMENTO
        val dens = data.density(food.nome)
        val peso = cap.valore * frazione * dens.valore
        val hasKnownCapacity = !cap.isDefault && (
            (m.capacitaContenitoreMl != null && m.capacitaContenitoreMl > 0.0) ||
                cap.fonte.startsWith("calibrazione")
            )
        return RawEstimate(
            pesoG = peso,
            chain = CalculationChain(
                formula = "capacità × frazione_riempimento × densità",
                valori = mapOf(
                    "capacita_ml" to cap.valore,
                    "frazione_riempimento" to frazione,
                    "densità" to dens.valore,
                ),
                fonti = listOf(
                    CalculationSource("capacità", cap.valore, cap.fonte),
                    CalculationSource("densità", dens.valore, dens.fonte),
                ),
                sintesi = "${g(peso)} g — ${cap.fonte} ${g(cap.valore)} ml al ${pct(frazione)}, ${dens.fonte}",
            ),
            usoDefaultFisico = cap.isDefault || dens.isDefault,
            usoFallbackMisura = m.frazioneRiempimento == null || (m.contenitore == null && !hasKnownCapacity),
        )
    }

    private fun resolveContainerCapacity(
        m: com.ugocalc.app.domain.inference.model.RawMeasurements,
        scale: ScaleContext,
    ): PhysicalValue {
        m.capacitaContenitoreMl?.takeIf { it > 0.0 }?.let { ml ->
            return PhysicalValue(ml, "capacità contenitore (M1)", isDefault = false)
        }

        val contenitore = m.contenitore?.trim().orEmpty()
        if (contenitore.isNotEmpty() && scale.calibratedCapacitiesByName.isNotEmpty()) {
            val norm = StrategyRouter.normalize(contenitore)
            scale.calibratedCapacitiesByName[norm]?.let { ml ->
                return PhysicalValue(ml, "calibrazione contenitore ($contenitore)", isDefault = false)
            }
            scale.calibratedCapacitiesByName.entries
                .firstOrNull { (k, _) -> k.isNotEmpty() && (norm.contains(k) || k.contains(norm)) }
                ?.let { (k, ml) ->
                    return PhysicalValue(ml, "calibrazione contenitore ($k)", isDefault = false)
                }
        }

        scale.calibratedCapacityMl?.takeIf { it > 0.0 }?.let { ml ->
            return PhysicalValue(ml, "calibrazione ancora contenitore", isDefault = false)
        }

        return data.capacity(contenitore)
    }

    private fun block(food: PerceivedFood): RawEstimate {
        val m = food.misureGrezze
        val count = m.conteggio ?: 1
        val dens = data.density(food.nome)
        val (volume, formaUsata, mancano) = blockVolume(m)
        val peso = count * volume * dens.valore
        val sintesiPrefix = if (count > 1) "$count × " else ""
        return RawEstimate(
            pesoG = peso,
            chain = CalculationChain(
                formula = "${if (count > 1) "conteggio × " else ""}$formaUsata × densità",
                valori = mapOf(
                    "conteggio" to count.toDouble(),
                    "volume_cm3" to volume,
                    "densità" to dens.valore
                ),
                fonti = listOf(CalculationSource("densità", dens.valore, dens.fonte)),
                sintesi = "${g(peso)} g — $sintesiPrefix$formaUsata ${g(volume)} cm³, ${dens.fonte}",
            ),
            usoDefaultFisico = dens.isDefault,
            usoFallbackMisura = mancano,
        )
    }

    private fun composite(food: PerceivedFood): RawEstimate {
        // Peso totale geometrico come un blocco; la ripartizione da ricetta è demandata a M6/M7.
        val base = block(food)
        return base.copy(
            chain = base.chain.copy(
                sintesi = base.chain.sintesi + " — composto, composizione da ricetta a valle",
            ),
        )
    }

    private fun negligible(): RawEstimate = RawEstimate(
        pesoG = TRASCURABILE_G,
        chain = CalculationChain(
            formula = "valore fisso di riferimento",
            valori = mapOf("peso_fisso_g" to TRASCURABILE_G),
            fonti = listOf(CalculationSource("riferimento", TRASCURABILE_G, "valore trascurabile")),
            sintesi = "${g(TRASCURABILE_G)} g — quantità trascurabile, dichiarata incerta",
        ),
        usoDefaultFisico = false,
        usoFallbackMisura = false,
    )

    /** Volume di un blocco secondo la forma dichiarata; ripiega su un blocco di default se mancano dati. */
    private fun blockVolume(m: com.ugocalc.app.domain.inference.model.RawMeasurements): Triple<Double, String, Boolean> {
        val forma = (m.forma ?: "").lowercase()
        val h = m.altezzaCm
        return when {
            // Cilindro: diametro + altezza
            (forma.contains("cilindro") || (m.diametroCm != null && m.lunghezzaCm == null)) &&
                m.diametroCm != null && h != null -> {
                val r = m.diametroCm / 2.0
                Triple(PI * r * r * h, "cilindro", false)
            }
            // Cuneo/fetta/spicchio: mezzo parallelepipedo
            (forma.contains("cuneo") || forma.contains("fetta") || forma.contains("spicchio")) &&
                m.lunghezzaCm != null && m.larghezzaCm != null && h != null ->
                Triple(0.5 * m.lunghezzaCm * m.larghezzaCm * h, "cuneo", false)
            // Parallelepipedo: lunghezza × larghezza × altezza
            m.lunghezzaCm != null && m.larghezzaCm != null && h != null ->
                Triple(m.lunghezzaCm * m.larghezzaCm * h, "parallelepipedo", false)
            // Dati insufficienti: blocco di default
            else -> Triple(
                FALLBACK_BLOCCO.first * FALLBACK_BLOCCO.second * FALLBACK_BLOCCO.third,
                "blocco stimato",
                true,
            )
        }
    }

    // --- Confidenza (modello moltiplicativo) ------------------------------------------------

    private fun confidenceScore(
        food: PerceivedFood,
        strategy: EstimationStrategy,
        scale: ScaleContext,
        raw: RawEstimate,
    ): ConfidenceScore {
        var confidence = baseConfidence(strategy)

        // Fattori moltiplicativi (0.0 .. 1.0)
        confidence *= when (food.occlusione) {
            OcclusionDegree.NESSUNA -> 1.0
            OcclusionDegree.PARZIALE -> 0.88
            OcclusionDegree.ELEVATA -> 0.72
        }

        confidence *= when (food.confidenza) {
            Confidence.ALTO -> 1.0
            Confidence.MEDIO -> 0.93
            Confidence.BASSO -> 0.80
        }

        confidence *= when (scale.quality) {
            Confidence.ALTO -> 1.0
            Confidence.MEDIO -> 0.95
            Confidence.BASSO -> 0.87
        }

        if (raw.usoDefaultFisico) confidence *= 0.87
        if (raw.usoFallbackMisura) confidence *= 0.83
        if (strategy == EstimationStrategy.DISTESA_SUL_PIATTO && scale.plateDiameterIsDefault) confidence *= 0.87

        val percent = (confidence * 100).toInt().coerceIn(0, 100)
        return ConfidenceScore(percent)
    }

    private fun baseConfidence(s: EstimationStrategy): Double = when (s) {
        EstimationStrategy.CONTABILE -> 0.92
        EstimationStrategy.DISTESA_SUL_PIATTO -> 0.80
        EstimationStrategy.A_CONTENITORE -> 0.83
        EstimationStrategy.BLOCCO_MISURABILE -> 0.83
        EstimationStrategy.COMPOSTO_OCCLUSO -> 0.70
        EstimationStrategy.TRASCURABILE -> 0.50
    }

    private data class RawEstimate(
        val pesoG: Double,
        val chain: CalculationChain,
        val usoDefaultFisico: Boolean,
        val usoFallbackMisura: Boolean,
    )

    private companion object {
        const val FALLBACK_FRAZIONE_PIATTO = 0.3
        const val FALLBACK_ALTEZZA_CM = 1.5
        const val FALLBACK_RIEMPIMENTO = 0.8
        const val FALLBACK_FRAZIONE_RIEMPIMENTO = 0.7
        const val TRASCURABILE_G = 5.0
        val FALLBACK_BLOCCO = Triple(10.0, 8.0, 2.5) // parallelepipedo di riserva (cm)

        fun g(x: Double): Int = x.roundToInt()
        fun cm(x: Double): String = ((x * 10).roundToInt() / 10.0).toString()
        fun pct(x: Double): String = "${(x * 100).roundToInt()}%"
    }
}

/**
 * Contesto di scala per M3: il diametro del piatto usato per le stime "distese" e una valutazione
 * complessiva della qualità della scala. Risolto dalle ancore dello [com.ugocalc.app.domain.inference.model.SceneGraph] (§8) o ripiegato
 * su valori di default con qualità bassa quando nessuna ancora è disponibile (§9).
 */
data class ScaleContext(
    val plateDiameterCm: Double = PhysicalData.DEFAULT_PLATE_DIAMETER_CM,
    val plateDiameterIsDefault: Boolean = true,
    val quality: Confidence = Confidence.BASSO,
    /** Forma del piatto: "cilindro" (default) o "conico". */
    val plateShapeType: String = "cilindro",
    /** Diametro del fondo del piatto conico in centimetri (ignorato se plateShapeType non è "conico"). */
    val plateBottomDiameterCm: Double? = null,
    /** Profondità/altezza calibrata del piatto conico in centimetri, se nota, per interpolare il raggio superiore effettivo. */
    val plateHeightCm: Double? = null,
    /** Capacità calibrata in ml dell'ancora scelta (se è un contenitore calibrato). */
    val calibratedCapacityMl: Double? = null,
    /** Mappa delle capacità calibrate (nome normalizzato -> ml) disponibili tra le calibrazioni utente. */
    val calibratedCapacitiesByName: Map<String, Double> = emptyMap(),
)
