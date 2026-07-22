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

@Singleton
class ShareClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @Named("aws_base_url") private val baseUrl: String,
) {
    private val json = "application/json; charset=utf-8".toMediaType()

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
                    val responseBody = response.body?.string() ?: "{}"
                    val accountId = runCatching {
                        JSONObject(responseBody).getString("accountId")
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
}
