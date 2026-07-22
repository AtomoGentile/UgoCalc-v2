package com.ugocalc.app.data.remote.aws

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed class SyncResult {
    data object Success : SyncResult()
    data object Duplicate : SyncResult()
    data class RetryableError(val message: String) : SyncResult()
    data class FatalError(val message: String) : SyncResult()
}

@Singleton
class CloudSyncClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @Named("aws_base_url") private val baseUrl: String,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendEvent(
        endpoint: String,
        eventId: String,
        userId: String,
        payloadJson: String,
    ): SyncResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("eventId", JsonPrimitive(eventId))
            put("userId", JsonPrimitive(userId))
            put("payload", json.parseToJsonElement(payloadJson))
        }

        val request = Request.Builder()
            .url("$baseUrl/$endpoint")
            .header("Accept", "application/json")
            .header("User-Agent", "UgoCalc-Android")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val client = okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 201 -> SyncResult.Success
                    409 -> SyncResult.Duplicate
                    in 400..499 -> SyncResult.FatalError("HTTP ${response.code}")
                    else -> SyncResult.RetryableError("HTTP ${response.code}")
                }
            }
        } catch (_: IOException) {
            SyncResult.RetryableError("network error")
        }
    }
}
