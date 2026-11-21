package com.ugocalc.app.ui.settings

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.prefs.AppTheme
import com.ugocalc.app.data.local.prefs.UserSettings
import com.ugocalc.app.data.remote.ai.auth.GeminiServiceAccountManager
import com.ugocalc.app.data.remote.ai.auth.VertexAiAuthStatus
import com.ugocalc.app.data.remote.ai.auth.VertexAiTokenProvider
import com.ugocalc.app.data.remote.ai.client.AwsSigV4Signer
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.glycemic.GlycemicProfilePreset
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named

@Immutable
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

sealed interface GeminiModelsState {
    data object Idle : GeminiModelsState
    data object Loading : GeminiModelsState
    data class Loaded(val count: Int) : GeminiModelsState
    data class Error(val message: String) : GeminiModelsState
}

sealed interface ProviderTestState {
    data object Idle : ProviderTestState
    data object Testing : ProviderTestState
    data class Success(val message: String, val modelCount: Int? = null) : ProviderTestState
    data class Error(val message: String) : ProviderTestState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    tableRepository: TableRepository,
    private val connectivityObserver: ConnectivityObserver,
    @Named("no_auth") private val httpClient: OkHttpClient,
    private val sessionStore: AnalysisSessionStore,
    private val vertexAiTokenProvider: VertexAiTokenProvider,
    private val geminiServiceAccountManager: GeminiServiceAccountManager,
) : ViewModel() {

    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    val availableModels: StateFlow<List<AiModel>> = _availableModels.asStateFlow()

    private val _providerTestStates = MutableStateFlow<Map<AiProviderType, ProviderTestState>>(emptyMap())
    val providerTestStates: StateFlow<Map<AiProviderType, ProviderTestState>> = _providerTestStates.asStateFlow()

    private val _configuredProviders = MutableStateFlow<Map<AiProviderType, Boolean>>(emptyMap())
    val configuredProviders: StateFlow<Map<AiProviderType, Boolean>> = _configuredProviders.asStateFlow()

    val vertexAiAuthStatus: StateFlow<VertexAiAuthStatus> = geminiServiceAccountManager.status

    fun isVertexAiServiceAccountConfigured(): Boolean = geminiServiceAccountManager.isConfigured

    private fun refreshConfiguredProviders() {
        _configuredProviders.value = mapOf(
            AiProviderType.OPENAI_API to (settingsRepository.getApiKey(AiProviderType.OPENAI_API) != null),
            AiProviderType.ANTHROPIC to (settingsRepository.getApiKey(AiProviderType.ANTHROPIC) != null),
            AiProviderType.GEMINI to (settingsRepository.getApiKey(AiProviderType.GEMINI) != null),
            AiProviderType.BEDROCK_CLAUDE to settingsRepository.hasBedrockCredentials(),
            AiProviderType.BEDROCK_IAM_CLAUDE to settingsRepository.hasBedrockIamCredentials(),
            AiProviderType.VERTEX_AI to (settingsRepository.hasVertexAiCredentials() && geminiServiceAccountManager.isConfigured),
        )
    }

    fun refreshAvailableModels() {
        refreshConfiguredProviders()
        viewModelScope.launch(Dispatchers.Default) {
            _availableModels.value = computeAvailableModels()
        }
    }

    init {
        val openAiIds = settingsRepository.getOpenAiAvailableModels()
        if (openAiIds.isNotEmpty()) AiModelCatalog.setOpenAiModels(openAiIds)

        val anthropicIds = settingsRepository.getAnthropicAvailableModels()
        if (anthropicIds.isNotEmpty()) AiModelCatalog.setAnthropicModels(anthropicIds)

        val mantleIds = settingsRepository.getBedrockAvailableModels()
        if (mantleIds.isNotEmpty()) AiModelCatalog.setBedrockModels(mantleIds)

        val iamIds = settingsRepository.getBedrockIamAvailableModels()
        if (iamIds.isNotEmpty()) AiModelCatalog.setBedrockIamModels(iamIds)

        val geminiIds = settingsRepository.getGeminiAvailableModels()
        if (geminiIds.isNotEmpty()) AiModelCatalog.setGeminiModels(geminiIds)

        refreshAvailableModels()

        viewModelScope.launch {
            AiModelCatalog.catalogVersion.collect {
                refreshAvailableModels()
            }
        }

        // Fetch silenzioso se le credenziali ci sono ma i modelli non sono ancora stati scaricati
        viewModelScope.launch(Dispatchers.IO) {
            val openAiKey = settingsRepository.getApiKey(AiProviderType.OPENAI_API)
            if (openAiKey != null && openAiIds.isEmpty()) {
                runCatching { fetchAndSaveOpenAiModels(openAiKey) }
            }
            val anthropicKey = settingsRepository.getApiKey(AiProviderType.ANTHROPIC)
            if (anthropicKey != null && anthropicIds.isEmpty()) {
                runCatching { fetchAndSaveAnthropicModels(anthropicKey) }
            }
            val mantleKey = settingsRepository.getBedrockMantleApiKey()
            val region = settingsRepository.getBedrockRegion()
            if (mantleKey != null && mantleIds.isEmpty()) {
                runCatching { fetchAndSaveMantleModels(mantleKey, region) }
            }
            val accessKey = settingsRepository.getBedrockIamAccessKey()
            val secretKey = settingsRepository.getBedrockIamSecretKey()
            val iamAccountId = settingsRepository.getBedrockIamAccountId()
            val iamRegion = settingsRepository.getBedrockIamRegion()
            if (accessKey != null && secretKey != null && iamAccountId != null && iamIds.isEmpty()) {
                runCatching { fetchAndSaveIamModels(accessKey, secretKey, iamAccountId, iamRegion) }
            }
            val geminiKey = settingsRepository.getApiKey(AiProviderType.GEMINI)
            if (geminiKey != null && geminiIds.isEmpty()) {
                runCatching { fetchAndSaveGeminiModels(geminiKey) }
            }
            refreshAvailableModels()
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
    fun applyGlycemicProfilePreset(preset: GlycemicProfilePreset) {
        viewModelScope.launch { settingsRepository.applyGlycemicProfilePreset(preset) }
    }
    fun setBaselineGlucoseMgDl(mgDl: Int) {
        viewModelScope.launch { settingsRepository.setBaselineGlucoseMgDl(mgDl.coerceIn(65, 180)) }
    }
    fun setCarbSensitivityMgDlPer10g(mgDlPer10g: Int) {
        viewModelScope.launch { settingsRepository.setCarbSensitivityMgDlPer10g(mgDlPer10g.coerceIn(10, 60)) }
    }
    fun setTargetPostPrandialMaxMgDl(mgDl: Int) {
        viewModelScope.launch { settingsRepository.setTargetPostPrandialMaxMgDl(mgDl.coerceIn(120, 200)) }
    }

    private fun toggleModelIdOrUid(current: Set<String>, uidOrId: String): Set<String> {
        val bareId = uidOrId.substringAfter("::", uidOrId)
        val isSelected = uidOrId in current || (uidOrId != bareId && bareId in current)
        return if (isSelected) current - uidOrId - bareId else current + uidOrId
    }

    /** Aggiunge/rimuove un modello dai preferiti (§2.2, più di una scelta possibile). */
    fun togglePreferredModel(id: String) {
        viewModelScope.launch {
            val current = state.value.userSettings.preferredModelIds
            settingsRepository.setPreferredModelIds(toggleModelIdOrUid(current, id))
        }
    }

    /** Aggiunge/rimuove un modello dalla shadow mode (testing V2). */
    fun toggleShadowModel(id: String) {
        viewModelScope.launch {
            val current = state.value.userSettings.shadowModelIds
            settingsRepository.setShadowModelIds(toggleModelIdOrUid(current, id))
        }
    }

    fun toggleNormalizationModel(id: String) {
        viewModelScope.launch {
            val current = state.value.userSettings.enabledNormalizationModelIds
            settingsRepository.setEnabledNormalizationModelIds(toggleModelIdOrUid(current, id))
        }
    }

    fun setConfidenceIndicatorEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setConfidenceIndicatorEnabled(enabled) } }
    fun setConfidenceAlertThresholdPercent(percent: Int) { viewModelScope.launch { settingsRepository.setConfidenceAlertThresholdPercent(percent) } }

    private fun setProviderTestState(provider: AiProviderType, state: ProviderTestState) {
        _providerTestStates.update { it + (provider to state) }
    }

    fun getApiKey(provider: AiProviderType): String? = settingsRepository.getApiKey(provider)

    fun setApiKey(provider: AiProviderType, key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            clearApiKey(provider)
            return
        }
        settingsRepository.setApiKey(provider, trimmed)
        refreshAvailableModels()
        when (provider) {
            AiProviderType.OPENAI_API -> testAndSaveOpenAiKey(trimmed)
            AiProviderType.ANTHROPIC -> testAndSaveAnthropicKey(trimmed)
            AiProviderType.GEMINI -> testAndSaveGeminiKey(trimmed)
            else -> Unit
        }
    }

    fun clearApiKey(provider: AiProviderType) {
        settingsRepository.clearApiKey(provider)
        when (provider) {
            AiProviderType.OPENAI_API -> {
                settingsRepository.clearOpenAiAvailableModels()
                AiModelCatalog.clearOpenAiModels()
            }
            AiProviderType.ANTHROPIC -> {
                settingsRepository.clearAnthropicAvailableModels()
                AiModelCatalog.clearAnthropicModels()
            }
            AiProviderType.GEMINI -> {
                settingsRepository.clearGeminiAvailableModels()
                AiModelCatalog.clearGeminiModels()
                _geminiModelsState.value = GeminiModelsState.Idle
            }
            else -> Unit
        }
        setProviderTestState(provider, ProviderTestState.Idle)
        refreshAvailableModels()
    }

    // ── OpenAI ──────────────────────────────────────────────────────────────────

    fun testAndSaveOpenAiKey(rawKey: String) {
        val apiKey = rawKey.trim()
        if (apiKey.isBlank()) {
            setProviderTestState(AiProviderType.OPENAI_API, ProviderTestState.Error("Inserisci la chiave API OpenAI"))
            return
        }
        setProviderTestState(AiProviderType.OPENAI_API, ProviderTestState.Testing)
        settingsRepository.setApiKey(AiProviderType.OPENAI_API, apiKey)
        refreshAvailableModels()
        viewModelScope.launch {
            try {
                val ids = fetchAndSaveOpenAiModels(apiKey)
                refreshAvailableModels()
                val msg = if (ids.isNotEmpty()) {
                    "Connessione OpenAI riuscita · ${ids.size} modelli"
                } else {
                    "Chiave OpenAI valida (usati modelli predefiniti)"
                }
                setProviderTestState(
                    AiProviderType.OPENAI_API,
                    ProviderTestState.Success(msg, ids.size),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setProviderTestState(
                    AiProviderType.OPENAI_API,
                    ProviderTestState.Error(e.message ?: "Errore durante il test OpenAI"),
                )
            }
        }
    }

    private suspend fun fetchAndSaveOpenAiModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(formatProviderHttpError("OpenAI", response.code, body))
            }
            val ids = parseOpenAiModelIds(body)
            if (ids.isNotEmpty()) {
                settingsRepository.setOpenAiAvailableModels(ids)
                AiModelCatalog.setOpenAiModels(ids)
            }
            ids
        }
    }

    private fun parseOpenAiModelIds(rawJson: String): List<String> {
        return try {
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("data") ?: return emptyList()
            val excludedTokens = listOf(
                "realtime",
                "audio",
                "tts",
                "whisper",
                "dall-e",
                "embedding",
                "moderation",
                "sora",
                "instruct",
            )
            val ids = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id").trim()
                if (id.isBlank()) continue
                val lower = id.lowercase()
                val isChatOrReasoning = lower.startsWith("gpt-") || lower.startsWith("o3") || lower.startsWith("o4")
                if (isChatOrReasoning && excludedTokens.none { it in lower }) {
                    ids.add(id)
                }
            }
            ids.distinct().sorted()
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Errore parsing modelli OpenAI: ${e.message}", e)
            emptyList()
        }
    }

    // ── Anthropic ───────────────────────────────────────────────────────────────

    fun testAndSaveAnthropicKey(rawKey: String) {
        val apiKey = rawKey.trim()
        if (apiKey.isBlank()) {
            setProviderTestState(AiProviderType.ANTHROPIC, ProviderTestState.Error("Inserisci la chiave API Anthropic"))
            return
        }
        setProviderTestState(AiProviderType.ANTHROPIC, ProviderTestState.Testing)
        settingsRepository.setApiKey(AiProviderType.ANTHROPIC, apiKey)
        refreshAvailableModels()
        viewModelScope.launch {
            try {
                val ids = fetchAndSaveAnthropicModels(apiKey)
                refreshAvailableModels()
                val msg = if (ids.isNotEmpty()) {
                    "Connessione Anthropic riuscita · ${ids.size} modelli"
                } else {
                    "Chiave Anthropic valida (usati modelli predefiniti)"
                }
                setProviderTestState(
                    AiProviderType.ANTHROPIC,
                    ProviderTestState.Success(msg, ids.size),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setProviderTestState(
                    AiProviderType.ANTHROPIC,
                    ProviderTestState.Error(e.message ?: "Errore durante il test Anthropic"),
                )
            }
        }
    }

    private suspend fun fetchAndSaveAnthropicModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/models")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(formatProviderHttpError("Anthropic", response.code, body))
            }
            val ids = parseAnthropicModelIds(body)
            if (ids.isNotEmpty()) {
                settingsRepository.setAnthropicAvailableModels(ids)
                AiModelCatalog.setAnthropicModels(ids)
            }
            ids
        }
    }

    private fun parseAnthropicModelIds(rawJson: String): List<String> {
        return try {
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("data") ?: return emptyList()
            val ids = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id").trim()
                if (id.lowercase().startsWith("claude-")) {
                    ids.add(id)
                }
            }
            ids.distinct()
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Errore parsing modelli Anthropic: ${e.message}", e)
            emptyList()
        }
    }

    // ── AWS Bedrock Mantle credentials ─────────────────────────────────────────

    private val _bedrockTestResult = MutableStateFlow<BedrockTestResult>(BedrockTestResult.Idle)
    val bedrockTestResult: StateFlow<BedrockTestResult> = _bedrockTestResult.asStateFlow()

    private val _isSavingCredentials = MutableStateFlow(false)
    val isSavingCredentials: StateFlow<Boolean> = _isSavingCredentials.asStateFlow()

    fun getBedrockMantleApiKey(): String = settingsRepository.getBedrockMantleApiKey().orEmpty()
    fun getBedrockRegion(): String = settingsRepository.getBedrockRegion()

    fun setBedrockCredentials(mantleApiKey: String, region: String) {
        val trimmed = mantleApiKey.trim()
        viewModelScope.launch {
            try {
                _isSavingCredentials.value = true
                sessionStore.setIsSavingCredentials(true)
                withContext(Dispatchers.IO) {
                    if (trimmed.isBlank()) {
                        settingsRepository.clearBedrockCredentials()
                        settingsRepository.setBedrockAvailableModels(emptyList())
                        AiModelCatalog.clearBedrockModels()
                    } else {
                        settingsRepository.setBedrockCredentials(trimmed, settingsRepository.getBedrockMantleProjectId(), region)
                    }
                }
                refreshAvailableModels()
            } finally {
                _isSavingCredentials.value = false
                sessionStore.setIsSavingCredentials(false)
            }
        }
    }

    fun clearBedrockCredentials() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsRepository.clearBedrockCredentials()
                settingsRepository.setBedrockAvailableModels(emptyList())
            }
            AiModelCatalog.clearBedrockModels()
            _bedrockTestResult.value = BedrockTestResult.Idle
            setProviderTestState(AiProviderType.BEDROCK_CLAUDE, ProviderTestState.Idle)
            refreshAvailableModels()
        }
    }

    fun testBedrockConnection(mantleApiKey: String, region: String) {
        val trimmed = mantleApiKey.trim()
        if (trimmed.isBlank()) {
            val msg = "Inserisci la Mantle API key"
            _bedrockTestResult.value = BedrockTestResult.Error(msg)
            setProviderTestState(AiProviderType.BEDROCK_CLAUDE, ProviderTestState.Error(msg))
            return
        }
        _bedrockTestResult.value = BedrockTestResult.Loading
        setProviderTestState(AiProviderType.BEDROCK_CLAUDE, ProviderTestState.Testing)
        settingsRepository.setBedrockCredentials(trimmed, settingsRepository.getBedrockMantleProjectId(), region)
        refreshAvailableModels()
        viewModelScope.launch {
            try {
                val ids = fetchAndSaveMantleModels(trimmed, region)
                refreshAvailableModels()
                val msg = "Connessione Bedrock Mantle riuscita · ${ids.size} modelli"
                _bedrockTestResult.value = BedrockTestResult.Success(msg)
                setProviderTestState(AiProviderType.BEDROCK_CLAUDE, ProviderTestState.Success(msg, ids.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Errore di rete sconosciuto"
                _bedrockTestResult.value = BedrockTestResult.Error(msg)
                setProviderTestState(AiProviderType.BEDROCK_CLAUDE, ProviderTestState.Error(msg))
            }
        }
    }

    private suspend fun fetchAndSaveMantleModels(mantleApiKey: String, region: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://bedrock.$region.amazonaws.com/foundation-models?byOutputModality=TEXT")
            .header("Authorization", "Bearer $mantleApiKey")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(formatProviderHttpError("Bedrock Mantle", response.code, body))
            }
            val ids = parseFoundationModelIds(body)
            if (ids.isEmpty()) throw Exception("Nessun modello trovato su Bedrock Mantle")
            settingsRepository.setBedrockAvailableModels(ids)
            AiModelCatalog.setBedrockModels(ids)
            ids
        }
    }

    // ── AWS Bedrock Runtime IAM credentials ────────────────────────────────────

    private val _bedrockIamTestResult = MutableStateFlow<BedrockTestResult>(BedrockTestResult.Idle)
    val bedrockIamTestResult: StateFlow<BedrockTestResult> = _bedrockIamTestResult.asStateFlow()

    fun getBedrockIamAccessKey(): String = settingsRepository.getBedrockIamAccessKey().orEmpty()
    fun getBedrockIamSecretKey(): String = settingsRepository.getBedrockIamSecretKey().orEmpty()
    fun getBedrockIamAccountId(): String = settingsRepository.getBedrockIamAccountId().orEmpty()
    fun getBedrockIamRegion(): String = settingsRepository.getBedrockIamRegion()

    fun setBedrockIamCredentials(accessKey: String, secretKey: String, accountId: String, region: String) {
        val a = accessKey.trim()
        val s = secretKey.trim()
        val acc = accountId.trim()
        viewModelScope.launch {
            try {
                _isSavingCredentials.value = true
                sessionStore.setIsSavingCredentials(true)
                withContext(Dispatchers.IO) {
                    if (a.isBlank() || s.isBlank() || acc.isBlank()) {
                        settingsRepository.clearBedrockIamCredentials()
                        settingsRepository.setBedrockIamAvailableModels(emptyList())
                        AiModelCatalog.clearBedrockIamModels()
                    } else {
                        settingsRepository.setBedrockIamCredentials(a, s, acc, region)
                    }
                }
                refreshAvailableModels()
            } finally {
                _isSavingCredentials.value = false
                sessionStore.setIsSavingCredentials(false)
            }
        }
    }

    fun clearBedrockIamCredentials() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsRepository.clearBedrockIamCredentials()
                settingsRepository.setBedrockIamAvailableModels(emptyList())
            }
            AiModelCatalog.clearBedrockIamModels()
            _bedrockIamTestResult.value = BedrockTestResult.Idle
            setProviderTestState(AiProviderType.BEDROCK_IAM_CLAUDE, ProviderTestState.Idle)
            refreshAvailableModels()
        }
    }

    fun testBedrockIamConnection(accessKey: String, secretKey: String, accountId: String, region: String) {
        val a = accessKey.trim()
        val s = secretKey.trim()
        val acc = accountId.trim()
        if (a.isBlank() || s.isBlank() || acc.isBlank()) {
            val msg = "Inserisci Access Key, Secret Key e Account ID"
            _bedrockIamTestResult.value = BedrockTestResult.Error(msg)
            setProviderTestState(AiProviderType.BEDROCK_IAM_CLAUDE, ProviderTestState.Error(msg))
            return
        }
        _bedrockIamTestResult.value = BedrockTestResult.Loading
        setProviderTestState(AiProviderType.BEDROCK_IAM_CLAUDE, ProviderTestState.Testing)
        settingsRepository.setBedrockIamCredentials(a, s, acc, region)
        refreshAvailableModels()
        viewModelScope.launch {
            try {
                val ids = fetchAndSaveIamModels(a, s, acc, region)
                refreshAvailableModels()
                val msg = "Connessione Bedrock IAM riuscita · ${ids.size} modelli"
                _bedrockIamTestResult.value = BedrockTestResult.Success(msg)
                setProviderTestState(AiProviderType.BEDROCK_IAM_CLAUDE, ProviderTestState.Success(msg, ids.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Errore di rete sconosciuto"
                _bedrockIamTestResult.value = BedrockTestResult.Error(msg)
                setProviderTestState(AiProviderType.BEDROCK_IAM_CLAUDE, ProviderTestState.Error(msg))
            }
        }
    }

    private suspend fun fetchAndSaveIamModels(
        accessKey: String,
        secretKey: String,
        accountId: String,
        region: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val url = "https://bedrock.$region.amazonaws.com/foundation-models?byOutputModality=TEXT"
        val baseRequest = Request.Builder().url(url).get().build()
        val signedRequest = AwsSigV4Signer.sign(
            baseRequest,
            accessKey,
            secretKey,
            region,
            ByteArray(0),
        )
        httpClient.newCall(signedRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(formatProviderHttpError("Bedrock IAM", response.code, body))
            }
            val ids = parseFoundationModelIds(body)
            if (ids.isEmpty()) throw Exception("Nessun modello trovato per l'account $accountId")
            settingsRepository.setBedrockIamAvailableModels(ids)
            AiModelCatalog.setBedrockIamModels(ids)
            ids
        }
    }

    private fun parseFoundationModelIds(json: String): List<String> {
        return try {
            val root = JSONObject(json)
            val models = mutableListOf<String>()
            if (root.has("inferenceProfileSummaries")) {
                val arr = root.getJSONArray("inferenceProfileSummaries")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    if (item.optString("status", "ACTIVE") != "INACTIVE") {
                        models.add(item.getString("inferenceProfileId"))
                    }
                }
            }
            if (root.has("modelSummaries")) {
                val arr = root.getJSONArray("modelSummaries")
                for (i in 0 until arr.length()) {
                    models.add(arr.getJSONObject(i).getString("modelId"))
                }
            }
            val distinct = models.distinct()
            Log.d("Bedrock", "Catalogo modelli caricato: ${distinct.size} modelli")
            distinct
        } catch (e: Exception) {
            Log.e("Bedrock", "Errore nel parsing del catalogo: ${e.message}", e)
            emptyList()
        }
    }

    // ── Google AI Studio (Gemini): fetch dinamico modelli disponibili ──────────

    private val _geminiModelsState = MutableStateFlow<GeminiModelsState>(GeminiModelsState.Idle)
    val geminiModelsState: StateFlow<GeminiModelsState> = _geminiModelsState.asStateFlow()

    fun testAndSaveGeminiKey(rawKey: String) {
        val apiKey = rawKey.trim()
        if (apiKey.isBlank()) {
            val msg = "Inserisci la chiave API Gemini"
            _geminiModelsState.value = GeminiModelsState.Error(msg)
            setProviderTestState(AiProviderType.GEMINI, ProviderTestState.Error(msg))
            return
        }
        _geminiModelsState.value = GeminiModelsState.Loading
        setProviderTestState(AiProviderType.GEMINI, ProviderTestState.Testing)
        settingsRepository.setApiKey(AiProviderType.GEMINI, apiKey)
        refreshAvailableModels()
        viewModelScope.launch {
            fetchAndSaveGeminiModels(apiKey)
        }
    }

    private suspend fun fetchAndSaveGeminiModels(apiKey: String) {
        _geminiModelsState.value = GeminiModelsState.Loading
        setProviderTestState(AiProviderType.GEMINI, ProviderTestState.Testing)
        try {
            val ids = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100")
                    .header("x-goog-api-key", apiKey)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw Exception(formatProviderHttpError("Gemini", response.code, body))
                    }
                    parseGeminiModelIds(body)
                }
            }
            if (ids.isEmpty()) throw Exception("Nessun modello Gemini compatibile trovato per questa chiave")
            settingsRepository.setGeminiAvailableModels(ids)
            AiModelCatalog.setGeminiModels(ids)
            refreshAvailableModels()
            _geminiModelsState.value = GeminiModelsState.Loaded(ids.size)
            setProviderTestState(
                AiProviderType.GEMINI,
                ProviderTestState.Success("Connessione Gemini riuscita · ${ids.size} modelli", ids.size),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Gemini", "Fetch modelli fallito: ${e.message}", e)
            val msg = e.message ?: "Errore sconosciuto"
            _geminiModelsState.value = GeminiModelsState.Error(msg)
            setProviderTestState(AiProviderType.GEMINI, ProviderTestState.Error(msg))
        }
    }

    private fun parseGeminiModelIds(json: String): List<String> {
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("models") ?: return emptyList()
            val excludedTokens = listOf("embedding", "imagen", "veo", "tts", "aqa", "bison")
            val ids = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val methods = obj.optJSONArray("supportedGenerationMethods")
                val supportsGenerate = methods != null && (0 until methods.length()).any {
                    methods.getString(it) == "generateContent"
                }
                if (!supportsGenerate) continue
                val name = obj.optString("name").removePrefix("models/").trim()
                val lower = name.lowercase()
                if (lower.startsWith("gemini-") && excludedTokens.none { it in lower }) {
                    ids.add(name)
                }
            }
            ids.distinct()
        } catch (e: Exception) {
            Log.e("Gemini", "Parsing modelli fallito: ${e.message}", e)
            emptyList()
        }
    }

    // ── Google Vertex AI credentials ────────────────────────────────────────────

    fun getVertexAiProjectId(): String =
        settingsRepository.getVertexAiProjectId()
            ?: geminiServiceAccountManager.configuredProjectId().orEmpty()

    fun getVertexAiRegion(): String = settingsRepository.getVertexAiRegion()

    fun setVertexAiCredentials(projectId: String, region: String) {
        val trimmed = projectId.trim()
        viewModelScope.launch {
            try {
                _isSavingCredentials.value = true
                sessionStore.setIsSavingCredentials(true)
                withContext(Dispatchers.IO) {
                    if (trimmed.isBlank()) {
                        settingsRepository.clearVertexAiCredentials()
                    } else {
                        settingsRepository.setVertexAiCredentials(trimmed, region)
                    }
                }
                refreshAvailableModels()
            } finally {
                _isSavingCredentials.value = false
                sessionStore.setIsSavingCredentials(false)
            }
        }
    }

    fun importVertexAiServiceAccount(
        uri: Uri,
        region: String,
        onProjectIdDetected: (String) -> Unit,
    ) {
        setProviderTestState(AiProviderType.VERTEX_AI, ProviderTestState.Testing)
        viewModelScope.launch {
            try {
                geminiServiceAccountManager.importFromUri(uri)
                val detectedProjectId = geminiServiceAccountManager.configuredProjectId().orEmpty()
                if (detectedProjectId.isNotBlank()) {
                    settingsRepository.setVertexAiCredentials(detectedProjectId, region)
                    onProjectIdDetected(detectedProjectId)
                }
                refreshAvailableModels()
                when (val authStatus = geminiServiceAccountManager.status.value) {
                    is VertexAiAuthStatus.Valid -> setProviderTestState(
                        AiProviderType.VERTEX_AI,
                        ProviderTestState.Success("Service account importato e token OAuth2 verificato"),
                    )
                    is VertexAiAuthStatus.Error -> setProviderTestState(
                        AiProviderType.VERTEX_AI,
                        ProviderTestState.Error(authStatus.message),
                    )
                    is VertexAiAuthStatus.Unconfigured -> setProviderTestState(
                        AiProviderType.VERTEX_AI,
                        ProviderTestState.Error("Service account non configurato"),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setProviderTestState(
                    AiProviderType.VERTEX_AI,
                    ProviderTestState.Error(e.message ?: "Errore importazione Service Account"),
                )
            }
        }
    }

    fun testAndSaveVertexAi(projectId: String, region: String) {
        val trimmed = projectId.trim()
        if (trimmed.isBlank()) {
            setProviderTestState(AiProviderType.VERTEX_AI, ProviderTestState.Error("Inserisci il Project ID Google Cloud"))
            return
        }
        setProviderTestState(AiProviderType.VERTEX_AI, ProviderTestState.Testing)
        settingsRepository.setVertexAiCredentials(trimmed, region)
        refreshAvailableModels()
        viewModelScope.launch {
            try {
                if (!geminiServiceAccountManager.isConfigured) {
                    setProviderTestState(
                        AiProviderType.VERTEX_AI,
                        ProviderTestState.Error("Project ID salvato. Carica il file JSON del Service Account per autenticare Vertex AI."),
                    )
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    vertexAiTokenProvider.invalidateCache()
                    val token = vertexAiTokenProvider.getAccessToken()
                    if (token.isBlank()) throw Exception("Token OAuth2 vuoto")
                }
                refreshAvailableModels()
                setProviderTestState(
                    AiProviderType.VERTEX_AI,
                    ProviderTestState.Success("Connessione Vertex AI verificata (token OAuth2 valido)"),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setProviderTestState(
                    AiProviderType.VERTEX_AI,
                    ProviderTestState.Error(e.message ?: "Errore verifica Vertex AI"),
                )
            }
        }
    }

    fun clearVertexAiCredentials() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsRepository.clearVertexAiCredentials()
                geminiServiceAccountManager.clear()
            }
            setProviderTestState(AiProviderType.VERTEX_AI, ProviderTestState.Idle)
            refreshAvailableModels()
        }
    }

    private fun formatProviderHttpError(providerLabel: String, code: Int, body: String): String {
        val brief = body.replace(Regex("\\s+"), " ").trim().take(160)
        return when (code) {
            401, 403 -> "$providerLabel: chiave API o permessi non validi (HTTP $code)"
            429 -> "$providerLabel: limite richieste superato (HTTP 429)"
            else -> if (brief.isNotBlank()) "$providerLabel: HTTP $code ($brief)" else "$providerLabel: HTTP $code"
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

    // ── Picker unificato: tutti i modelli disponibili → enabledAnalysisModelIds ──

    private val _showAllModelsPicker = MutableStateFlow(false)
    val showAllModelsPicker: StateFlow<Boolean> = _showAllModelsPicker.asStateFlow()

    fun openAllModelsPicker() { _showAllModelsPicker.value = true }
    fun dismissAllModelsPicker() { _showAllModelsPicker.value = false }

    fun saveAllEnabledModels(ids: Set<String>) {
        viewModelScope.launch { settingsRepository.setEnabledAnalysisModelIds(ids) }
        _showAllModelsPicker.value = false
    }

    fun clearHistory() { viewModelScope.launch { historyRepository.clearHistory() } }

    fun resetAllSettings() {
        viewModelScope.launch {
            settingsRepository.resetAll()
            AiModelCatalog.clearOpenAiModels()
            AiModelCatalog.clearAnthropicModels()
            AiModelCatalog.clearGeminiModels()
            AiModelCatalog.clearBedrockModels()
            AiModelCatalog.clearBedrockIamModels()
            _providerTestStates.value = emptyMap()
            refreshAvailableModels()
        }
    }

    private fun computeAvailableModels(): List<AiModel> {
        val isOffline = !connectivityObserver.isOnlineNow()
        val hasMantle = settingsRepository.hasBedrockCredentials()
        val hasIam = settingsRepository.hasBedrockIamCredentials()
        return AiModelCatalog.ALL.filter { model ->
            if (isOffline) return@filter false
            when (model.provider) {
                AiProviderType.BEDROCK_CLAUDE,
                AiProviderType.BEDROCK_GPT -> hasMantle
                AiProviderType.BEDROCK_IAM_CLAUDE,
                AiProviderType.BEDROCK_IAM_GPT,
                AiProviderType.BEDROCK_IAM_AMAZON -> hasIam
                AiProviderType.VERTEX_AI -> settingsRepository.hasVertexAiCredentials()
                else -> settingsRepository.getApiKey(model.provider) != null
            }
        }
    }
}
