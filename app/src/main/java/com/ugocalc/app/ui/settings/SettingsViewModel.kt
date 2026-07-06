package com.ugocalc.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.prefs.AppTheme
import com.ugocalc.app.data.local.prefs.UserSettings
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsScreenState(
    val userSettings: UserSettings = UserSettings(),
    val officialTableCount: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    tableRepository: TableRepository,
) : ViewModel() {

    val state: StateFlow<SettingsScreenState> = combine(
        settingsRepository.settings,
        tableRepository.observeTableCount(),
    ) { settings, tableCount -> SettingsScreenState(settings, tableCount) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsScreenState())

    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setTextScale(scale: Float) = viewModelScope.launch { settingsRepository.setTextScale(scale) }
    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    fun setFavoritesEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setFavoritesEnabled(enabled) }
    fun setPrimaryColor(argb: Int?) = viewModelScope.launch { settingsRepository.setPrimaryColor(argb) }
    fun setSecondaryColor(argb: Int?) = viewModelScope.launch { settingsRepository.setSecondaryColor(argb) }
    fun setTertiaryColor(argb: Int?) = viewModelScope.launch { settingsRepository.setTertiaryColor(argb) }
    fun setBackgroundColor(argb: Int?) = viewModelScope.launch { settingsRepository.setBackgroundColor(argb) }
    fun setErrorColor(argb: Int?) = viewModelScope.launch { settingsRepository.setErrorColor(argb) }
    fun setPrimaryColorRistorante(argb: Int?) = viewModelScope.launch { settingsRepository.setPrimaryColorRistorante(argb) }
    fun setSecondaryColorRistorante(argb: Int?) = viewModelScope.launch { settingsRepository.setSecondaryColorRistorante(argb) }
    fun setTertiaryColorRistorante(argb: Int?) = viewModelScope.launch { settingsRepository.setTertiaryColorRistorante(argb) }
    fun setBackgroundColorRistorante(argb: Int?) = viewModelScope.launch { settingsRepository.setBackgroundColorRistorante(argb) }
    fun setErrorColorRistorante(argb: Int?) = viewModelScope.launch { settingsRepository.setErrorColorRistorante(argb) }
    fun setHistoryChartEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHistoryChartEnabled(enabled) }
    fun setFontFamily(name: String?) = viewModelScope.launch { settingsRepository.setFontFamily(name) }
    fun setFontColor(argb: Int?) = viewModelScope.launch { settingsRepository.setFontColor(argb) }
    /** Aggiunge/rimuove un modello dai preferiti (§2.2, più di una scelta possibile). */
    fun togglePreferredModel(id: String) = viewModelScope.launch {
        val current = state.value.userSettings.preferredModelIds
        val updated = if (id in current) current - id else current + id
        settingsRepository.setPreferredModelIds(updated)
    }
    fun setConfidenceIndicatorEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setConfidenceIndicatorEnabled(enabled) }
    fun setConfidenceThresholdPercent(percent: Int) = viewModelScope.launch { settingsRepository.setConfidenceThresholdPercent(percent) }

    fun getApiKey(provider: AiProviderType): String? = settingsRepository.getApiKey(provider)
    fun setApiKey(provider: AiProviderType, key: String) {
        if (key.isBlank()) settingsRepository.clearApiKey(provider) else settingsRepository.setApiKey(provider, key)
    }

    fun clearHistory() = viewModelScope.launch { historyRepository.clearHistory() }

    val availableModels = AiModelCatalog.ALL
}
