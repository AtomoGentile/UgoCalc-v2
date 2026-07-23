package com.ugocalc.app.ui.modelselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelSelectionUiState(
    val availableModels: List<AiModel> = emptyList(),
    val selectedModelIds: Set<String> = emptySet(),
    val isOffline: Boolean = false,
)

@HiltViewModel
class ModelSelectionViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionStore: AnalysisSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelSelectionUiState())
    val uiState: StateFlow<ModelSelectionUiState> = _uiState

    init {
        viewModelScope.launch {
            val isOffline = !connectivityObserver.isOnlineNow()
            // §2.4 punto 1: senza connessione nessun provider cloud è raggiungibile.
            val models = AiModelCatalog.ALL.filter { model ->
                if (isOffline) false
                else if (model.provider == AiProviderType.BEDROCK_CLAUDE || model.provider == AiProviderType.BEDROCK_GPT) {
                    settingsRepository.hasBedrockCredentials()
                } else {
                    settingsRepository.getApiKey(model.provider) != null
                }
            }

            val settings = settingsRepository.settings
            settings.collect { userSettings ->
                val preselected = userSettings.preferredModelIds
                _uiState.value = _uiState.value.copy(
                    availableModels = models,
                    isOffline = isOffline,
                    selectedModelIds = if (_uiState.value.selectedModelIds.isEmpty()) preselected else _uiState.value.selectedModelIds,
                )
            }
        }
    }

    fun toggleModel(modelId: String) {
        _uiState.value = _uiState.value.let { state ->
            val newSelection = if (modelId in state.selectedModelIds) state.selectedModelIds - modelId else state.selectedModelIds + modelId
            state.copy(selectedModelIds = newSelection)
        }
    }

    fun confirmSelectionAndProceed(): List<String> {
        val selected = _uiState.value.availableModels.filter { it.id in _uiState.value.selectedModelIds }
        sessionStore.setSelectedModels(selected)
        return selected.map { it.id }
    }
}
