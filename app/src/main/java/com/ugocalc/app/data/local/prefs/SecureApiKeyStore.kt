package com.ugocalc.app.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ugocalc.app.domain.model.AiProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureApiKeyStore @Inject constructor(@ApplicationContext context: Context) {

    @Suppress("DEPRECATION")
    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefsWithRecovery(context)
    }

    @Suppress("DEPRECATION")
    private fun createEncryptedPrefsWithRecovery(context: Context): SharedPreferences {
        fun create(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return try {
            create()
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "EncryptedSharedPreferences corrotti (es. ripristino backup); eliminazione e ricreazione.", e)
            recoverAndRecreate(context, ::create)
        } catch (e: IOException) {
            Log.w(TAG, "Errore I/O apertura EncryptedSharedPreferences; eliminazione e ricreazione.", e)
            recoverAndRecreate(context, ::create)
        }
    }

    private fun recoverAndRecreate(context: Context, create: () -> SharedPreferences): SharedPreferences {
        runCatching { context.deleteSharedPreferences(PREFS_FILE_NAME) }
        return try {
            create()
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "MasterKey AndroidKeyStore corrotta; rimozione alias e ricreazione.", e)
            runCatching {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
            runCatching { context.deleteSharedPreferences(PREFS_FILE_NAME) }
            create()
        } catch (e: IOException) {
            Log.w(TAG, "Nuovo errore I/O EncryptedSharedPreferences; ritento dopo pulizia.", e)
            runCatching { context.deleteSharedPreferences(PREFS_FILE_NAME) }
            create()
        }
    }

    // Cache in memoria per evitare letture e decrittografie ripetute sul main thread.
    // ConcurrentHashMap non supporta null, usiamo una stringa vuota come sentinel per "assente"
    // dato che le chiavi reali non sono mai vuote (o sono nulle).
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val NULL_SENTINEL = "__NULL__"

    private fun getCachedString(key: String, defaultValue: String? = null): String? {
        val cached = memoryCache[key]
        if (cached != null) {
            return if (cached == NULL_SENTINEL) defaultValue else cached
        }
        val read = prefs.getString(key, null)
        if (read != null) {
            memoryCache[key] = read
            return read
        } else {
            memoryCache[key] = NULL_SENTINEL
            return defaultValue
        }
    }

    private fun putCachedString(key: String, value: String?) {
        if (value == null) {
            memoryCache[key] = NULL_SENTINEL
        } else {
            memoryCache[key] = value
        }
    }

    private fun removeCachedString(key: String) {
        memoryCache[key] = NULL_SENTINEL
    }

    fun getApiKey(provider: AiProviderType): String? = getCachedString(keyFor(provider))

    fun setApiKey(provider: AiProviderType, apiKey: String) {
        val key = keyFor(provider)
        putCachedString(key, apiKey)
        prefs.edit().putString(key, apiKey).apply()
    }

    fun clearApiKey(provider: AiProviderType) {
        val key = keyFor(provider)
        removeCachedString(key)
        prefs.edit().remove(key).apply()
    }

    /**
     * Contenuto grezzo del file service account Google Cloud (JSON) usato per l'autenticazione
     * OAuth2 di Gemini via Vertex AI (vedi VertexAiTokenProvider). Cifrato allo stesso modo delle
     * API key: mai in chiaro su disco, mai nel repository (vedi .gitignore).
     */
    fun getServiceAccountJson(): String? = getCachedString(KEY_GEMINI_SERVICE_ACCOUNT)

    fun setServiceAccountJson(json: String) {
        putCachedString(KEY_GEMINI_SERVICE_ACCOUNT, json)
        prefs.edit().putString(KEY_GEMINI_SERVICE_ACCOUNT, json).apply()
    }

    fun clearServiceAccountJson() {
        removeCachedString(KEY_GEMINI_SERVICE_ACCOUNT)
        prefs.edit().remove(KEY_GEMINI_SERVICE_ACCOUNT).apply()
    }

    fun hasAnyApiKey(): Boolean = AiProviderType.entries.any { getApiKey(it) != null } || hasBedrockCredentials() || hasBedrockIamCredentials() || hasVertexAiCredentials()

    // ── AWS Bedrock Mantle: API key + project ID + region ──────────────────────

    fun getBedrockMantleApiKey(): String? = getCachedString(KEY_BEDROCK_MANTLE_API_KEY)
    fun getBedrockMantleProjectId(): String = getCachedString(KEY_BEDROCK_MANTLE_PROJECT_ID, DEFAULT_MANTLE_PROJECT_ID) ?: DEFAULT_MANTLE_PROJECT_ID
    fun getBedrockRegion(): String = getCachedString(KEY_BEDROCK_REGION, DEFAULT_BEDROCK_REGION) ?: DEFAULT_BEDROCK_REGION

    fun setBedrockCredentials(mantleApiKey: String, projectId: String = DEFAULT_MANTLE_PROJECT_ID, region: String = DEFAULT_BEDROCK_REGION) {
        putCachedString(KEY_BEDROCK_MANTLE_API_KEY, mantleApiKey)
        putCachedString(KEY_BEDROCK_MANTLE_PROJECT_ID, projectId)
        putCachedString(KEY_BEDROCK_REGION, region)
        prefs.edit()
            .putString(KEY_BEDROCK_MANTLE_API_KEY, mantleApiKey)
            .putString(KEY_BEDROCK_MANTLE_PROJECT_ID, projectId)
            .putString(KEY_BEDROCK_REGION, region)
            .apply()
    }

    fun clearBedrockCredentials() {
        removeCachedString(KEY_BEDROCK_MANTLE_API_KEY)
        removeCachedString(KEY_BEDROCK_MANTLE_PROJECT_ID)
        removeCachedString(KEY_BEDROCK_REGION)
        prefs.edit()
            .remove(KEY_BEDROCK_MANTLE_API_KEY)
            .remove(KEY_BEDROCK_MANTLE_PROJECT_ID)
            .remove(KEY_BEDROCK_REGION)
            .apply()
    }

    fun hasBedrockCredentials(): Boolean = getBedrockMantleApiKey() != null

    // ── AWS Bedrock Runtime IAM: Access Key + Secret + Account ID + Region ─────

    fun getBedrockIamAccessKey(): String? = getCachedString(KEY_BEDROCK_IAM_ACCESS_KEY)
    fun getBedrockIamSecretKey(): String? = getCachedString(KEY_BEDROCK_IAM_SECRET_KEY)
    fun getBedrockIamAccountId(): String? = getCachedString(KEY_BEDROCK_IAM_ACCOUNT_ID)
    fun getBedrockIamRegion(): String = getCachedString(KEY_BEDROCK_IAM_REGION, DEFAULT_BEDROCK_REGION) ?: DEFAULT_BEDROCK_REGION

    fun setBedrockIamCredentials(accessKey: String, secretKey: String, accountId: String, region: String = DEFAULT_BEDROCK_REGION) {
        putCachedString(KEY_BEDROCK_IAM_ACCESS_KEY, accessKey)
        putCachedString(KEY_BEDROCK_IAM_SECRET_KEY, secretKey)
        putCachedString(KEY_BEDROCK_IAM_ACCOUNT_ID, accountId)
        putCachedString(KEY_BEDROCK_IAM_REGION, region)
        prefs.edit()
            .putString(KEY_BEDROCK_IAM_ACCESS_KEY, accessKey)
            .putString(KEY_BEDROCK_IAM_SECRET_KEY, secretKey)
            .putString(KEY_BEDROCK_IAM_ACCOUNT_ID, accountId)
            .putString(KEY_BEDROCK_IAM_REGION, region)
            .apply()
    }

    fun clearBedrockIamCredentials() {
        removeCachedString(KEY_BEDROCK_IAM_ACCESS_KEY)
        removeCachedString(KEY_BEDROCK_IAM_SECRET_KEY)
        removeCachedString(KEY_BEDROCK_IAM_ACCOUNT_ID)
        removeCachedString(KEY_BEDROCK_IAM_REGION)
        prefs.edit()
            .remove(KEY_BEDROCK_IAM_ACCESS_KEY)
            .remove(KEY_BEDROCK_IAM_SECRET_KEY)
            .remove(KEY_BEDROCK_IAM_ACCOUNT_ID)
            .remove(KEY_BEDROCK_IAM_REGION)
            .apply()
    }

    fun hasBedrockIamCredentials(): Boolean =
        getBedrockIamAccessKey() != null && getBedrockIamSecretKey() != null && getBedrockIamAccountId() != null

    fun getBedrockAvailableModels(): List<String> {
        val raw = getCachedString(KEY_BEDROCK_MODELS) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setBedrockAvailableModels(modelIds: List<String>) {
        val raw = modelIds.joinToString("|")
        putCachedString(KEY_BEDROCK_MODELS, raw)
        prefs.edit().putString(KEY_BEDROCK_MODELS, raw).apply()
    }

    fun getBedrockIamAvailableModels(): List<String> {
        val raw = getCachedString(KEY_BEDROCK_IAM_MODELS) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setBedrockIamAvailableModels(modelIds: List<String>) {
        val raw = modelIds.joinToString("|")
        putCachedString(KEY_BEDROCK_IAM_MODELS, raw)
        prefs.edit().putString(KEY_BEDROCK_IAM_MODELS, raw).apply()
    }

    // ── Google AI Studio: lista modelli disponibili ────────────────────────────

    fun getGeminiAvailableModels(): List<String> {
        val raw = getCachedString(KEY_GEMINI_MODELS) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setGeminiAvailableModels(modelIds: List<String>) {
        val raw = modelIds.joinToString("|")
        putCachedString(KEY_GEMINI_MODELS, raw)
        prefs.edit().putString(KEY_GEMINI_MODELS, raw).apply()
    }

    fun clearGeminiAvailableModels() {
        removeCachedString(KEY_GEMINI_MODELS)
        prefs.edit().remove(KEY_GEMINI_MODELS).apply()
    }

    // ── OpenAI API: lista modelli disponibili ──────────────────────────────────

    fun getOpenAiAvailableModels(): List<String> {
        val raw = getCachedString(KEY_OPENAI_MODELS) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setOpenAiAvailableModels(modelIds: List<String>) {
        val raw = modelIds.joinToString("|")
        putCachedString(KEY_OPENAI_MODELS, raw)
        prefs.edit().putString(KEY_OPENAI_MODELS, raw).apply()
    }

    fun clearOpenAiAvailableModels() {
        removeCachedString(KEY_OPENAI_MODELS)
        prefs.edit().remove(KEY_OPENAI_MODELS).apply()
    }

    // ── Anthropic API: lista modelli disponibili ───────────────────────────────

    fun getAnthropicAvailableModels(): List<String> {
        val raw = getCachedString(KEY_ANTHROPIC_MODELS) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setAnthropicAvailableModels(modelIds: List<String>) {
        val raw = modelIds.joinToString("|")
        putCachedString(KEY_ANTHROPIC_MODELS, raw)
        prefs.edit().putString(KEY_ANTHROPIC_MODELS, raw).apply()
    }

    fun clearAnthropicAvailableModels() {
        removeCachedString(KEY_ANTHROPIC_MODELS)
        prefs.edit().remove(KEY_ANTHROPIC_MODELS).apply()
    }

    // ── Google Vertex AI: Project ID + Region ──────────────────────────────────

    fun getVertexAiProjectId(): String? = getCachedString(KEY_VERTEX_AI_PROJECT_ID)
    fun getVertexAiRegion(): String = getCachedString(KEY_VERTEX_AI_REGION, DEFAULT_VERTEX_AI_REGION) ?: DEFAULT_VERTEX_AI_REGION

    fun setVertexAiCredentials(projectId: String, region: String = DEFAULT_VERTEX_AI_REGION) {
        putCachedString(KEY_VERTEX_AI_PROJECT_ID, projectId)
        putCachedString(KEY_VERTEX_AI_REGION, region)
        prefs.edit()
            .putString(KEY_VERTEX_AI_PROJECT_ID, projectId)
            .putString(KEY_VERTEX_AI_REGION, region)
            .apply()
    }

    fun clearVertexAiCredentials() {
        removeCachedString(KEY_VERTEX_AI_PROJECT_ID)
        removeCachedString(KEY_VERTEX_AI_REGION)
        prefs.edit()
            .remove(KEY_VERTEX_AI_PROJECT_ID)
            .remove(KEY_VERTEX_AI_REGION)
            .apply()
    }

    fun clearAll() {
        memoryCache.clear()
        prefs.edit().clear().apply()
    }

    fun hasVertexAiCredentials(): Boolean =
        !getVertexAiProjectId().isNullOrBlank() && !getServiceAccountJson().isNullOrBlank()

    private fun keyFor(provider: AiProviderType) = "api_key_${provider.name}"

    private companion object {
        const val TAG = "SecureApiKeyStore"
        const val PREFS_FILE_NAME = "ugocalc_secure_prefs"
        const val KEY_GEMINI_SERVICE_ACCOUNT = "gemini_vertex_service_account_json"
        const val KEY_GEMINI_MODELS = "gemini_available_models"
        const val KEY_OPENAI_MODELS = "openai_available_models"
        const val KEY_ANTHROPIC_MODELS = "anthropic_available_models"
        const val KEY_BEDROCK_MANTLE_API_KEY = "bedrock_mantle_api_key"
        const val KEY_BEDROCK_MANTLE_PROJECT_ID = "bedrock_mantle_project_id"
        const val KEY_BEDROCK_REGION = "bedrock_aws_region"
        const val KEY_BEDROCK_MODELS = "bedrock_available_models"
        const val KEY_BEDROCK_IAM_MODELS = "bedrock_iam_available_models"
        const val KEY_BEDROCK_IAM_ACCESS_KEY = "bedrock_iam_access_key"
        const val KEY_BEDROCK_IAM_SECRET_KEY = "bedrock_iam_secret_key"
        const val KEY_BEDROCK_IAM_ACCOUNT_ID = "bedrock_iam_account_id"
        const val KEY_BEDROCK_IAM_REGION = "bedrock_iam_region"
        const val KEY_VERTEX_AI_PROJECT_ID = "vertex_ai_project_id"
        const val KEY_VERTEX_AI_REGION = "vertex_ai_region"
        const val DEFAULT_BEDROCK_REGION = "us-east-1"
        const val DEFAULT_MANTLE_PROJECT_ID = "proj_thoat36pxhdzt3eioqvl"
        const val DEFAULT_VERTEX_AI_REGION = "us-central1"
    }
}
