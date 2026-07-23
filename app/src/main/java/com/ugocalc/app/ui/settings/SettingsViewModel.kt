package com.ugocalc.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.prefs.AppTheme
import com.ugocalc.app.data.local.prefs.UserSettings
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.model.AiProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named

data class SettingsScreenState(
    val userSettings: UserSettings = UserSettings(),
    val officialTableCount: Int = 0,
)

sealed interface BedrockTestResult {
    data object Idle : BedrockTestResult
    data object Loading : BedrockTestResult
    data class Success(val message: String) : BedrockTestResult
    data class Error(val message: String) : BedrockTestResult
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    tableRepository: TableRepository,
    @Named("no_auth") private val httpClient: OkHttpClient,
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
    fun setConfidenceAlertThresholdPercent(percent: Int) = viewModelScope.launch { settingsRepository.setConfidenceAlertThresholdPercent(percent) }

    fun getApiKey(provider: AiProviderType): String? = settingsRepository.getApiKey(provider)
    fun setApiKey(provider: AiProviderType, key: String) {
        if (key.isBlank()) settingsRepository.clearApiKey(provider) else settingsRepository.setApiKey(provider, key)
    }

    // ── AWS Bedrock Mantle credentials ─────────────────────────────────────────

    private val _bedrockTestResult = MutableStateFlow<BedrockTestResult>(BedrockTestResult.Idle)
    val bedrockTestResult: StateFlow<BedrockTestResult> = _bedrockTestResult.asStateFlow()

    fun getBedrockMantleApiKey(): String = settingsRepository.getBedrockMantleApiKey().orEmpty()
    fun getBedrockRegion(): String = settingsRepository.getBedrockRegion()

    fun setBedrockCredentials(mantleApiKey: String, region: String) {
        if (mantleApiKey.isBlank()) {
            settingsRepository.clearBedrockCredentials()
        } else {
            settingsRepository.setBedrockCredentials(mantleApiKey, settingsRepository.getBedrockMantleProjectId(), region)
        }
    }

    fun testBedrockConnection(mantleApiKey: String, region: String) {
        if (mantleApiKey.isBlank()) {
            _bedrockTestResult.value = BedrockTestResult.Error("Inserisci la Mantle API key")
            return
        }
        _bedrockTestResult.value = BedrockTestResult.Loading
        viewModelScope.launch {
            _bedrockTestResult.value = try {
                withContext(Dispatchers.IO) {
                    val projectId = settingsRepository.getBedrockMantleProjectId()
                    val body = """{"model":"anthropic.claude-sonnet-5","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
                        .toByteArray()
                    val request = Request.Builder()
                        .url("https://bedrock-mantle.$region.api.aws/anthropic/v1/messages")
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer $mantleApiKey")
                        .header("anthropic-version", "2023-06-01")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful || response.code == 400) {
                            // 400 può indicare parametri invalidi ma key valida
                            BedrockTestResult.Success("Connessione Bedrock Mantle riuscita")
                        } else {
                            BedrockTestResult.Error("HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                        }
                    }
                }
            } catch (e: Exception) {
                BedrockTestResult.Error(e.message ?: "Errore di rete sconosciuto")
            }
        }
    }

    fun clearHistory() = viewModelScope.launch { historyRepository.clearHistory() }

    fun resetAllSettings() = viewModelScope.launch { settingsRepository.resetAll() }

    val availableModels = AiModelCatalog.ALL
}
