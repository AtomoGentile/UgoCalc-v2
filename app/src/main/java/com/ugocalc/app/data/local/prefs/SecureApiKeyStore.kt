package com.ugocalc.app.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ugocalc.app.domain.model.AiProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le API key inserite in Impostazioni > API (§2.2) sono dati sensibili: vengono salvate in
 * EncryptedSharedPreferences (backed da Android Keystore), MAI nel database Room in chiaro, e sono
 * escluse dal backup automatico (vedi data_extraction_rules.xml / backup_rules.xml).
 */
@Singleton
class SecureApiKeyStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ugocalc_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiKey(provider: AiProviderType): String? = prefs.getString(keyFor(provider), null)

    fun setApiKey(provider: AiProviderType, apiKey: String) {
        prefs.edit().putString(keyFor(provider), apiKey).apply()
    }

    fun clearApiKey(provider: AiProviderType) {
        prefs.edit().remove(keyFor(provider)).apply()
    }

    /**
     * Contenuto grezzo del file service account Google Cloud (JSON) usato per l'autenticazione
     * OAuth2 di Gemini via Vertex AI (vedi VertexAiTokenProvider). Cifrato allo stesso modo delle
     * API key: mai in chiaro su disco, mai nel repository (vedi .gitignore).
     */
    fun getServiceAccountJson(): String? = prefs.getString(KEY_GEMINI_SERVICE_ACCOUNT, null)

    fun setServiceAccountJson(json: String) {
        prefs.edit().putString(KEY_GEMINI_SERVICE_ACCOUNT, json).apply()
    }

    fun clearServiceAccountJson() {
        prefs.edit().remove(KEY_GEMINI_SERVICE_ACCOUNT).apply()
    }

    fun hasAnyApiKey(): Boolean = AiProviderType.entries.any { getApiKey(it) != null }

    private fun keyFor(provider: AiProviderType) = "api_key_${provider.name}"

    private companion object {
        const val KEY_GEMINI_SERVICE_ACCOUNT = "gemini_vertex_service_account_json"
    }
}
