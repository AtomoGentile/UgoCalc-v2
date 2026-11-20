package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.prefs.SecureApiKeyStore
import com.ugocalc.app.data.local.prefs.SettingsDataStore
import com.ugocalc.app.data.local.prefs.UserSettings
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.AiProviderType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Facciata unica su Impostazioni (§2.2): preferenze "semplici" via DataStore + API key cifrate. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val apiKeyStore: SecureApiKeyStore,
) {
    val settings: Flow<UserSettings> = dataStore.settingsFlow

    suspend fun setTheme(theme: com.ugocalc.app.data.local.prefs.AppTheme) = dataStore.setTheme(theme)
    suspend fun setTextScale(scale: Float) = dataStore.setTextScale(scale)
    suspend fun setPreferredModelIds(ids: Set<String>) = dataStore.setPreferredModelIds(ids)
    suspend fun setConfidenceIndicatorEnabled(enabled: Boolean) = dataStore.setConfidenceIndicatorEnabled(enabled)
    suspend fun setConfidenceAlertThresholdPercent(percent: Int) = dataStore.setConfidenceAlertThresholdPercent(percent)
    suspend fun setNotificationsEnabled(enabled: Boolean) = dataStore.setNotificationsEnabled(enabled)
    suspend fun setFavoritesEnabled(enabled: Boolean) = dataStore.setFavoritesEnabled(enabled)
    suspend fun setPrimaryColor(argb: Int?) = dataStore.setPrimaryColor(argb)
    suspend fun setSecondaryColor(argb: Int?) = dataStore.setSecondaryColor(argb)
    suspend fun setTertiaryColor(argb: Int?) = dataStore.setTertiaryColor(argb)
    suspend fun setBackgroundColor(argb: Int?) = dataStore.setBackgroundColor(argb)
    suspend fun setErrorColor(argb: Int?) = dataStore.setErrorColor(argb)
    suspend fun setPrimaryColorRistorante(argb: Int?) = dataStore.setPrimaryColorRistorante(argb)
    suspend fun setSecondaryColorRistorante(argb: Int?) = dataStore.setSecondaryColorRistorante(argb)
    suspend fun setTertiaryColorRistorante(argb: Int?) = dataStore.setTertiaryColorRistorante(argb)
    suspend fun setBackgroundColorRistorante(argb: Int?) = dataStore.setBackgroundColorRistorante(argb)
    suspend fun setErrorColorRistorante(argb: Int?) = dataStore.setErrorColorRistorante(argb)
    suspend fun setHistoryChartEnabled(enabled: Boolean) = dataStore.setHistoryChartEnabled(enabled)
    suspend fun setFontFamily(name: String?) = dataStore.setFontFamily(name)
    suspend fun setFontColor(argb: Int?) = dataStore.setFontColor(argb)
    suspend fun setHasSeenDataDisclaimer(seen: Boolean) = dataStore.setHasSeenDataDisclaimer(seen)
    suspend fun setLastSeenVersionCode(versionCode: Int) = dataStore.setLastSeenVersionCode(versionCode)
    suspend fun setBiasDataConsentGiven(given: Boolean) = dataStore.setBiasDataConsentGiven(given)
    suspend fun setV2ShadowModeEnabled(enabled: Boolean) = dataStore.setV2ShadowModeEnabled(enabled)
    suspend fun setShadowModelIds(ids: Set<String>) = dataStore.setShadowModelIds(ids)
    suspend fun setEnabledAnalysisModelIds(ids: Set<String>) = dataStore.setEnabledAnalysisModelIds(ids)
    suspend fun setEnabledNormalizationModelIds(ids: Set<String>) = dataStore.setEnabledNormalizationModelIds(ids)
    suspend fun setActiveSessionMode(mode: SessionMode) = dataStore.setActiveSessionMode(mode)
    suspend fun setV2VisibleModeEnabled(enabled: Boolean) = dataStore.setV2VisibleModeEnabled(enabled)
    suspend fun setGlobalBiasEnabled(enabled: Boolean) = dataStore.setGlobalBiasEnabled(enabled)
    suspend fun setHasCompletedOnboarding(completed: Boolean) = dataStore.setHasCompletedOnboarding(completed)
    suspend fun setHasSeededStarterTable(seeded: Boolean) = dataStore.setHasSeededStarterTable(seeded)
    suspend fun setAngleOverlayEnabled(enabled: Boolean) = dataStore.setAngleOverlayEnabled(enabled)

    fun getApiKey(provider: AiProviderType): String? = apiKeyStore.getApiKey(provider)
    fun setApiKey(provider: AiProviderType, key: String) = apiKeyStore.setApiKey(provider, key)
    fun clearApiKey(provider: AiProviderType) = apiKeyStore.clearApiKey(provider)
    fun hasAnyApiKeyConfigured(): Boolean = apiKeyStore.hasAnyApiKey()

    // ── AWS Bedrock Mantle credentials ─────────────────────────────────────────
    fun getBedrockMantleApiKey(): String? = apiKeyStore.getBedrockMantleApiKey()
    fun getBedrockMantleProjectId(): String = apiKeyStore.getBedrockMantleProjectId()
    fun getBedrockRegion(): String = apiKeyStore.getBedrockRegion()
    fun setBedrockCredentials(mantleApiKey: String, projectId: String, region: String) =
        apiKeyStore.setBedrockCredentials(mantleApiKey, projectId, region)
    fun clearBedrockCredentials() = apiKeyStore.clearBedrockCredentials()
    fun hasBedrockCredentials(): Boolean = apiKeyStore.hasBedrockCredentials()
    fun getBedrockAvailableModels(): List<String> = apiKeyStore.getBedrockAvailableModels()
    fun setBedrockAvailableModels(modelIds: List<String>) = apiKeyStore.setBedrockAvailableModels(modelIds)
    fun getBedrockIamAvailableModels(): List<String> = apiKeyStore.getBedrockIamAvailableModels()
    fun setBedrockIamAvailableModels(modelIds: List<String>) = apiKeyStore.setBedrockIamAvailableModels(modelIds)
    fun getGeminiAvailableModels(): List<String> = apiKeyStore.getGeminiAvailableModels()
    fun setGeminiAvailableModels(modelIds: List<String>) = apiKeyStore.setGeminiAvailableModels(modelIds)
    fun clearGeminiAvailableModels() = apiKeyStore.clearGeminiAvailableModels()
    fun getOpenAiAvailableModels(): List<String> = apiKeyStore.getOpenAiAvailableModels()
    fun setOpenAiAvailableModels(modelIds: List<String>) = apiKeyStore.setOpenAiAvailableModels(modelIds)
    fun clearOpenAiAvailableModels() = apiKeyStore.clearOpenAiAvailableModels()
    fun getAnthropicAvailableModels(): List<String> = apiKeyStore.getAnthropicAvailableModels()
    fun setAnthropicAvailableModels(modelIds: List<String>) = apiKeyStore.setAnthropicAvailableModels(modelIds)
    fun clearAnthropicAvailableModels() = apiKeyStore.clearAnthropicAvailableModels()

    // ── AWS Bedrock Runtime IAM credentials ────────────────────────────────────
    fun getBedrockIamAccessKey(): String? = apiKeyStore.getBedrockIamAccessKey()
    fun getBedrockIamSecretKey(): String? = apiKeyStore.getBedrockIamSecretKey()
    fun getBedrockIamAccountId(): String? = apiKeyStore.getBedrockIamAccountId()
    fun getBedrockIamRegion(): String = apiKeyStore.getBedrockIamRegion()
    fun setBedrockIamCredentials(accessKey: String, secretKey: String, accountId: String, region: String) =
        apiKeyStore.setBedrockIamCredentials(accessKey, secretKey, accountId, region)
    fun clearBedrockIamCredentials() = apiKeyStore.clearBedrockIamCredentials()
    fun hasBedrockIamCredentials(): Boolean = apiKeyStore.hasBedrockIamCredentials()

    // ── Google Vertex AI credentials ────────────────────────────────────────────
    fun getVertexAiProjectId(): String? = apiKeyStore.getVertexAiProjectId()
    fun getVertexAiRegion(): String = apiKeyStore.getVertexAiRegion()
    fun setVertexAiCredentials(projectId: String, region: String) =
        apiKeyStore.setVertexAiCredentials(projectId, region)
    fun clearVertexAiCredentials() = apiKeyStore.clearVertexAiCredentials()
    fun hasVertexAiCredentials(): Boolean = apiKeyStore.hasVertexAiCredentials()

    /** Resetta tutte le impostazioni ai valori di default e rimuove le API key salvate. */
    suspend fun resetAll() {
        dataStore.clearAll()
        apiKeyStore.clearAll()
    }
}

