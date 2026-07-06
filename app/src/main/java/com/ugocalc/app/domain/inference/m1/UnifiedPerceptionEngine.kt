package com.ugocalc.app.domain.inference.m1

import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.data.remote.ai.AiCallExecutor
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.AiModel
import javax.inject.Inject

/**
 * M1 – Percezione unificata (piano V2 §4, §5). Unica chiamata multimodale per l'intera sessione:
 * riceve tutte le foto già filtrate da M0 e produce lo [SceneGraph] che M2 userà per instradare le
 * strategie. Riusa [AiCallExecutor] (condiviso con la V1): stesso retry sugli errori transitori
 * noti, stessa normalizzazione degli errori, stessa decodifica JSON rigida.
 *
 * Budget: un solo modello per analisi (§3, due chiamate totali nel caso normale: M1 + M6).
 */
class UnifiedPerceptionEngine @Inject constructor(
    private val executor: AiCallExecutor,
) {

    /**
     * Esegue la percezione unificata. Il retry sul JSON malformato/servizio non disponibile è già
     * gestito da [AiCallExecutor] (un tentativo di recupero, §5 M1); se anche quello fallisce,
     * l'analisi non prosegue con M2–M8: il chiamante deve ripiegare sulla stima diretta in stile V1
     * ([PerceptionOutcome.FallbackAV1]), dichiarata esplicitamente come tale e mai spacciata per un
     * risultato della pipeline V2.
     */
    suspend fun perceive(
        model: AiModel,
        images: List<ByteArray>,
        imageMimeType: String,
        sessionMode: SessionMode,
        angleDescriptions: List<String?> = emptyList(),
        calibrations: List<ScaleCalibrationEntity> = emptyList(),
    ): PerceptionOutcome {
        val imageParts = executor.encodeImages(images, imageMimeType)
        val prompt = PerceptionPrompt.build(sessionMode, angleDescriptions, calibrations)
        return try {
            val sceneGraph = executor.completeJson<SceneGraph>(model, prompt, imageParts)
            PerceptionOutcome.Riuscita(sceneGraph)
        } catch (e: AiCallException) {
            PerceptionOutcome.FallbackAV1(motivo = e.message ?: "errore sconosciuto nella chiamata al modello")
        }
    }
}

/**
 * Esito di M1. [FallbackAV1] non è un errore silenzioso: il motivo va mostrato come tale
 * all'utente e l'analisi risultante va dichiarata "stima diretta" (stile V1), mai presentata come
 * una stima V2 completa (§5 M1 "Fallimento gestito").
 */
sealed interface PerceptionOutcome {
    data class Riuscita(val sceneGraph: SceneGraph) : PerceptionOutcome
    data class FallbackAV1(val motivo: String) : PerceptionOutcome
}
