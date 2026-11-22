package com.ugocalc.app.data.auth

import android.util.Log
import com.ugocalc.app.data.remote.aws.normalizeAwsBaseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class AuthUser(
    val userId: String,
    val email: String,
    val accessToken: String,
)

sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Client degli account UgoCalc sul Worker Cloudflare (`/auth/...`).
 *
 * Usa `HttpURLConnection` (non OkHttp) di proposito: l'`AuthInterceptor` di OkHttp dipende
 * da `AuthRepository`, che dipende da questo client — così si evitano cicli e ricorsioni.
 */
@Singleton
class CloudflareAuthClient @Inject constructor(
    @Named("aws_base_url") private val baseUrl: String,
) {
    private companion object {
        const val TAG = "CloudflareAuthClient"
        const val TIMEOUT_MS = 15_000
        const val UNAVAILABLE = "Servizio account non disponibile. L'app resta utilizzabile in locale."
    }

    private class Reply(val code: Int, val json: JSONObject?)

    suspend fun register(email: String, password: String): AuthResult =
        sessionCall("/auth/register", "POST", null, JSONObject().put("email", email).put("password", password))

    suspend fun login(email: String, password: String): AuthResult =
        sessionCall("/auth/login", "POST", null, JSONObject().put("email", email).put("password", password))

    suspend fun refresh(token: String): AuthResult = sessionCall("/auth/refresh", "POST", token, JSONObject())

    suspend fun changePassword(token: String, oldPassword: String, newPassword: String): AuthResult =
        simpleCall(
            "/auth/password", token,
            JSONObject().put("oldPassword", oldPassword).put("newPassword", newPassword),
        )

    suspend fun changeEmail(token: String, password: String, newEmail: String): AuthResult =
        simpleCall("/auth/email", token, JSONObject().put("password", password).put("newEmail", newEmail))

    suspend fun deleteAccount(token: String, password: String): AuthResult =
        simpleCall("/auth/delete", token, JSONObject().put("password", password))

    private suspend fun sessionCall(path: String, method: String, token: String?, body: JSONObject): AuthResult {
        val reply = execute(path, method, token, body) ?: return AuthResult.Error(UNAVAILABLE)
        val json = reply.json
        if (reply.code in 200..299 && json != null) {
            val jwt = json.optString("token")
            val userId = json.optString("userId")
            if (jwt.isNotEmpty() && userId.isNotEmpty()) {
                return AuthResult.Success(AuthUser(userId, json.optString("email"), jwt))
            }
        }
        return AuthResult.Error(errorMessage(reply))
    }

    private suspend fun simpleCall(path: String, token: String, body: JSONObject): AuthResult {
        val reply = execute(path, "POST", token, body) ?: return AuthResult.Error(UNAVAILABLE)
        return if (reply.code in 200..299) {
            AuthResult.Success(AuthUser("", reply.json?.optString("email").orEmpty(), token))
        } else {
            AuthResult.Error(errorMessage(reply))
        }
    }

    private fun errorMessage(reply: Reply): String =
        reply.json?.optString("error")?.takeIf { it.isNotBlank() } ?: when (reply.code) {
            401 -> "Credenziali non valide"
            429 -> "Troppi tentativi. Riprova tra qualche minuto."
            503 -> UNAVAILABLE
            else -> "Operazione non riuscita (${reply.code})"
        }

    private suspend fun execute(path: String, method: String, token: String?, body: JSONObject): Reply? {
        val root = normalizeAwsBaseUrl(baseUrl) ?: return null
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(root.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (token != null) setRequestProperty("Authorization", "Bearer $token")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Reply(code, runCatching { JSONObject(text) }.getOrNull())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$path failed: ${e.message}")
                null
            } finally {
                conn?.disconnect()
            }
        }
    }
}
