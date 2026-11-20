package com.ugocalc.app.data.remote.aws

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

interface CloudSyncClient {
    suspend fun fetchInferenceRecords(): List<String>
    suspend fun fetchCalibrationRecords(): List<String>
    suspend fun sendEvent(
        endpoint: String,
        eventId: String,
        userId: String,
        payloadJson: String,
    ): SyncResult
}

@Singleton
class RealCloudSyncClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @Named("aws_base_url") private val baseUrl: String,
) : CloudSyncClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Scarica i record INFERENCE dell'utente dal backend (l'identità viene dal token Cognito nell'AuthInterceptor). Restituisce i payload JSON grezzi oppure lista vuota in caso di errore. */
    override suspend fun fetchInferenceRecords(): List<String> = fetchRecords("inference")

    override suspend fun fetchCalibrationRecords(): List<String> = fetchRecords("calibration")

    private suspend fun fetchRecords(path: String): List<String> = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeAwsBaseUrl(baseUrl) ?: return@withContext emptyList()

        val request = Request.Builder()
            .url("$normalizedBase/$path")
            .header("Accept", "application/json")
            .header("User-Agent", "UgoCalc-Android")
            .get()
            .build()

        val client = okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val records = root["records"]?.jsonArray ?: return@withContext emptyList()
                records.map { it.toString() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun sendEvent(
        endpoint: String,
        eventId: String,
        userId: String,
        payloadJson: String,
    ): SyncResult = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeAwsBaseUrl(baseUrl)
            ?: return@withContext SyncResult.FatalError("AWS backend disabled")

        val parsedPayload = try {
            json.parseToJsonElement(payloadJson)
        } catch (_: Exception) {
            return@withContext SyncResult.FatalError("Invalid payload JSON")
        }

        val body = buildJsonObject {
            put("eventId", JsonPrimitive(eventId))
            put("userId", JsonPrimitive(userId))
            put("payload", parsedPayload)
        }

        val request = Request.Builder()
            .url("$normalizedBase/$endpoint")
            .header("Accept", "application/json")
            .header("User-Agent", "UgoCalc-Android")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val client = okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            SyncResult.RetryableError("network error")
        } catch (e: Exception) {
            SyncResult.RetryableError(e.message ?: "unexpected error")
        }
    }
}
