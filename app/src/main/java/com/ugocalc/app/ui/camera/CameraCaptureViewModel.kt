package com.ugocalc.app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.usecase.RunVisionForModelsUseCase
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.AnalysisNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    }
}
