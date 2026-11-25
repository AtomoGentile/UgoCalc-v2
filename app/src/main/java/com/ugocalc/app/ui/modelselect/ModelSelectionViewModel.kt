package com.ugocalc.app.ui.modelselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.remote.ai.client.AwsSigV4Signer
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.domain.model.AiEffort
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiModelTier
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.domain.model.recommendedTier
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named

@androidx.compose.runtime.Immutable
data class ModelSelectionUiState(
    val availableModels: List<AiModel> = emptyList(),
    val selectedModelUids: Set<String> = emptySet(),
    val effort: AiEffort = AiEffort.MEDIUM,
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class ModelSelectionViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionStore: AnalysisSessionStore,
    @Named("no_auth") private val httpClient: OkHttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelSelectionUiState())
    val uiState: StateFlow<ModelSelectionUiState> = _uiState

    val isSavingCredentials: StateFlow<Boolean> = sessionStore.isSavingCredentials

    private var hasInitializedSelection = false

    init {
        combine(
            settingsRepository.settings,
            connectivityObserver.observe(),
            AiModelCatalog.catalogVersion,
        ) { userSettings, isOnline, _ ->
            val isOffline = !isOnline
            val hasMantle = settingsRepository.hasBedrockCredentials()
            val hasIam = settingsRepository.hasBedrockIamCredentials()
            val hasVertex = settingsRepository.hasVertexAiCredentials()
            val enabledIds = userSettings.enabledAnalysisModelIds

            val models = if (isOffline) {
                emptyList()
            } else {
                AiModelCatalog.ALL.filter { model ->
                    val isEnabled = enabledIds.isEmpty() || model.uid in enabledIds || model.id in enabledIds
                    if (!isEnabled) return@filter false
                    when (model.provider) {
                        AiProviderType.BEDROCK_CLAUDE, AiProviderType.BEDROCK_GPT -> hasMantle
                        AiProviderType.BEDROCK_IAM_CLAUDE, AiProviderType.BEDROCK_IAM_GPT, AiProviderType.BEDROCK_IAM_AMAZON -> hasIam
                        AiProviderType.VERTEX_AI -> hasVertex
                        else -> !settingsRepository.getApiKey(model.provider).isNullOrBlank()
                    }
                }
            }
            Triple(
                models.sortedBy {
                    when (it.recommendedTier()) {
                        AiModelTier.RECOMMENDED -> 0
                        AiModelTier.FAST -> 1
                        AiModelTier.ACCURATE -> 2
                        AiModelTier.STANDARD -> 3
                    }
                },
                isOffline,
                userSettings.aiEffort,
            )
        }
            .onEach { (models, isOffline, effort) ->
                val availableUids = models.map { it.uid }.toSet()
                _uiState.update { state ->
                    val nextSelection = when {
                        isOffline -> state.selectedModelUids
                        models.isEmpty() -> emptySet()
                        !hasInitializedSelection -> {
                            hasInitializedSelection = true
                            val rec = models.firstOrNull { it.recommendedTier() == AiModelTier.RECOMMENDED }
                                ?: models.firstOrNull { it.recommendedTier() == AiModelTier.FAST }
                                ?: models.firstOrNull()
                            if (rec != null) setOf(rec.uid) else emptySet()
                        }
                        else -> state.selectedModelUids.intersect(availableUids)
                    }
                    state.copy(
                        availableModels = models,
                        selectedModelUids = nextSelection,
                        effort = effort,
                        isOffline = isOffline,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Esegue un refresh one-shot delle liste modelli dai provider configurati (solo se mancanti in
     * memoria/cache), senza aprire nuovi collettori di Flow.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                var mantleIds = settingsRepository.getBedrockAvailableModels()
                if (mantleIds.isEmpty() && settingsRepository.hasBedrockCredentials()) {
                    mantleIds = fetchModelsMantle() ?: emptyList()
                }
                if (mantleIds.isNotEmpty() && AiModelCatalog.BEDROCK_MANTLE_OPTIONS.isEmpty()) {
                    AiModelCatalog.setBedrockModels(mantleIds)
                }

                var iamIds = settingsRepository.getBedrockIamAvailableModels()
                if (iamIds.isEmpty() && settingsRepository.hasBedrockIamCredentials()) {
                    iamIds = fetchModelsIam() ?: emptyList()
                }
                if (iamIds.isNotEmpty() && AiModelCatalog.BEDROCK_IAM_OPTIONS.isEmpty()) {
                    AiModelCatalog.setBedrockIamModels(iamIds)
                }

                val geminiIds = settingsRepository.getGeminiAvailableModels()
                if (geminiIds.isNotEmpty()) {
                    val current = AiModelCatalog.ALL
                        .filter { it.provider == AiProviderType.GEMINI }
                        .map { it.id }
                        .toSet()
                    if (current != geminiIds.toSet()) {
                        AiModelCatalog.setGeminiModels(
                            geminiIds,
                            settingsRepository.getGeminiThinkingCapabilities(),
                        )
                    }
                }

                val openAiIds = settingsRepository.getOpenAiAvailableModels()
                if (openAiIds.isNotEmpty()) {
                    val current = AiModelCatalog.ALL
                        .filter { it.provider == AiProviderType.OPENAI_API }
                        .map { it.id }
                        .toSet()
                    if (current != openAiIds.toSet()) {
                        AiModelCatalog.setOpenAiModels(openAiIds)
                    }
                }

                val anthropicIds = settingsRepository.getAnthropicAvailableModels()
                if (anthropicIds.isNotEmpty()) {
                    val current = AiModelCatalog.ALL
                        .filter { it.provider == AiProviderType.ANTHROPIC }
                        .map { it.id }
                        .toSet()
                    if (current != anthropicIds.toSet()) {
                        AiModelCatalog.setAnthropicModels(anthropicIds)
                    }
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun fetchModelsMantle(): List<String>? = withContext(Dispatchers.IO) {
        try {
            val region = settingsRepository.getBedrockRegion()
            val apiKey = settingsRepository.getBedrockMantleApiKey() ?: return@withContext null
            val request = Request.Builder()
                .url("https://bedrock.$region.amazonaws.com/foundation-models?byOutputModality=TEXT")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val ids = parseModelIds(body)
                    if (ids.isNotEmpty()) {
                        settingsRepository.setBedrockAvailableModels(ids)
                        ids
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchModelsIam(): List<String>? = withContext(Dispatchers.IO) {
        try {
            val accessKey = settingsRepository.getBedrockIamAccessKey() ?: return@withContext null
            val secretKey = settingsRepository.getBedrockIamSecretKey() ?: return@withContext null
            val region = settingsRepository.getBedrockIamRegion()
            val url = "https://bedrock.$region.amazonaws.com/foundation-models?byOutputModality=TEXT"
            val baseRequest = Request.Builder().url(url).get().build()
            val signedRequest = AwsSigV4Signer.sign(baseRequest, accessKey, secretKey, region, ByteArray(0))
            httpClient.newCall(signedRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val ids = parseModelIds(body)
                    if (ids.isNotEmpty()) {
                        settingsRepository.setBedrockIamAvailableModels(ids)
                        ids
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseModelIds(json: String): List<String> {
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
            models.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toggleModel(uid: String) {
        _uiState.update { state ->
            val newSelection = if (uid in state.selectedModelUids) {
                state.selectedModelUids - uid
            } else {
                state.selectedModelUids + uid
            }
            state.copy(selectedModelUids = newSelection)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedModelUids = state.availableModels.map { it.uid }.toSet())
        }
    }

    fun selectOnlyOne() {
        _uiState.update { state ->
            val preferred = state.availableModels.firstOrNull { it.uid in state.selectedModelUids }
                ?: state.availableModels.firstOrNull()
            state.copy(selectedModelUids = if (preferred != null) setOf(preferred.uid) else emptySet())
        }
    }

    fun selectRecommended() {
        _uiState.update { state ->
            val best = state.availableModels.firstOrNull { it.recommendedTier() == AiModelTier.RECOMMENDED }
                ?: state.availableModels.firstOrNull { it.recommendedTier() == AiModelTier.FAST }
                ?: state.availableModels.firstOrNull()
            state.copy(selectedModelUids = if (best != null) setOf(best.uid) else emptySet())
        }
    }

    fun setEffort(effort: AiEffort) {
        _uiState.update { it.copy(effort = effort) }
        viewModelScope.launch { settingsRepository.setAiEffort(effort) }
    }

    fun confirmSelectionAndProceed(): List<String> {
        val selected = _uiState.value.availableModels.filter { it.uid in _uiState.value.selectedModelUids }
        sessionStore.setSelectedModels(selected)
        return selected.map { it.uid }
    }
}
