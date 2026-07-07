package com.ugocalc.app.domain.inference

import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.domain.inference.m0.CameraMetadata
import com.ugocalc.app.domain.inference.m0.PhotoAssessment
import com.ugocalc.app.domain.inference.m0.PhotoQuality
import com.ugocalc.app.domain.inference.m0.QualityGate
import com.ugocalc.app.domain.inference.m1.PerceptionOutcome
import com.ugocalc.app.domain.inference.m2.FoodCategoryRouter
import com.ugocalc.app.domain.inference.m2.SceneRoutingResult
import com.ugocalc.app.domain.inference.m2.StrategyAssignment
import com.ugocalc.app.domain.inference.m2.StrategyRouter
import com.ugocalc.app.domain.inference.m3.PhysicsEngine
import com.ugocalc.app.domain.inference.m3.ScaleContext
import com.ugocalc.app.domain.inference.m3.ScaleResolver
import com.ugocalc.app.domain.inference.m4.ConsistencyVerifier
import com.ugocalc.app.domain.inference.m5.ArbitrationOutcome
import com.ugocalc.app.domain.inference.m7.NutritionCalculator
import com.ugocalc.app.domain.inference.m7.NutritionReport
import com.ugocalc.app.domain.inference.model.AmbiguityResolution
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.FlaggedItem
import com.ugocalc.app.domain.inference.model.FoodCategory
import com.ugocalc.app.domain.inference.model.RevisedFood
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.inference.model.VerificationReport
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.NormalizedFoodEntry

/**
 * Firma di M1 vista dalla pipeline: la stessa di [com.ugocalc.app.domain.inference.m1.UnifiedPerceptionEngine.perceive].
 * Tipizzata a parte (invece di dipendere direttamente dal motore, che richiede AiCallExecutor e
 * quindi client HTTP reali) così [V2AnalysisPipeline] resta testabile con un fake, senza rete.
 */
typealias PerceptionStep = suspend (
    model: AiModel,
    images: List<ByteArray>,
    imageMimeType: String,
    sessionMode: SessionMode,
    angleDescriptions: List<String?>,
    cameraMetadatas: List<CameraMetadata?>,
    calibrations: List<ScaleCalibrationEntity>,
) -> PerceptionOutcome

/** Firma di M5 vista dalla pipeline: la stessa di [com.ugocalc.app.domain.inference.m5.SelectiveArbitrationEngine.arbitrate]. */
typealias ArbitrationStep = suspend (
    model: AiModel,
    images: List<ByteArray>,
    imageMimeType: String,
    scene: SceneGraph,
    flaggati: List<FlaggedItem>,
) -> ArbitrationOutcome

/** Firma di M6 vista dalla pipeline: dai nomi degli alimenti stimati agli abbinamenti nutrizionali trovati (§5 M6). */
typealias NutritionMatchStep = suspend (
    model: AiModel,
    stime: List<WeightEstimate>,
) -> Map<String, NormalizedFoodEntry>

/**
 * Firma di M8 vista dalla pipeline: salva l'analisi completata nell'archivio di telemetria (§5 M8)
 * e restituisce l'id della riga salvata. Tipizzata a parte, come [PerceptionStep], così la pipeline
 * resta testabile senza Room. Il default non registra nulla (id -1): usato dai test e da qualunque
 * chiamante a cui la telemetria non interessa.
 */
typealias TelemetryStep = suspend (
    outcome: PipelineOutcome.Verificato,
    model: AiModel,
    sessionMode: SessionMode,
) -> Long

/**
 * Firma del fattore di correzione da bias vista dalla pipeline (§5 M3, §8, §14): dato il segmento
 * esatto (modalità, strategia, modello, categoria alimentare — vedi [FoodCategory]), il fattore
 * appreso da applicare al peso grezzo di M3, o null se non disponibile (consenso non dato, dati
 * insufficienti o bias instabile). La categoria è stata aggiunta perché la sola strategia mescolava
 * alimenti troppo diversi (pasta e riso condividono `DISTESA_SUL_PIATTO`). Tipizzata a parte, come
 * le altre injection points, così la pipeline resta testabile senza Room. Il default non applica
 * mai alcuna correzione.
 */
typealias BiasFactorStep = suspend (
    sessionMode: SessionMode,
    strategy: EstimationStrategy,
    aiModelId: String,
    foodCategory: FoodCategory,
) -> Double?

/**
 * Firma dello step che recupera le calibrazioni persistenti dell'utente (§13 punto 4): restituisce
 * tutte le calibrazioni note per il tipo richiesto (o tutte se null).
 */
typealias CalibrationsStep = suspend () -> List<ScaleCalibrationEntity>

/**
 * Firma della ricerca locale di CHO/100g per nome alimento, usata per risolvere un'ambiguità
 * residua dopo M5 (§5 M4 punto 2, §13 punto 2): dato un elenco di nomi candidati (il nome
 * originale + le sue alternative), restituisce il CHO/100g trovato nelle tabelle locali per
 * ciascun candidato riconosciuto — nessuna chiamata al modello, per restare nel budget (§4).
 * Il default non risolve mai nulla, così l'ambiguità resta segnalata ma non corretta finché
 * questo step non viene collegato a una ricerca reale.
 */
typealias AmbiguityResolutionStep = suspend (candidati: List<String>) -> Map<String, Double>

/** Una foto della sessione: metriche già calcolate per M0 e byte grezzi da inviare a M1 se accettata. */
class SessionPhoto(val quality: PhotoQuality, val bytes: ByteArray)

/**
 * Orchestra M0 → M1 → M2 → M3 → M4 → M5 → M6 → M7 → M8 (§4). Il risultato mostrabile all'utente
 * (CHO + grassi + spiegazione per alimento, §1) è pronto già a valle di M7; M8 (telemetria, §5)
 * salva l'analisi nell'archivio di apprendimento senza intervenire sul risultato: un fallimento del
 * salvataggio non deve mai far fallire l'analisi già calcolata, per questo [TelemetryStep] non
 * lancia eccezioni verso l'utente, restituisce solo l'id salvato (o -1 se non registrato).
 *
 * Ogni esito di errore gestito lungo la catena (§5) diventa un ramo esplicito di [PipelineOutcome],
 * mai un'eccezione silenziosa: foto tutte scadenti, fallback M1→V1, schema SceneGraph non
 * supportato sono tutti casi che il chiamante deve poter distinguere e mostrare diversamente.
 */
class V2AnalysisPipeline(
        private val qualityGate: QualityGate = QualityGate(),
        private val perceive: PerceptionStep,
        private val strategyRouter: StrategyRouter = StrategyRouter(),
        private val physicsEngine: PhysicsEngine = PhysicsEngine(),
        private val consistencyVerifier: ConsistencyVerifier = ConsistencyVerifier(),
        private val arbitrate: ArbitrationStep = { _, _, _, _, _ -> ArbitrationOutcome.NonDisponibile("arbitrato non configurato") },
        private val matchNutrition: NutritionMatchStep = { _, _ -> emptyMap() },
        private val recordTelemetry: TelemetryStep = { _, _, _ -> -1L },
        private val biasFactor: BiasFactorStep = { _, _, _, _ -> null },
        private val getCalibrations: CalibrationsStep = { emptyList() },
        private val resolveAmbiguity: AmbiguityResolutionStep = { emptyMap() },
        private val recipeRepository: com.ugocalc.app.data.repository.RecipeRepository? = null,
    ) {

    suspend fun run(
        photos: List<SessionPhoto>,
        imageMimeType: String,
        sessionMode: SessionMode,
        model: AiModel,
    ): PipelineOutcome {
        val ingestion = qualityGate.assess(photos.map { it.quality })
        if (ingestion.tutteScadenti) return PipelineOutcome.NessunaFotoValida(ingestion.scartate)

        val fotoValideIds = ingestion.fotoValide.toSet()
        val fotoValide = photos.filter { it.quality.id in fotoValideIds }
        val immaginiValide = fotoValide.map { it.bytes }
        // Stesso ordine e stesso filtro di [immaginiValide] (costruite insieme da [fotoValide]):
        // l'indice N in questa lista descrive SEMPRE la foto N in [immaginiValide] (§13 punto 7).
        val angoliValidi = fotoValide.map { it.quality.angleDescription }
        val metadataValidi = fotoValide.map { it.quality.cameraMetadata }

        val calibrations = getCalibrations()
        val sceneGraph = when (val esito = perceive(model, immaginiValide, imageMimeType, sessionMode, angoliValidi, metadataValidi, calibrations)) {
            is PerceptionOutcome.FallbackAV1 -> return PipelineOutcome.FallbackAV1(esito.motivo)
            is PerceptionOutcome.Riuscita -> esito.sceneGraph
        }


        val assegnazioni = when (val routing = strategyRouter.route(sceneGraph)) {
            is SceneRoutingResult.SchemaNonSupportato ->
                return PipelineOutcome.SchemaNonSupportato(routing.trovata, routing.attesa)
            is SceneRoutingResult.Ok -> routing.assegnazioni
        }

        val scale = ScaleResolver.resolve(sceneGraph.ancore)
        val stime = stimaTutti(sceneGraph, assegnazioni, scale, sessionMode, model)
        val verifica = consistencyVerifier.verify(sceneGraph, stime, scale)

        if (!verifica.richiedeArbitrato) {
            return finalizza(sceneGraph, stime, verifica, model, sessionMode)
        }

        // M5, al massimo una volta per analisi (§5 M5): qualunque sia l'esito, M4 non lo richiama
        // una seconda volta sullo stesso flag.
        return when (val esito = arbitrate(model, immaginiValide, imageMimeType, sceneGraph, verifica.flaggati)) {
            is ArbitrationOutcome.NonDisponibile ->
                finalizza(sceneGraph, stime, verifica, model, sessionMode, arbitratoMotivoMancato = esito.motivo)
            is ArbitrationOutcome.Riuscita -> {
                if (esito.revisioni.isEmpty()) {
                    finalizza(sceneGraph, stime, verifica, model, sessionMode)
                } else {
                    val scenaRivista = applicaRevisioni(sceneGraph, esito.revisioni)
                    val assegnazioniRiviste = when (val routing = strategyRouter.route(scenaRivista)) {
                        is SceneRoutingResult.SchemaNonSupportato ->
                            return PipelineOutcome.SchemaNonSupportato(routing.trovata, routing.attesa)
                        is SceneRoutingResult.Ok -> routing.assegnazioni
                    }
                    val stimeRiviste = stimaTutti(scenaRivista, assegnazioniRiviste, scale, sessionMode, model)
                    val verificaFinale = consistencyVerifier.verify(scenaRivista, stimeRiviste, scale)
                    finalizza(scenaRivista, stimeRiviste, verificaFinale, model, sessionMode)
                }
            }
        }
    }

    /**
     * M6 (abbinamento nutrizionale) + M7 (calcolo CHO/grassi + spiegazione) sulle stime finali,
     * poi M8 (telemetria, §5): il salvataggio avviene sempre sul risultato già pronto, mai lo
     * condiziona.
     */
    private suspend fun finalizza(
        scene: SceneGraph,
        stime: List<WeightEstimate>,
        verifica: VerificationReport,
        model: AiModel,
        sessionMode: SessionMode,
        arbitratoMotivoMancato: String? = null,
    ): PipelineOutcome.Verificato {
        val (sceneFinale, stimeFinali, risoluzioni) = risolviAmbiguitaResidua(scene, stime)
        val verificaFinale = if (risoluzioni.isEmpty()) verifica else verifica.copy(ambiguitaRisolta = risoluzioni)
        val abbinamenti = matchNutrition(model, stimeFinali)
        val nutrizione = NutritionCalculator.calculate(stimeFinali, abbinamenti)
        val outcome = PipelineOutcome.Verificato(sceneFinale, stimeFinali, verificaFinale, nutrizione, arbitratoMotivoMancato, abbinamenti = abbinamenti)
        val telemetryId = recordTelemetry(outcome, model, sessionMode)
        return outcome.copy(telemetryId = telemetryId)
    }

    /**
     * Regola di sicurezza sull'ambiguità residua (§5 M4 punto 2, §13 punto 2): dopo l'eventuale
     * tentativo di M5, gli alimenti che hanno ancora alternative di identificazione aperte vengono
     * risolti scegliendo, tra il nome originale e le alternative, quello con il CHO/100g più alto
     * nelle tabelle locali — mai lasciare che un'ambiguità irrisolta scelga per difetto
     * un'identificazione a CHO più basso, l'errore peggiore per un'app che informa dosi di
     * insulina. Se [resolveAmbiguity] non riconosce nessun candidato, l'alimento resta invariato:
     * l'ambiguità resta segnalata da M4 (`flaggati`) ma non "risolta" per difetto.
     */
    private suspend fun risolviAmbiguitaResidua(
        scene: SceneGraph,
        stime: List<WeightEstimate>,
    ): Triple<SceneGraph, List<WeightEstimate>, List<AmbiguityResolution>> {
        val ambigui = scene.alimenti.filter { it.alternativeIdentificazione.isNotEmpty() }
        if (ambigui.isEmpty()) return Triple(scene, stime, emptyList())

        val risoluzioni = mutableListOf<AmbiguityResolution>()
        val nomeRiscritto = mutableMapOf<String, String>()
        for (food in ambigui) {
            val candidati = listOf(food.nome) + food.alternativeIdentificazione
            val choPerCandidato = resolveAmbiguity(candidati)
            val scelto = candidati
                .mapNotNull { nome -> choPerCandidato[nome]?.let { nome to it } }
                .maxByOrNull { it.second }
                ?: continue
            if (scelto.first != food.nome) {
                nomeRiscritto[food.nome] = scelto.first
                risoluzioni += AmbiguityResolution(food.nome, scelto.first, scelto.second)
            }
        }
        if (nomeRiscritto.isEmpty()) return Triple(scene, stime, risoluzioni)

        val sceneFinale = scene.copy(
            alimenti = scene.alimenti.map { food ->
                val nuovoNome = nomeRiscritto[food.nome] ?: return@map food
                food.copy(nome = nuovoNome, alternativeIdentificazione = emptyList())
            },
        )
        val stimeFinali = stime.map { stima ->
            val nuovoNome = nomeRiscritto[stima.nome] ?: return@map stima
            stima.copy(nome = nuovoNome)
        }
        return Triple(sceneFinale, stimeFinali, risoluzioni)
    }

    private suspend fun stimaTutti(
        scene: SceneGraph,
        assegnazioni: List<StrategyAssignment>,
        scale: ScaleContext,
        sessionMode: SessionMode,
        model: AiModel,
    ): List<WeightEstimate> {
        val foodByName = scene.alimenti.associateBy { it.nome }
        val results = mutableListOf<WeightEstimate>()
        for (assegnazione in assegnazioni) {
            val food = foodByName.getValue(assegnazione.nome)
            val categoria = FoodCategoryRouter.categorize(food.nome)
            val fattore = biasFactor(sessionMode, assegnazione.strategia, model.id, categoria)
            val baseEstimate = physicsEngine.estimate(food, assegnazione.strategia, scale, fattore)
            if (assegnazione.strategia == EstimationStrategy.COMPOSTO_OCCLUSO && recipeRepository != null) {
                val recipe = recipeRepository.findByName(food.nome)
                if (recipe != null && recipe.ingredients.isNotEmpty()) {
                    for (ing in recipe.ingredients) {
                        val proportion = ing.percentage / 100.0
                        results.add(
                            baseEstimate.copy(
                                nome = ing.name,
                                pesoG = baseEstimate.pesoG * proportion,
                                intervallo = baseEstimate.intervallo.copy(
                                    minG = baseEstimate.intervallo.minG * proportion,
                                    maxG = baseEstimate.intervallo.maxG * proportion,
                                ),
                                catenaCalcolo = baseEstimate.catenaCalcolo.copy(
                                    sintesi = "${ing.name}: ${"%.1f".format(baseEstimate.pesoG * proportion)} g — da ricetta ${recipe.name}"
                                ),
                            )
                        )
                    }
                } else {
                    results.add(baseEstimate)
                }
            } else {
                results.add(baseEstimate)
            }
        }
        return results
    }

    /**
     * Applica le revisioni di M5 allo [SceneGraph]: solo gli alimenti presenti in [revisioni] sono
     * toccati, tutti gli altri restano invariati. [RevisedFood.nome] è la chiave di corrispondenza
     * con il nome originale in [SceneGraph.alimenti].
     */
    private fun applicaRevisioni(scene: SceneGraph, revisioni: List<RevisedFood>): SceneGraph {
        val revisioneByNome = revisioni.associateBy { it.nome }
        return scene.copy(
            alimenti = scene.alimenti.map { food ->
                val revisione = revisioneByNome[food.nome] ?: return@map food
                food.copy(
                    nome = revisione.nomeConfermato ?: food.nome,
                    alternativeIdentificazione = if (revisione.risolto) emptyList() else food.alternativeIdentificazione,
                    misureGrezze = revisione.misureGrezze ?: food.misureGrezze,
                    confidenza = revisione.confidenza,
                )
            },
        )
    }
}

/** Esito della pipeline V2 fino a M4 (§4, §5): un ramo per ciascun fallimento gestito lungo la catena. */
sealed interface PipelineOutcome {
    /** M0: nessuna foto utilizzabile, zero chiamate effettuate (§5 M0). */
    data class NessunaFotoValida(val scartate: List<PhotoAssessment>) : PipelineOutcome

    /** M1 ha esaurito il retry: si dichiara esplicitamente la necessità di ripiegare sulla V1 (§5 M1). */
    data class FallbackAV1(val motivo: String) : PipelineOutcome

    /** M2 ha rifiutato uno SceneGraph con schema_version non riconosciuta (§7). */
    data class SchemaNonSupportato(val trovata: Int, val attesa: Int) : PipelineOutcome

    /**
     * M0–M7 completati: stime pesate, report di coerenza (con M5 già applicato se necessario) e
     * report nutrizionale finale (CHO + grassi + spiegazione per alimento, §1). Se M4 aveva
     * richiesto l'arbitrato ma M5 non è stato disponibile (§5 M5 "Fallimento gestito"),
     * [arbitratoMotivoMancato] lo dichiara esplicitamente e [stime] / [verifica] restano quelli
     * pre-arbitrato — mai spacciati per una stima già rivista.
     *
     * [telemetryId] è l'id della riga salvata da M8 (-1 se non registrata, es. nei test che non
     * configurano [TelemetryStep]): mai richiesto per mostrare il risultato all'utente, serve solo
     * a collegare una futura correzione manuale al record giusto (§7, §8).
     *
     * [abbinamenti] è l'esito grezzo di M6 (nome alimento → voce di tabella abbinata, §5 M6): oltre
     * ai totali già pronti in [nutrizione], serve a chi promuove la V2 a flusso visibile (§13 punto
     * 1) per ricostruire una riga modificabile (correzione manuale del match, ricerca online) nello
     * stesso formato già usato dalla V1 (`CalculatedFoodLine`), senza richiamare di nuovo M6.
     */
    data class Verificato(
        val sceneGraph: SceneGraph,
        val stime: List<WeightEstimate>,
        val verifica: VerificationReport,
        val nutrizione: NutritionReport,
        val arbitratoMotivoMancato: String? = null,
        val telemetryId: Long = -1,
        val abbinamenti: Map<String, NormalizedFoodEntry> = emptyMap(),
    ) : PipelineOutcome
}
