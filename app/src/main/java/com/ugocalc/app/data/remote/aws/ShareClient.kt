package com.ugocalc.app.data.remote.aws

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
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

    // ─── legacy: invito diretto per email ───────────────────────────────────
    suspend fun invite(inviteeEmail: String): ShareResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("inviteeEmail", inviteeEmail) }
            .toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$baseUrl/share")
            .header("Accept", "application/json")
            .post(body)
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
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
        } catch (e: IOException) {
            ShareResult.Error("Errore di rete: ${e.message}")
        }
    }

    // ─── Owner: genera un token + deep link ─────────────────────────────────
    suspend fun generateInvite(): GenerateInviteResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/share/generate")
            .header("Accept", "application/json")
            .post("{}".toRequestBody(json))
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val obj = JSONObject(response.body?.string() ?: "{}")
                    GenerateInviteResult.Success(
                        token = obj.getString("token"),
                        deepLink = obj.getString("deepLink"),
                    )
                } else {
                    val msg = runCatching {
                        JSONObject(response.body?.string() ?: "{}").getString("error")
                    }.getOrDefault("HTTP ${response.code}")
                    GenerateInviteResult.Error(msg)
                }
            }
        } catch (e: IOException) {
            GenerateInviteResult.Error("Errore di rete: ${e.message}")
        }
    }

    // ─── Guest: attiva il token e mette la richiesta in coda ────────────────
    suspend fun requestAccess(token: String): RequestAccessResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("token", token) }.toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$baseUrl/share/request")
            .header("Accept", "application/json")
            .post(body)
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    RequestAccessResult.WaitingApproval
                } else {
                    val msg = runCatching {
                        JSONObject(response.body?.string() ?: "{}").getString("error")
                    }.getOrDefault("HTTP ${response.code}")
                    RequestAccessResult.Error(msg)
                }
            }
        } catch (e: IOException) {
            RequestAccessResult.Error("Errore di rete: ${e.message}")
        }
    }

    // ─── Guest: polling dello stato ──────────────────────────────────────────
    suspend fun pollStatus(token: String): InviteStatusResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/share/status?token=$token")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val statusStr = runCatching {
                        JSONObject(response.body?.string() ?: "{}").getString("status")
                    }.getOrDefault("unknown")
                    val status = when (statusStr) {
                        "pending"          -> InviteStatus.PENDING
                        "waiting_approval" -> InviteStatus.WAITING_APPROVAL
                        "approved"         -> InviteStatus.APPROVED
                        "denied"           -> InviteStatus.DENIED
                        else               -> InviteStatus.UNKNOWN
                    }
                    InviteStatusResult.Success(status)
                } else {
                    val msg = runCatching {
                        JSONObject(response.body?.string() ?: "{}").getString("error")
                    }.getOrDefault("HTTP ${response.code}")
                    InviteStatusResult.Error(msg)
                }
            }
        } catch (e: IOException) {
            InviteStatusResult.Error("Errore di rete: ${e.message}")
        }
    }

    // ─── Owner: approva o nega ───────────────────────────────────────────────
    suspend fun approve(token: String, approved: Boolean): ApproveResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("token", token)
            put("approved", approved)
        }.toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$baseUrl/share/approve")
            .header("Accept", "application/json")
            .post(body)
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ApproveResult.Ok
                } else {
                    val msg = runCatching {
                        JSONObject(response.body?.string() ?: "{}").getString("error")
                    }.getOrDefault("HTTP ${response.code}")
                    ApproveResult.Error(msg)
                }
            }
        } catch (e: IOException) {
            ApproveResult.Error("Errore di rete: ${e.message}")
        }
    }
}
