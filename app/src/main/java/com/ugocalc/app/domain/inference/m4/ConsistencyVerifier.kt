package com.ugocalc.app.domain.inference.m4

import com.ugocalc.app.domain.inference.m3.ScaleContext
import com.ugocalc.app.domain.inference.model.ConstraintCheck
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.FlagType
import com.ugocalc.app.domain.inference.model.FlaggedItem
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.VerificationReport
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.Confidence
import kotlin.math.PI

/**
 * M4 – Verifica di coerenza (piano V2 §5, §7). Modulo deterministico, gratuito e **sempre attivo**:
 * non chiama mai il modello. Controlla che le stime di M3 rispettino vincoli plausibili e produce un
 * [VerificationReport]. Gli alimenti che falliscono un vincolo finiscono tra i [VerificationReport]
 * `flaggati`: solo quelli innescano M5 (Arbitrato selettivo), un'unica chiamata al modello. Nel caso
 * tipico nessun alimento è flaggato e M5 non parte affatto (§4, budget di due chiamate).
 *
 * Tre famiglie di vincoli:
 * - **fisici**: peso entro un intervallo tipico; densità superficiale del piatto (g/cm²) plausibile;
 * - **di scena**: la somma delle frazioni di piatto coperte non eccede il piatto;
 * - **di identità**: un alimento con identificazioni alternative e confidenza non alta resta ambiguo.
 */
class ConsistencyVerifier {

    fun verify(
        scene: SceneGraph,
        estimates: List<WeightEstimate>,
        scale: ScaleContext,
    ): VerificationReport {
        val foodByName: Map<String, PerceivedFood> = scene.alimenti.associateBy { it.nome }
        val plateArea = PI * (scale.plateDiameterCm / 2.0) * (scale.plateDiameterCm / 2.0)

        val controlli = mutableListOf<ConstraintCheck>()
        val flaggati = mutableListOf<FlaggedItem>()

        // --- Vincolo fisico: peso entro un intervallo tipico ---
        val pesiFuoriTipico = estimates.filter { it.pesoG < PESO_MIN_G || it.pesoG > PESO_MAX_G }
        controlli += ConstraintCheck(
            nome = "peso entro intervallo tipico",
            superato = pesiFuoriTipico.isEmpty(),
            dettaglio = if (pesiFuoriTipico.isEmpty()) "tutti tra $PESO_MIN_G e $PESO_MAX_G g"
            else pesiFuoriTipico.joinToString { "${it.nome}=${it.pesoG.toInt()}g" },
        )
        pesiFuoriTipico.forEach {
            flaggati += FlaggedItem(it.nome, "peso ${it.pesoG.toInt()} g fuori dall'intervallo tipico", FlagType.VINCOLO_FISICO)
        }

        // --- Vincolo di BUON SENSO (INTUIZIONE AI): confronto M3 vs M1 (§4, §5 M4) ---
        val pesiFuoriIntuizione = estimates.filter { est ->
            val limiti = foodByName[est.nome]?.limitiBuonSenso ?: return@filter false
            est.pesoG < limiti.pesoMinG || est.pesoG > limiti.pesoMaxG
        }
        controlli += ConstraintCheck(
            nome = "buon senso (intuizione AI)",
            superato = pesiFuoriIntuizione.isEmpty(),
            dettaglio = if (pesiFuoriIntuizione.isEmpty()) "coerente con l'intuizione"
            else pesiFuoriIntuizione.joinToString { est ->
                val l = foodByName[est.nome]!!.limitiBuonSenso!!
                "${est.nome}: calcolato ${est.pesoG.toInt()}g vs plausibile ${l.pesoMinG.toInt()}-${l.pesoMaxG.toInt()}g"
            }
        )
        pesiFuoriIntuizione.forEach { est ->
            val l = foodByName[est.nome]!!.limitiBuonSenso!!
            flaggati += FlaggedItem(
                est.nome,
                "calcolo geometrico (${est.pesoG.toInt()}g) incoerente con l'intuizione AI (${l.pesoMinG.toInt()}-${l.pesoMaxG.toInt()}g)",
                FlagType.VINCOLO_FISICO
            )
        }

        // --- Vincolo fisico: densità superficiale del piatto (solo alimenti distesi) ---
        val densitaFuori = estimates.filter { est ->
            if (est.strategia != EstimationStrategy.DISTESA_SUL_PIATTO) return@filter false
            val frazione = foodByName[est.nome]?.misureGrezze?.frazionePiatto ?: return@filter false
            val areaCoperta = frazione * plateArea
            if (areaCoperta <= 0.0) return@filter false
            val gPerCm2 = est.pesoG / areaCoperta
            gPerCm2 < AREAL_MIN_G_CM2 || gPerCm2 > AREAL_MAX_G_CM2
        }
        controlli += ConstraintCheck(
            nome = "densità superficiale piatto plausibile",
            superato = densitaFuori.isEmpty(),
            dettaglio = if (densitaFuori.isEmpty()) "tra $AREAL_MIN_G_CM2 e $AREAL_MAX_G_CM2 g/cm²"
            else densitaFuori.joinToString { it.nome },
        )
        densitaFuori.forEach {
            flaggati += FlaggedItem(it.nome, "densità superficiale del piatto implausibile", FlagType.VINCOLO_FISICO)
        }

        // --- Vincolo di scena: copertura totale del piatto ---
        val distese = scene.alimenti.filter { it.misureGrezze.frazionePiatto != null }
        val coperturaTotale = distese.sumOf { it.misureGrezze.frazionePiatto ?: 0.0 }
        val coperturaOk = coperturaTotale <= COPERTURA_MAX
        controlli += ConstraintCheck(
            nome = "copertura totale del piatto",
            superato = coperturaOk,
            dettaglio = "somma frazioni = ${"%.2f".format(coperturaTotale)}",
        )
        if (!coperturaOk) {
            // Segnala il contributore maggiore come rappresentante dell'incoerenza di scena.
            distese.maxByOrNull { it.misureGrezze.frazionePiatto ?: 0.0 }?.let {
                flaggati += FlaggedItem(
                    it.nome,
                    "copertura del piatto incoerente (somma frazioni = ${"%.2f".format(coperturaTotale)})",
                    FlagType.VINCOLO_SCENA,
                )
            }
        }

        // --- Vincolo di identità: alternative con confidenza non alta ---
        // Nota: il raffinamento "cambia sensibilmente i carboidrati" richiede le tabelle di M6 e sarà
        // aggiunto quando M6 sarà collegato; qui si usa il proxy alternative + confidenza non alta.
        val ambigui = scene.alimenti.filter {
            it.alternativeIdentificazione.isNotEmpty() && it.confidenza != Confidence.ALTO
        }
        controlli += ConstraintCheck(
            nome = "identificazione non ambigua",
            superato = ambigui.isEmpty(),
            dettaglio = if (ambigui.isEmpty()) "nessuna ambiguità" else ambigui.joinToString { it.nome },
        )
        ambigui.forEach {
            flaggati += FlaggedItem(
                it.nome,
                "identificazione ambigua, alternative: ${it.alternativeIdentificazione.joinToString()}",
                FlagType.AMBIGUITA_IDENTITA,
            )
        }

        return VerificationReport(controlli = controlli, flaggati = flaggati)
    }

    companion object {
        const val PESO_MIN_G = 1.0
        const val PESO_MAX_G = 2000.0
        const val AREAL_MIN_G_CM2 = 0.05
        const val AREAL_MAX_G_CM2 = 3.0
        const val COPERTURA_MAX = 1.2
    }
}
