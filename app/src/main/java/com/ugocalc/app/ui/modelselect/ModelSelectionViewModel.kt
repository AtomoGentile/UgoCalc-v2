package com.ugocalc.app.ui.modelselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.remote.ai.client.AwsSigV4Signer
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    val isOffline: Boolean = false,
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

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .onEach { userSettings ->
                    val isOffline = !connectivityObserver.isOnlineNow()
                    val hasMantle = settingsRepository.hasBedrockCredentials()
                    val hasIam = settingsRepository.hasBedrockIamCredentials()
                    val enabledIds = userSettings.enabledAnalysisModelIds

                    val models = AiModelCatalog.ALL.filter { model ->
                        if (isOffline) return@filter false
                        val isEnabled = enabledIds.isEmpty() || model.id in enabledIds
                        if (!isEnabled) return@filter false
                        when (model.provider) {
                            AiProviderType.BEDROCK_CLAUDE, AiProviderType.BEDROCK_GPT -> hasMantle
                            AiProviderType.BEDROCK_IAM_CLAUDE, AiProviderType.BEDROCK_IAM_GPT -> hasIam
                            else -> settingsRepository.getApiKey(model.provider) != null
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        availableModels = models,
                        isOffline = isOffline,
                        selectedModelUids = if (_uiState.value.selectedModelUids.isEmpty()) {
                            models.map { it.uid }.toSet()
                        } else {
                            _uiState.value.selectedModelUids
                        },
                    )
                }
                .launchIn(viewModelScope)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // Re-hydrate catalog from storage; if empty, fetch from AWS
            var mantleIds = settingsRepository.getBedrockAvailableModels()
            if (mantleIds.isEmpty() && settingsRepository.hasBedrockCredentials()) {
                mantleIds = fetchModelsMantle() ?: emptyList()
            }
            if (mantleIds.isNotEmpty()) AiModelCatalog.setBedrockModels(mantleIds)

            var iamIds = settingsRepository.getBedrockIamAvailableModels()
            if (iamIds.isEmpty() && settingsRepository.hasBedrockIamCredentials()) {
                iamIds = fetchModelsIam() ?: emptyList()
            }
            if (iamIds.isNotEmpty()) AiModelCatalog.setBedrockIamModels(iamIds)

            val isOffline = !connectivityObserver.isOnlineNow()
            val hasMantle = settingsRepository.hasBedrockCredentials()
            val hasIam = settingsRepository.hasBedrockIamCredentials()

            settingsRepository.settings.collect { userSettings ->
                val enabledIds = userSettings.enabledAnalysisModelIds
                val models = AiModelCatalog.ALL.filter { model ->
                    if (isOffline) return@filter false
                    val isEnabled = enabledIds.isEmpty() || model.id in enabledIds
                    if (!isEnabled) return@filter false
                    when (model.provider) {
                        AiProviderType.BEDROCK_CLAUDE, AiProviderType.BEDROCK_GPT -> hasMantle
                        AiProviderType.BEDROCK_IAM_CLAUDE, AiProviderType.BEDROCK_IAM_GPT -> hasIam
                        else -> settingsRepository.getApiKey(model.provider) != null
                    }
                }
                _uiState.value = _uiState.value.copy(
                    availableModels = models,
                    isOffline = isOffline,
                    selectedModelUids = if (_uiState.value.selectedModelUids.isEmpty()) {
                        models.map { it.uid }.toSet()
                    } else {
                        _uiState.value.selectedModelUids
                    },
                )
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
        } catch (_: Exception) { null }
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
        } catch (_: Exception) { null }
    }

    private fun parseModelIds(json: String): List<String> {
        return try {
            val arr = JSONObject(json).getJSONArray("modelSummaries")
            (0 until arr.length()).map { arr.getJSONObject(it).getString("modelId") }
        } catch (_: Exception) { emptyList() }
    }

    fun toggleModel(uid: String) {
        _uiState.value = _uiState.value.let { state ->
            val newSelection = if (uid in state.selectedModelUids) state.selectedModelUids - uid else state.selectedModelUids + uid
            state.copy(selectedModelUids = newSelection)
        }
    }

    fun confirmSelectionAndProceed(): List<String> {
        val selected = _uiState.value.availableModels.filter { it.uid in _uiState.value.selectedModelUids }
        sessionStore.setSelectedModels(selected)
        return selected.map { it.id }
    }
}
