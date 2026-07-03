package com.ugocalc.app.data.remote.ai.auth

import android.content.Context
import android.net.Uri
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ugocalc.app.data.local.prefs.SecureApiKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Errore di autenticazione Google Cloud (service account mancante/non valido, JWT, rete, HTTP). */
class VertexAiAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Stato osservabile dell'autenticazione Gemini/Vertex AI, mostrato in Impostazioni > API. */
sealed class VertexAiAuthStatus {
    data object Unconfigured : VertexAiAuthStatus()
    data object Valid : VertexAiAuthStatus()
    data class Error(val message: String) : VertexAiAuthStatus()
}

/** Sottoinsieme dei campi del JSON service account Google Cloud rilevanti per il flusso JWT Bearer. */
@Serializable
data class ServiceAccountCredentials(
    val type: String = "",
    val project_id: String = "",
    val private_key_id: String = "",
    val private_key: String = "",
    val client_email: String = "",
    val token_uri: String = "",
)

private val serviceAccountJson = Json { ignoreUnknownKeys = true }

fun parseServiceAccountJson(rawJson: String): ServiceAccountCredentials {
    val creds = try {
        serviceAccountJson.decodeFromString<ServiceAccountCredentials>(rawJson)
    } catch (e: SerializationException) {
        throw VertexAiAuthException("Il file selezionato non è un JSON di service account valido", e)
    } catch (e: IllegalArgumentException) {
        throw VertexAiAuthException("Il file selezionato non è un JSON di service account valido", e)
    }
    if (creds.type != "service_account") {
        throw VertexAiAuthException("Il file non è un service account Google Cloud (campo \"type\" mancante o non valido)")
    }
    if (creds.project_id.isBlank() || creds.private_key.isBlank() || creds.client_email.isBlank()) {
        throw VertexAiAuthException("Il file JSON è incompleto: mancano project_id, private_key o client_email")
    }
    return creds
}

@Serializable
private data class TokenResponse(val access_token: String? = null, val expires_in: Long? = null)

@Serializable
private data class TokenErrorResponse(val error: String? = null, val error_description: String? = null)

/**
 * Genera e mantiene aggiornato l'access token OAuth2 di un service account Google Cloud (flusso
 * "JWT Bearer" standard, RFC 7523) per autenticare le chiamate a Gemini via Vertex AI. Il JWT di
 * asserzione viene firmato con RS256 usando la chiave privata del service account (java.security,
 * via Nimbus JOSE+JWT). Il token ottenuto (~1h di validità) viene cached in memoria e rinnovato
 * automaticamente un minuto prima della scadenza, senza interazione dell'utente.
 */
@Singleton
class VertexAiTokenProvider @Inject constructor(
    private val apiKeyStore: SecureApiKeyStore,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var cachedExpiryEpochSeconds: Long = 0

    private val _status = MutableStateFlow<VertexAiAuthStatus>(
        if (apiKeyStore.getServiceAccountJson() == null) VertexAiAuthStatus.Unconfigured else VertexAiAuthStatus.Valid,
    )
    val status: StateFlow<VertexAiAuthStatus> = _status.asStateFlow()

    /** ID progetto del service account configurato, usato per costruire l'URL Vertex AI. */
    fun projectId(): String {
        val raw = apiKeyStore.getServiceAccountJson()
            ?: throw VertexAiAuthException("Nessun service account configurato per Gemini. Caricalo in Impostazioni > API.")
        return parseServiceAccountJson(raw).project_id
    }

    fun invalidateCache() {
        cachedToken = null
        cachedExpiryEpochSeconds = 0
    }

    fun resetStatusToUnconfigured() {
        _status.value = VertexAiAuthStatus.Unconfigured
    }

    /** Restituisce un access token valido, rinnovandolo automaticamente se scaduto o mancante. */
    suspend fun getAccessToken(): String = mutex.withLock {
        val now = System.currentTimeMillis() / 1000
        cachedToken?.let { token ->
            if (now < cachedExpiryEpochSeconds - EXPIRY_SAFETY_MARGIN_SECONDS) return token
        }
        refreshToken(now)
    }

    private suspend fun refreshToken(now: Long): String = withContext(Dispatchers.IO) {
        val rawJson = apiKeyStore.getServiceAccountJson()
        if (rawJson == null) {
            _status.value = VertexAiAuthStatus.Unconfigured
            throw VertexAiAuthException("Nessun service account configurato per Gemini. Caricalo in Impostazioni > API.")
        }
        try {
            val creds = parseServiceAccountJson(rawJson)
            val tokenUri = creds.token_uri.ifBlank { DEFAULT_TOKEN_URI }
            val assertion = signAssertion(creds, tokenUri, now)
            val (accessToken, expiresIn) = exchangeForAccessToken(tokenUri, assertion)
            cachedToken = accessToken
            cachedExpiryEpochSeconds = now + expiresIn
            _status.value = VertexAiAuthStatus.Valid
            accessToken
        } catch (e: VertexAiAuthException) {
            _status.value = VertexAiAuthStatus.Error(e.message ?: "Errore di autenticazione Google Cloud")
            throw e
        } catch (e: Exception) {
            val message = "Errore di rete durante l'autenticazione Google Cloud: ${e.message ?: e.javaClass.simpleName}"
            _status.value = VertexAiAuthStatus.Error(message)
            throw VertexAiAuthException(message, e)
        }
    }

    private fun signAssertion(creds: ServiceAccountCredentials, tokenUri: String, now: Long): String {
        val privateKey = try {
            val pkcs8Base64 = creds.private_key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()
            val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(pkcs8Base64))
            KeyFactory.getInstance("RSA").generatePrivate(keySpec) as RSAPrivateKey
        } catch (e: Exception) {
            throw VertexAiAuthException("Chiave privata del service account non valida o non decodificabile", e)
        }

        val claims = JWTClaimsSet.Builder()
            .issuer(creds.client_email)
            .subject(creds.client_email)
            .audience(tokenUri)
            .claim("scope", CLOUD_PLATFORM_SCOPE)
            .issueTime(Date(now * 1000))
            .expirationTime(Date((now + JWT_ASSERTION_LIFETIME_SECONDS) * 1000))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build()
        val signedJwt = SignedJWT(header, claims)
        return try {
            signedJwt.sign(RSASSASigner(privateKey))
            signedJwt.serialize()
        } catch (e: Exception) {
            throw VertexAiAuthException("Impossibile firmare il token JWT con la chiave del service account", e)
        }
    }

    /** POST allo endpoint OAuth2 di Google (grant_type=jwt-bearer, RFC 7523 §2.1). Ritorna (access_token, expires_in). */
    private fun exchangeForAccessToken(tokenUri: String, assertion: String): Pair<String, Long> {
        val requestBody = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", assertion)
            .build()
        val request = Request.Builder().url(tokenUri).post(requestBody).build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = runCatching { json.decodeFromString<TokenErrorResponse>(bodyString) }.getOrNull()
                val detail = reason?.error_description ?: reason?.error ?: bodyString.ifBlank { "credenziali non valide" }
                throw VertexAiAuthException("Autenticazione Google Cloud fallita (HTTP ${response.code}): $detail")
            }
            val token = try {
                json.decodeFromString<TokenResponse>(bodyString)
            } catch (e: SerializationException) {
                throw VertexAiAuthException("Risposta del server OAuth2 non valida", e)
            }
            val accessToken = token.access_token
                ?: throw VertexAiAuthException("Risposta del server OAuth2 priva di access_token")
            return accessToken to (token.expires_in ?: JWT_ASSERTION_LIFETIME_SECONDS)
        }
    }

    private companion object {
        const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
        const val CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
        const val JWT_ASSERTION_LIFETIME_SECONDS = 3600L
        const val EXPIRY_SAFETY_MARGIN_SECONDS = 60L
    }
}

/**
 * Import, validazione e cancellazione del file service account caricato dall'utente in
 * Impostazioni > API. Separato da [VertexAiTokenProvider], che si occupa solo dei token.
 */
@Singleton
class GeminiServiceAccountManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiKeyStore: SecureApiKeyStore,
    private val tokenProvider: VertexAiTokenProvider,
) {
    val status: StateFlow<VertexAiAuthStatus> = tokenProvider.status

    val isConfigured: Boolean get() = apiKeyStore.getServiceAccountJson() != null

    fun configuredProjectId(): String? =
        apiKeyStore.getServiceAccountJson()?.let { runCatching { parseServiceAccountJson(it).project_id }.getOrNull() }

    /**
     * Legge il file scelto tramite Storage Access Framework, ne valida la struttura (lancia
     * [VertexAiAuthException] e NON salva nulla se il JSON non è un service account valido),
     * lo salva cifrato e verifica subito le credenziali contattando Google: l'esito aggiorna
     * [status], che la UI di Impostazioni osserva per mostrare l'eventuale errore.
     */
    suspend fun importFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        val rawJson = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw VertexAiAuthException("Impossibile leggere il file selezionato")
        parseServiceAccountJson(rawJson)
        apiKeyStore.setServiceAccountJson(rawJson)
        tokenProvider.invalidateCache()
        runCatching { tokenProvider.getAccessToken() }
        Unit
    }

    fun clear() {
        apiKeyStore.clearServiceAccountJson()
        tokenProvider.invalidateCache()
        tokenProvider.resetStatusToUnconfigured()
    }
}
