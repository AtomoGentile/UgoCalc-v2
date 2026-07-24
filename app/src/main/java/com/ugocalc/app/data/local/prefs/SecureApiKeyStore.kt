package com.ugocalc.app.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ugocalc.app.domain.model.AiProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureApiKeyStore @Inject constructor(@ApplicationContext context: Context) {

    @Suppress("DEPRECATION")
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

    fun hasAnyApiKey(): Boolean = AiProviderType.entries.any { getApiKey(it) != null } || hasBedrockCredentials() || hasBedrockIamCredentials()

    // ── AWS Bedrock Mantle: API key + project ID + region ──────────────────────

    fun getBedrockMantleApiKey(): String? = prefs.getString(KEY_BEDROCK_MANTLE_API_KEY, null)
    fun getBedrockMantleProjectId(): String = prefs.getString(KEY_BEDROCK_MANTLE_PROJECT_ID, null) ?: DEFAULT_MANTLE_PROJECT_ID
    fun getBedrockRegion(): String = prefs.getString(KEY_BEDROCK_REGION, null) ?: DEFAULT_BEDROCK_REGION

    fun setBedrockCredentials(mantleApiKey: String, projectId: String = DEFAULT_MANTLE_PROJECT_ID, region: String = DEFAULT_BEDROCK_REGION) {
        prefs.edit()
            .putString(KEY_BEDROCK_MANTLE_API_KEY, mantleApiKey)
            .putString(KEY_BEDROCK_MANTLE_PROJECT_ID, projectId)
            .putString(KEY_BEDROCK_REGION, region)
            .apply()
    }

    fun clearBedrockCredentials() {
        prefs.edit()
            .remove(KEY_BEDROCK_MANTLE_API_KEY)
            .remove(KEY_BEDROCK_MANTLE_PROJECT_ID)
            .remove(KEY_BEDROCK_REGION)
            .apply()
    }

    fun hasBedrockCredentials(): Boolean = getBedrockMantleApiKey() != null

    // ── AWS Bedrock Runtime IAM: Access Key + Secret + Region ─────────────────

    fun getBedrockIamAccessKey(): String? = prefs.getString(KEY_BEDROCK_IAM_ACCESS_KEY, null)
    fun getBedrockIamSecretKey(): String? = prefs.getString(KEY_BEDROCK_IAM_SECRET_KEY, null)
    fun getBedrockIamRegion(): String = prefs.getString(KEY_BEDROCK_IAM_REGION, null) ?: DEFAULT_BEDROCK_REGION

    fun setBedrockIamCredentials(accessKey: String, secretKey: String, region: String = DEFAULT_BEDROCK_REGION) {
        prefs.edit()
            .putString(KEY_BEDROCK_IAM_ACCESS_KEY, accessKey)
            .putString(KEY_BEDROCK_IAM_SECRET_KEY, secretKey)
            .putString(KEY_BEDROCK_IAM_REGION, region)
            .apply()
    }

    fun clearBedrockIamCredentials() {
        prefs.edit()
            .remove(KEY_BEDROCK_IAM_ACCESS_KEY)
            .remove(KEY_BEDROCK_IAM_SECRET_KEY)
            .remove(KEY_BEDROCK_IAM_REGION)
            .apply()
    }

    fun hasBedrockIamCredentials(): Boolean =
        getBedrockIamAccessKey() != null && getBedrockIamSecretKey() != null

    fun getBedrockAvailableModels(): List<String> {
        val raw = prefs.getString(KEY_BEDROCK_MODELS, null) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setBedrockAvailableModels(modelIds: List<String>) {
        prefs.edit().putString(KEY_BEDROCK_MODELS, modelIds.joinToString("|")).apply()
    }

    fun getBedrockIamAvailableModels(): List<String> {
        val raw = prefs.getString(KEY_BEDROCK_IAM_MODELS, null) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setBedrockIamAvailableModels(modelIds: List<String>) {
        prefs.edit().putString(KEY_BEDROCK_IAM_MODELS, modelIds.joinToString("|")).apply()
    }

    private fun keyFor(provider: AiProviderType) = "api_key_${provider.name}"

    private companion object {
        const val KEY_GEMINI_SERVICE_ACCOUNT = "gemini_vertex_service_account_json"
        const val KEY_BEDROCK_MANTLE_API_KEY = "bedrock_mantle_api_key"
        const val KEY_BEDROCK_MANTLE_PROJECT_ID = "bedrock_mantle_project_id"
        const val KEY_BEDROCK_REGION = "bedrock_aws_region"
        const val KEY_BEDROCK_MODELS = "bedrock_available_models"
        const val KEY_BEDROCK_IAM_MODELS = "bedrock_iam_available_models"
        const val KEY_BEDROCK_IAM_ACCESS_KEY = "bedrock_iam_access_key"
        const val KEY_BEDROCK_IAM_SECRET_KEY = "bedrock_iam_secret_key"
        const val KEY_BEDROCK_IAM_REGION = "bedrock_iam_region"
        const val DEFAULT_BEDROCK_REGION = "us-east-1"
        const val DEFAULT_MANTLE_PROJECT_ID = "proj_thoat36pxhdzt3eioqvl"
    }
}
