package com.ugocalc.app.ui.bias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.BiasCorrectionWithUsageStats
import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BiasEvolutionUiState(
    val loading: Boolean = true,
    val consentGiven: Boolean = false,
    val v2ShadowModeEnabled: Boolean = false,
    val v2VisibleModeEnabled: Boolean = false,
    val corrections: List<BiasCorrectionWithUsageStats> = emptyList(),
)

/**
 * Menu laterale > BIAS ed evoluzione del comportamento (§ richiesta utente): il consenso al
 * trattamento dei dati per l'identificazione del bias (piano V2 §8) e la trasparenza su cosa il
 * sistema ha imparato finora vivono nella stessa schermata, perché sono la stessa cosa vista da due
 * lati — l'opt-in e il suo effetto osservabile.
 */
@HiltViewModel
class BiasEvolutionViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val biasCorrectionRepository: BiasCorrectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BiasEvolutionUiState())
    val uiState: StateFlow<BiasEvolutionUiState> = _uiState

    init {
        settingsRepository.settings.onEach { settings ->
            _uiState.value = _uiState.value.copy(
                consentGiven = settings.biasDataConsentGiven,
                v2ShadowModeEnabled = settings.v2ShadowModeEnabled,
                v2VisibleModeEnabled = settings.v2VisibleModeEnabled,
            )
        }.launchIn(viewModelScope)
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val corrections = biasCorrectionRepository.getAllCorrectionsWithUsageStats()
            _uiState.value = _uiState.value.copy(corrections = corrections, loading = false)
        }
    }

    fun setConsentGiven(given: Boolean) {
        viewModelScope.launch { settingsRepository.setBiasDataConsentGiven(given) }
    }

    fun setV2ShadowModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setV2ShadowModeEnabled(enabled) }
    }

    fun setV2VisibleModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setV2VisibleModeEnabled(enabled) }
    }
}
