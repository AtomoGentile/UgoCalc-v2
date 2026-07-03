package com.ugocalc.app.domain.inference

import com.ugocalc.app.domain.inference.m0.PhotoAssessment
import com.ugocalc.app.domain.inference.m0.PhotoQuality
import com.ugocalc.app.domain.inference.m0.QualityGate
import com.ugocalc.app.domain.inference.m1.PerceptionOutcome
import com.ugocalc.app.domain.inference.m2.SceneRoutingResult
import com.ugocalc.app.domain.inference.m2.StrategyAssignment
import com.ugocalc.app.domain.inference.m2.StrategyRouter
import com.ugocalc.app.domain.inference.m3.PhysicsEngine
import com.ugocalc.app.domain.inference.m3.ScaleContext
import com.ugocalc.app.domain.inference.m3.ScaleResolver
import com.ugocalc.app.domain.inference.m4.ConsistencyVerifier
import com.ugocalc.app.domain.inference.m5.ArbitrationOutcome
import com.ugocalc.app.domain.inference.model.FlaggedItem
import com.ugocalc.app.domain.inference.model.RevisedFood
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.inference.model.VerificationReport
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.AiModel

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
) -> PerceptionOutcome

/** Firma di M5 vista dalla pipeline: la stessa di [com.ugocalc.app.domain.inference.m5.SelectiveArbitrationEngine.arbitrate]. */
typealias ArbitrationStep = suspend (
    model: AiModel,
    images: List<ByteArray>,
    imageMimeType: String,
    scene: SceneGraph,
    flaggati: List<FlaggedItem>,
) -> ArbitrationOutcome

/** Una foto della sessione: metriche già calcolate per M0 e byte grezzi da inviare a M1 se accettata. */
class SessionPhoto(val quality: PhotoQuality, val bytes: ByteArray)

/**
 * Orchestra M0 → M1 → M2 → M3 → M4 → M5 (§4). M6 (nutrition engine), M7 (calcolo CHO/grassi) e M8
 * (telemetria) non esistono ancora: la pipeline si ferma al [VerificationReport] finale (post
 * arbitrato se scattato), che è già un punto di composizione utile e verificabile da solo.
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
        val immaginiValide = photos.filter { it.quality.id in fotoValideIds }.map { it.bytes }

        val sceneGraph = when (val esito = perceive(model, immaginiValide, imageMimeType, sessionMode)) {
            is PerceptionOutcome.FallbackAV1 -> return PipelineOutcome.FallbackAV1(esito.motivo)
            is PerceptionOutcome.Riuscita -> esito.sceneGraph
        }

        val assegnazioni = when (val routing = strategyRouter.route(sceneGraph)) {
            is SceneRoutingResult.SchemaNonSupportato ->
                return PipelineOutcome.SchemaNonSupportato(routing.trovata, routing.attesa)
            is SceneRoutingResult.Ok -> routing.assegnazioni
        }

        val scale = ScaleResolver.resolve(sceneGraph.ancore)
        val stime = stimaTutti(sceneGraph, assegnazioni, scale)
        val verifica = consistencyVerifier.verify(sceneGraph, stime, scale)

        if (!verifica.richiedeArbitrato) {
            return PipelineOutcome.Verificato(sceneGraph, stime, verifica)
        }

        // M5, al massimo una volta per analisi (§5 M5): qualunque sia l'esito, M4 non lo richiama
        // una seconda volta sullo stesso flag.
        return when (val esito = arbitrate(model, immaginiValide, imageMimeType, sceneGraph, verifica.flaggati)) {
            is ArbitrationOutcome.NonDisponibile ->
                PipelineOutcome.Verificato(sceneGraph, stime, verifica, arbitratoMotivoMancato = esito.motivo)
            is ArbitrationOutcome.Riuscita -> {
                if (esito.revisioni.isEmpty()) {
                    PipelineOutcome.Verificato(sceneGraph, stime, verifica)
                } else {
                    val scenaRivista = applicaRevisioni(sceneGraph, esito.revisioni)
                    val assegnazioniRiviste = when (val routing = strategyRouter.route(scenaRivista)) {
                        is SceneRoutingResult.SchemaNonSupportato ->
                            return PipelineOutcome.SchemaNonSupportato(routing.trovata, routing.attesa)
                        is SceneRoutingResult.Ok -> routing.assegnazioni
                    }
                    val stimeRiviste = stimaTutti(scenaRivista, assegnazioniRiviste, scale)
                    val verificaFinale = consistencyVerifier.verify(scenaRivista, stimeRiviste, scale)
                    PipelineOutcome.Verificato(scenaRivista, stimeRiviste, verificaFinale)
                }
            }
        }
    }

    private fun stimaTutti(
        scene: SceneGraph,
        assegnazioni: List<StrategyAssignment>,
        scale: ScaleContext,
    ): List<WeightEstimate> {
        val foodByName = scene.alimenti.associateBy { it.nome }
        return assegnazioni.map { assegnazione ->
            val food = foodByName.getValue(assegnazione.nome)
            physicsEngine.estimate(food, assegnazione.strategia, scale)
        }
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
     * M0–M4 completati, con M5 già applicato se era necessario: stime pesate e report di coerenza
     * finali, pronti per M6. Se M4 aveva richiesto l'arbitrato ma M5 non è stato disponibile
     * (§5 M5 "Fallimento gestito"), [arbitratoMotivoMancato] lo dichiara esplicitamente e [stime]
     * / [verifica] restano quelli pre-arbitrato — mai spacciati per una stima già rivista.
     */
    data class Verificato(
        val sceneGraph: SceneGraph,
        val stime: List<WeightEstimate>,
        val verifica: VerificationReport,
        val arbitratoMotivoMancato: String? = null,
    ) : PipelineOutcome
}
