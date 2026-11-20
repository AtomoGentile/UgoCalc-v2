package com.ugocalc.app.data.remote.aws

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed class ShareResult {
    data class Success(val accountId: String) : ShareResult()
    data class Error(val message: String) : ShareResult()
}

sealed class GenerateInviteResult {
    data class Success(val token: String, val deepLink: String) : GenerateInviteResult()
    data class Error(val message: String) : GenerateInviteResult()
}

sealed class RequestAccessResult {
    data object WaitingApproval : RequestAccessResult()
    data class Error(val message: String) : RequestAccessResult()
}

enum class InviteStatus { PENDING, WAITING_APPROVAL, APPROVED, DENIED, UNKNOWN }

sealed class InviteStatusResult {
    data class Success(val status: InviteStatus) : InviteStatusResult()
    data class Error(val message: String) : InviteStatusResult()
}

sealed class ApproveResult {
    data object Ok : ApproveResult()
    data class Error(val message: String) : ApproveResult()
}

@Singleton
class ShareClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @Named("aws_base_url") private val baseUrl: String,
) {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val shareClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ─── legacy: invito diretto per email ───────────────────────────────────
    suspend fun invite(inviteeEmail: String): ShareResult = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeAwsBaseUrl(baseUrl)
            ?: return@withContext ShareResult.Error(UNAVAILABLE_MESSAGE)

        val body = JSONObject().apply { put("inviteeEmail", inviteeEmail) }
            .toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$normalizedBase/share")
            .header("Accept", "application/json")
            .post(body)
            .build()
        try {
            shareClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val accountId = runCatching {
                        JSONObject(response.body?.string() ?: "{}").getString("accountId")
                    }.getOrDefault("")
                    ShareResult.Success(accountId)
                } else {
                    val msg = runCatching {
                        JSONObject(response.body?.string() ?: "{}").getString("error")
                    }.getOrDefault("HTTP ${response.code}")
                    ShareResult.Error(msg)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ShareResult.Error("Errore di rete: ${e.message}")
        } catch (e: Exception) {
            ShareResult.Error("Errore: ${e.message}")
        }
    }

    // ─── Owner: genera un token + deep link ─────────────────────────────────
    suspend fun generateInvite(): GenerateInviteResult = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeAwsBaseUrl(baseUrl)
            ?: return@withContext GenerateInviteResult.Error(UNAVAILABLE_MESSAGE)

        val request = Request.Builder()
            .url("$normalizedBase/share/generate")
            .header("Accept", "application/json")
            .post("{}".toRequestBody(json))
            .build()
        try {
            shareClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    val res = runCatching {
                        val obj = JSONObject(bodyStr)
                        GenerateInviteResult.Success(
                            token = obj.getString("token"),
                            deepLink = obj.getString("deepLink"),
                        )
                    }.getOrElse { GenerateInviteResult.Error("Risposta non valida: $bodyStr") }
                    res
                } else {
                    val msg = runCatching { JSONObject(bodyStr).getString("error") }
                        .getOrDefault("HTTP ${response.code}")
                    GenerateInviteResult.Error(msg)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GenerateInviteResult.Error("Errore: ${e.message}")
        }
    }

    // ─── Guest: attiva il token e mette la richiesta in coda ────────────────
    suspend fun requestAccess(token: String): RequestAccessResult = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeAwsBaseUrl(baseUrl)
            ?: return@withContext RequestAccessResult.Error(UNAVAILABLE_MESSAGE)

        val body = JSONObject().apply { put("token", token) }.toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$normalizedBase/share/request")
            .header("Accept", "application/json")
            .post(body)
            .build()
        try {
            shareClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    RequestAccessResult.WaitingApproval
                } else {
                    val msg = runCatching { JSONObject(bodyStr).getString("error") }
                        .getOrDefault("HTTP ${response.code}")
                    RequestAccessResult.Error(msg)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RequestAccessResult.Error("Errore: ${e.message}")
        }
    }

    // ─── Guest: polling dello stato ──────────────────────────────────────────
    suspend fun pollStatus(token: String): InviteStatusResult = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeAwsBaseUrl(baseUrl)
            ?: return@withContext InviteStatusResult.Error(UNAVAILABLE_MESSAGE)

        val request = Request.Builder()
            .url("$normalizedBase/share/status?token=$token")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            shareClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    val status = runCatching {
                        val statusStr = JSONObject(bodyStr).getString("status")
                        when (statusStr) {
                            "pending"          -> InviteStatus.PENDING
                            "waiting_approval" -> InviteStatus.WAITING_APPROVAL
                            "approved"         -> InviteStatus.APPROVED
                            "denied"           -> InviteStatus.DENIED
                            else               -> InviteStatus.UNKNOWN
                        }
                    }.getOrDefault(InviteStatus.UNKNOWN)
                    InviteStatusResult.Success(status)
                } else {
                    val msg = runCatching { JSONObject(bodyStr).getString("error") }
                        .getOrDefault("HTTP ${response.code}")
                    InviteStatusResult.Error(msg)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            InviteStatusResult.Error("Errore: ${e.message}")
        }
    }

    // ─── Owner: approva o nega ───────────────────────────────────────────────
    suspend fun approve(token: String, approved: Boolean): ApproveResult = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeAwsBaseUrl(baseUrl)
            ?: return@withContext ApproveResult.Error(UNAVAILABLE_MESSAGE)

        val body = JSONObject().apply {
            put("token", token)
            put("approved", approved)
        }.toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$normalizedBase/share/approve")
            .header("Accept", "application/json")
            .post(body)
            .build()
        try {
            shareClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    ApproveResult.Ok
                } else {
                    val msg = runCatching { JSONObject(bodyStr).getString("error") }
                        .getOrDefault("HTTP ${response.code}")
                    ApproveResult.Error(msg)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApproveResult.Error("Errore: ${e.message}")
        }
    }

    companion object {
        private const val UNAVAILABLE_MESSAGE = "Condivisione cloud non disponibile: backend AWS non configurato"
    }
}
