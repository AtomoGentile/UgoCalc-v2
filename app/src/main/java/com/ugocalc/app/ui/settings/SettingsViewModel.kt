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
import com.ugocalc.app.util.ConnectivityObserver
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
import com.ugocalc.app.domain.model.AiModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named

@androidx.compose.runtime.Immutable
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
    private val connectivityObserver: ConnectivityObserver,
    @Named("no_auth") private val httpClient: OkHttpClient,
) : ViewModel() {

    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    val availableModels: StateFlow<List<AiModel>> = _availableModels.asStateFlow()

    fun refreshAvailableModels() { _availableModels.value = computeAvailableModels() }

    init {
        val mantleIds = settingsRepository.getBedrockAvailableModels()
        if (mantleIds.isNotEmpty()) AiModelCatalog.setBedrockModels(mantleIds)

        val iamIds = settingsRepository.getBedrockIamAvailableModels()
        if (iamIds.isNotEmpty()) AiModelCatalog.setBedrockIamModels(iamIds)

        refreshAvailableModels()

        // Fetch silenzioso se le credenziali ci sono ma i modelli non sono ancora stati scaricati
        viewModelScope.launch {
            val mantleKey = settingsRepository.getBedrockMantleApiKey()
            val region = settingsRepository.getBedrockRegion()
            if (mantleKey != null && mantleIds.isEmpty()) {
                fetchAndSaveMantleModels(mantleKey, region)
                refreshAvailableModels()
            }
            val accessKey = settingsRepository.getBedrockIamAccessKey()
            val secretKey = settingsRepository.getBedrockIamSecretKey()
            val iamRegion = settingsRepository.getBedrockIamRegion()
            if (accessKey != null && secretKey != null && iamIds.isEmpty()) {
                fetchAndSaveIamModels(accessKey, secretKey, iamRegion)
                refreshAvailableModels()
            }
        }
    }

    val state: StateFlow<SettingsScreenState> = combine(
        settingsRepository.settings,
        tableRepository.observeTableCount(),
    ) { settings, tableCount -> SettingsScreenState(settings, tableCount) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsScreenState())

    fun setTheme(theme: AppTheme) { viewModelScope.launch { settingsRepository.setTheme(theme) } }
    fun setTextScale(scale: Float) { viewModelScope.launch { settingsRepository.setTextScale(scale) } }
    fun setNotificationsEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) } }
    fun setFavoritesEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setFavoritesEnabled(enabled) } }
    fun setPrimaryColor(argb: Int?) { viewModelScope.launch { settingsRepository.setPrimaryColor(argb) } }
    fun setSecondaryColor(argb: Int?) { viewModelScope.launch { settingsRepository.setSecondaryColor(argb) } }
    fun setTertiaryColor(argb: Int?) { viewModelScope.launch { settingsRepository.setTertiaryColor(argb) } }
    fun setBackgroundColor(argb: Int?) { viewModelScope.launch { settingsRepository.setBackgroundColor(argb) } }
    fun setErrorColor(argb: Int?) { viewModelScope.launch { settingsRepository.setErrorColor(argb) } }
    fun setPrimaryColorRistorante(argb: Int?) { viewModelScope.launch { settingsRepository.setPrimaryColorRistorante(argb) } }
    fun setSecondaryColorRistorante(argb: Int?) { viewModelScope.launch { settingsRepository.setSecondaryColorRistorante(argb) } }
    fun setTertiaryColorRistorante(argb: Int?) { viewModelScope.launch { settingsRepository.setTertiaryColorRistorante(argb) } }
    fun setBackgroundColorRistorante(argb: Int?) { viewModelScope.launch { settingsRepository.setBackgroundColorRistorante(argb) } }
    fun setErrorColorRistorante(argb: Int?) { viewModelScope.launch { settingsRepository.setErrorColorRistorante(argb) } }
    fun setHistoryChartEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setHistoryChartEnabled(enabled) } }
    fun setFontFamily(name: String?) { viewModelScope.launch { settingsRepository.setFontFamily(name) } }
    fun setFontColor(argb: Int?) { viewModelScope.launch { settingsRepository.setFontColor(argb) } }
    /** Aggiunge/rimuove un modello dai preferiti (§2.2, più di una scelta possibile). */
    fun togglePreferredModel(id: String) {
        viewModelScope.launch {
            val current = state.value.userSettings.preferredModelIds
            val updated = if (id in current) current - id else current + id
            settingsRepository.setPreferredModelIds(updated)
        }
    }
    /** Aggiunge/rimuove un modello dalla shadow mode (testing V2). */
    fun toggleShadowModel(id: String) {
        viewModelScope.launch {
            val current = state.value.userSettings.shadowModelIds
            val updated = if (id in current) current - id else current + id
            settingsRepository.setShadowModelIds(updated)
        }
    }
    fun setConfidenceIndicatorEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setConfidenceIndicatorEnabled(enabled) } }
    fun setConfidenceAlertThresholdPercent(percent: Int) { viewModelScope.launch { settingsRepository.setConfidenceAlertThresholdPercent(percent) } }

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
                            fetchAndSaveMantleModels(mantleApiKey, region)
                            refreshAvailableModels()
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

    private suspend fun fetchAndSaveMantleModels(mantleApiKey: String, region: String) {
        val ids = try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://bedrock.$region.amazonaws.com/foundation-models?byOutputModality=TEXT")
                    .header("Authorization", "Bearer $mantleApiKey")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        parseFoundationModelIds(body).ifEmpty { null }
                    } else null
                }
            }
        } catch (_: Exception) { null }

        if (ids != null) {
            settingsRepository.setBedrockAvailableModels(ids)
            AiModelCatalog.setBedrockModels(ids)
        }
    }

    // ── AWS Bedrock Runtime IAM credentials ────────────────────────────────────

    private val _bedrockIamTestResult = MutableStateFlow<BedrockTestResult>(BedrockTestResult.Idle)
    val bedrockIamTestResult: StateFlow<BedrockTestResult> = _bedrockIamTestResult.asStateFlow()

    fun getBedrockIamAccessKey(): String = settingsRepository.getBedrockIamAccessKey().orEmpty()
    fun getBedrockIamSecretKey(): String = settingsRepository.getBedrockIamSecretKey().orEmpty()
    fun getBedrockIamRegion(): String = settingsRepository.getBedrockIamRegion()

    fun setBedrockIamCredentials(accessKey: String, secretKey: String, region: String) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            settingsRepository.clearBedrockIamCredentials()
        } else {
            settingsRepository.setBedrockIamCredentials(accessKey, secretKey, region)
        }
    }

    fun testBedrockIamConnection(accessKey: String, secretKey: String, region: String) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            _bedrockIamTestResult.value = BedrockTestResult.Error("Inserisci Access Key e Secret Key")
            return
        }
        _bedrockIamTestResult.value = BedrockTestResult.Loading
        viewModelScope.launch {
            _bedrockIamTestResult.value = try {
                withContext(Dispatchers.IO) {
                    val modelId = "anthropic.claude-sonnet-5-20251001-v1:0"
                    val bodyStr = """{"anthropic_version":"bedrock-2023-05-31","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
                    val bodyBytes = bodyStr.toByteArray()
                    val encodedModelId = java.net.URLEncoder.encode(modelId, "UTF-8")
                    val url = "https://bedrock-runtime.$region.amazonaws.com/model/$encodedModelId/invoke"
                    val baseRequest = okhttp3.Request.Builder()
                        .url(url)
                        .header("Content-Type", "application/json")
                        .post(bodyBytes.toRequestBody("application/json".toMediaType()))
                        .build()
                    val signedRequest = com.ugocalc.app.data.remote.ai.client.AwsSigV4Signer.sign(
                        baseRequest, accessKey, secretKey, region, bodyBytes
                    )
                    httpClient.newCall(signedRequest).execute().use { response ->
                        if (response.isSuccessful || response.code == 400) {
                            fetchAndSaveIamModels(accessKey, secretKey, region)
                            refreshAvailableModels()
                            BedrockTestResult.Success("Connessione Bedrock IAM riuscita")
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

    private suspend fun fetchAndSaveIamModels(accessKey: String, secretKey: String, region: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                val url = "https://bedrock.$region.amazonaws.com/foundation-models?byOutputModality=TEXT"
                val baseRequest = Request.Builder().url(url).get().build()
                val signedRequest = com.ugocalc.app.data.remote.ai.client.AwsSigV4Signer.sign(
                    baseRequest, accessKey, secretKey, region, ByteArray(0)
                )
                httpClient.newCall(signedRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@withContext "Risposta vuota"
                        val ids = parseFoundationModelIds(body)
                        if (ids.isEmpty()) return@withContext "Nessun modello trovato"
                        settingsRepository.setBedrockIamAvailableModels(ids)
                        AiModelCatalog.setBedrockIamModels(ids)
                        null
                    } else {
                        "Lista modelli IAM: HTTP ${response.code}"
                    }
                }
            }
        } catch (e: Exception) {
            e.message
        }
    }

    private fun parseFoundationModelIds(json: String): List<String> {
        return try {
            val arr = JSONObject(json).getJSONArray("modelSummaries")
            (0 until arr.length()).map { arr.getJSONObject(it).getString("modelId") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Bedrock Mantle model picker (legacy, kept for compatibility) ───────────
    private val _showBedrockModelPicker = MutableStateFlow(false)
    val showBedrockModelPicker: StateFlow<Boolean> = _showBedrockModelPicker.asStateFlow()
    val enabledBedrockModelIds: StateFlow<Set<String>> = MutableStateFlow(emptySet<String>()).asStateFlow()
    fun openBedrockModelPicker() { _showAllModelsPicker.value = true }
    fun dismissBedrockModelPicker() { _showAllModelsPicker.value = false }
    fun saveBedrockEnabledModels(ids: Set<String>) = saveAllEnabledModels(ids)

    // ── Bedrock IAM model picker (legacy, kept for compatibility) ──────────────
    private val _showBedrockIamModelPicker = MutableStateFlow(false)
    val showBedrockIamModelPicker: StateFlow<Boolean> = _showBedrockIamModelPicker.asStateFlow()
    val enabledBedrockIamModelIds: StateFlow<Set<String>> = MutableStateFlow(emptySet<String>()).asStateFlow()
    fun openBedrockIamModelPicker() { _showAllModelsPicker.value = true }
    fun dismissBedrockIamModelPicker() { _showAllModelsPicker.value = false }
    fun saveBedrockIamEnabledModels(ids: Set<String>) = saveAllEnabledModels(ids)

    // ── Picker unificato: tutti i modelli disponibili → preferredModelIds ──────

    private val _showAllModelsPicker = MutableStateFlow(false)
    val showAllModelsPicker: StateFlow<Boolean> = _showAllModelsPicker.asStateFlow()

    fun openAllModelsPicker() { _showAllModelsPicker.value = true }
    fun dismissAllModelsPicker() { _showAllModelsPicker.value = false }

    fun saveAllEnabledModels(ids: Set<String>) {
        viewModelScope.launch { settingsRepository.setEnabledAnalysisModelIds(ids) }
        _showAllModelsPicker.value = false
    }

    fun clearHistory() { viewModelScope.launch { historyRepository.clearHistory() } }

    fun resetAllSettings() { viewModelScope.launch { settingsRepository.resetAll() } }

    private fun computeAvailableModels(): List<AiModel> {
        val isOffline = !connectivityObserver.isOnlineNow()
        val hasMantle = settingsRepository.hasBedrockCredentials()
        val hasIam = settingsRepository.hasBedrockIamCredentials()
        return AiModelCatalog.ALL.filter { model ->
            if (isOffline) return@filter false
            when (model.provider) {
                AiProviderType.BEDROCK_CLAUDE, AiProviderType.BEDROCK_GPT,
                AiProviderType.BEDROCK_IAM_CLAUDE, AiProviderType.BEDROCK_IAM_GPT ->
                    hasMantle || hasIam
                else -> settingsRepository.getApiKey(model.provider) != null
            }
        }
    }

}
