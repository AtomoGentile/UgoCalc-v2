package com.ugocalc.app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.di.ShadowAnalysisScope
import com.ugocalc.app.domain.inference.PipelineOutcome
import com.ugocalc.app.domain.inference.SessionPhoto
import com.ugocalc.app.domain.inference.V2AnalysisPipelineFactory
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.domain.usecase.RunVisionForModelsUseCase
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.AnalysisNotificationHelper
import com.ugocalc.app.util.GyroscopeMotionTracker
import com.ugocalc.app.util.computePhotoQuality
import com.ugocalc.app.util.extractCameraMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CAPTURED_IMAGE_MIME_TYPE = "image/jpeg"

/** [capturedAtEpochMillis] approssima l'istante dello scatto (§16): la fotocamera di sistema non offre alcun callback nell'istante esatto, si usa il timestamp del file scritto su disco. */
data class CapturedPhoto(val bytes: ByteArray, val capturedAtEpochMillis: Long)

data class CameraUiState(
    val capturedImages: List<CapturedPhoto> = emptyList(),
    val isAnalyzing: Boolean = false,
    val analysisDone: Boolean = false,
)

@HiltViewModel
class CameraCaptureViewModel @Inject constructor(
    private val runVisionForModels: RunVisionForModelsUseCase,
    private val sessionStore: AnalysisSessionStore,
    private val notificationHelper: AnalysisNotificationHelper,
    private val settingsRepository: SettingsRepository,
    private val v2PipelineFactory: V2AnalysisPipelineFactory,
    private val benchmarkRepository: BenchmarkRepository,
    private val gyroscopeTracker: GyroscopeMotionTracker,
    @ShadowAnalysisScope private val shadowScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    /** Scatto avvenuto: si aggiunge alla galleria di foto di questa sessione (più scatti per un'analisi più efficace). */
    fun onPhotoCaptured(imageBytes: ByteArray, capturedAtEpochMillis: Long) {
        _uiState.update { it.copy(capturedImages = it.capturedImages + CapturedPhoto(imageBytes, capturedAtEpochMillis)) }
    }

    /** L'utente scarta una foto già scattata dalla galleria della sessione corrente. */
    fun removePhoto(index: Int) {
        _uiState.update { state ->
            state.copy(capturedImages = state.capturedImages.filterIndexed { i, _ -> i != index })
        }
    }

    /** Tutte le foto confermate → invio al Passaggio 1 per ciascun modello selezionato (§2.4 punto 3). */
    fun confirmPhotos(models: List<AiModel>) {
        val capturedPhotos = _uiState.value.capturedImages
        if (capturedPhotos.isEmpty()) return
        val images = capturedPhotos.map { it.bytes }
        sessionStore.setCapturedImages(images, CAPTURED_IMAGE_MIME_TYPE)
        _uiState.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            val outcomes = runVisionForModels(models, images, CAPTURED_IMAGE_MIME_TYPE)
            sessionStore.setVisionOutcomes(outcomes)
            _uiState.update { it.copy(isAnalyzing = false, analysisDone = true) }
            notificationHelper.notifyAnalysisCompletedIfBackgrounded()
            recordV1EstimateForBenchmarkIfEnabled(outcomes, models)
        }
        runV2ShadowAnalysisIfEnabled(capturedPhotos, models)
    }

    /**
     * Salva subito i pesi grezzi per alimento stimati da V1, per il benchmark V1/V2 (§10, §13 punto
     * 5): senza questo, un pasto per cui l'utente non preme "Salva" in cronologia sparirebbe anche
     * dal confronto. Usa il modello di riferimento (il primo con successo), stesso criterio della V2
     * shadow, così i due motori restano confrontabili sullo stesso modello quando possibile.
     */
    private suspend fun recordV1EstimateForBenchmarkIfEnabled(outcomes: List<ModelVisionOutcome>, models: List<AiModel>) {
        if (!settingsRepository.settings.first().v2ShadowModeEnabled) return
        val success = outcomes.filterIsInstance<ModelVisionOutcome.Success>().firstOrNull() ?: return
        val session = sessionStore.session.value
        benchmarkRepository.recordV1Estimate(
            sessionId = session.sessionId,
            capturedAtEpochMillis = System.currentTimeMillis(),
            sessionMode = session.sessionMode,
            aiModel = models.firstOrNull() ?: success.model,
            pesiPerAlimento = success.result.alimenti.associate { it.nome to it.pesoStimatoG },
        )
    }

    /**
     * Motore V2 in ombra (piano V2 §13 punto 1, §10 Fase 2): gira in aggiunta a V1, mai al posto —
     * l'utente continua a vedere solo l'esito V1. Su [shadowScope], non su [viewModelScope]: questo
     * ViewModel viene distrutto appena la UI naviga verso i risultati V1, ben prima che le due
     * chiamate AI di V2 (M1 + M6) abbiano finito. Avvolto per intero in [runCatching]: un fallimento
     * qui (rete, quota, parsing) non deve mai emergere in UI né interrompere il flusso V1.
     *
     * Il [sessionId] della sessione corrente viene passato alla pipeline (§13 punto 5): collega il
     * record di telemetria M8 al pasto tracciato nel benchmark, dove [recordV1EstimateForBenchmarkIfEnabled]
     * ha già salvato la stima V1 sulle stesse foto.
     */
    private fun runV2ShadowAnalysisIfEnabled(capturedPhotos: List<CapturedPhoto>, models: List<AiModel>) {
        val model = models.firstOrNull() ?: return
        val sessionId = sessionStore.session.value.sessionId
        shadowScope.launch {
            if (!settingsRepository.settings.first().v2ShadowModeEnabled) return@launch
            val sessionMode = sessionStore.session.value.sessionMode
            runCatching {
                val photos = capturedPhotos.mapIndexed { index, photo ->
                    val motion = gyroscopeTracker.peakMotionNear(photo.capturedAtEpochMillis)
                    val cameraMetadata = extractCameraMetadata(photo.bytes)
                    SessionPhoto(computePhotoQuality("foto_$index", photo.bytes, motion, cameraMetadata), photo.bytes)
                }
                val outcome = v2PipelineFactory.create(sessionId).run(photos, CAPTURED_IMAGE_MIME_TYPE, sessionMode, model)
                if (outcome is PipelineOutcome.Verificato && outcome.telemetryId >= 0) {
                    benchmarkRepository.linkV2Record(sessionId, outcome.telemetryId)
                }
            }
        }
    }
}
