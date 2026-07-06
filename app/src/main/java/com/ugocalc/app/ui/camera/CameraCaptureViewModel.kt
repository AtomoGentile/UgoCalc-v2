package com.ugocalc.app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.di.ShadowAnalysisScope
import com.ugocalc.app.domain.inference.SessionPhoto
import com.ugocalc.app.domain.inference.V2AnalysisPipelineFactory
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.usecase.RunVisionForModelsUseCase
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.AnalysisNotificationHelper
import com.ugocalc.app.util.computePhotoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CAPTURED_IMAGE_MIME_TYPE = "image/jpeg"

data class CameraUiState(
    val capturedImages: List<ByteArray> = emptyList(),
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
    @ShadowAnalysisScope private val shadowScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    /** Scatto avvenuto: si aggiunge alla galleria di foto di questa sessione (più scatti per un'analisi più efficace). */
    fun onPhotoCaptured(imageBytes: ByteArray) {
        _uiState.update { it.copy(capturedImages = it.capturedImages + imageBytes) }
    }

    /** L'utente scarta una foto già scattata dalla galleria della sessione corrente. */
    fun removePhoto(index: Int) {
        _uiState.update { state ->
            state.copy(capturedImages = state.capturedImages.filterIndexed { i, _ -> i != index })
        }
    }

    /** Tutte le foto confermate → invio al Passaggio 1 per ciascun modello selezionato (§2.4 punto 3). */
    fun confirmPhotos(models: List<AiModel>) {
        val images = _uiState.value.capturedImages
        if (images.isEmpty()) return
        sessionStore.setCapturedImages(images, CAPTURED_IMAGE_MIME_TYPE)
        _uiState.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            val outcomes = runVisionForModels(models, images, CAPTURED_IMAGE_MIME_TYPE)
            sessionStore.setVisionOutcomes(outcomes)
            _uiState.update { it.copy(isAnalyzing = false, analysisDone = true) }
            notificationHelper.notifyAnalysisCompletedIfBackgrounded()
        }
        runV2ShadowAnalysisIfEnabled(images, models)
    }

    /**
     * Motore V2 in ombra (piano V2 §13 punto 1, §10 Fase 2): gira in aggiunta a V1, mai al posto —
     * l'utente continua a vedere solo l'esito V1. Su [shadowScope], non su [viewModelScope]: questo
     * ViewModel viene distrutto appena la UI naviga verso i risultati V1, ben prima che le due
     * chiamate AI di V2 (M1 + M6) abbiano finito. Avvolto per intero in [runCatching]: un fallimento
     * qui (rete, quota, parsing) non deve mai emergere in UI né interrompere il flusso V1.
     */
    private fun runV2ShadowAnalysisIfEnabled(images: List<ByteArray>, models: List<AiModel>) {
        val model = models.firstOrNull() ?: return
        shadowScope.launch {
            if (!settingsRepository.settings.first().v2ShadowModeEnabled) return@launch
            val sessionMode = sessionStore.session.value.sessionMode
            runCatching {
                val photos = images.mapIndexed { index, bytes ->
                    SessionPhoto(computePhotoQuality("foto_$index", bytes), bytes)
                }
                v2PipelineFactory.create().run(photos, CAPTURED_IMAGE_MIME_TYPE, sessionMode, model)
            }
        }
    }
}
