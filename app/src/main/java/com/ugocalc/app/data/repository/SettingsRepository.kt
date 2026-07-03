package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.prefs.SecureApiKeyStore
import com.ugocalc.app.data.local.prefs.SettingsDataStore
import com.ugocalc.app.data.local.prefs.UserSettings
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
    suspend fun setConfidenceThresholdPercent(percent: Int) = dataStore.setConfidenceThresholdPercent(percent)
    suspend fun setNotificationsEnabled(enabled: Boolean) = dataStore.setNotificationsEnabled(enabled)
    suspend fun setFavoritesEnabled(enabled: Boolean) = dataStore.setFavoritesEnabled(enabled)
    suspend fun setPrimaryColor(argb: Int?) = dataStore.setPrimaryColor(argb)
    suspend fun setSecondaryColor(argb: Int?) = dataStore.setSecondaryColor(argb)
    suspend fun setTertiaryColor(argb: Int?) = dataStore.setTertiaryColor(argb)
    suspend fun setBackgroundColor(argb: Int?) = dataStore.setBackgroundColor(argb)
    suspend fun setErrorColor(argb: Int?) = dataStore.setErrorColor(argb)
    suspend fun setHistoryChartEnabled(enabled: Boolean) = dataStore.setHistoryChartEnabled(enabled)
    suspend fun setFontFamily(name: String?) = dataStore.setFontFamily(name)
    suspend fun setFontColor(argb: Int?) = dataStore.setFontColor(argb)
    suspend fun setHasSeenDataDisclaimer(seen: Boolean) = dataStore.setHasSeenDataDisclaimer(seen)
    suspend fun setLastSeenVersionCode(versionCode: Int) = dataStore.setLastSeenVersionCode(versionCode)

    fun getApiKey(provider: AiProviderType): String? = apiKeyStore.getApiKey(provider)
    fun setApiKey(provider: AiProviderType, key: String) = apiKeyStore.setApiKey(provider, key)
    fun clearApiKey(provider: AiProviderType) = apiKeyStore.clearApiKey(provider)
    fun hasAnyApiKeyConfigured(): Boolean = apiKeyStore.hasAnyApiKey()
}
