package com.ugocalc.app.data.remote.aws

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed class RemoteConfigResult {
    data class Success(val raw: JsonObject) : RemoteConfigResult()
    data object NetworkError : RemoteConfigResult()
    data object ParseError : RemoteConfigResult()
}

@Singleton
class RemoteConfigClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @Named("aws_base_url") private val baseUrl: String,
) {
    suspend fun fetch(): RemoteConfigResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/config")
            .header("Accept", "application/json")
            .header("User-Agent", "UgoCalc-Android")
            .build()

        val configOkHttp = okHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        try {
            configOkHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext RemoteConfigResult.NetworkError
                val body = response.body?.string().orEmpty()
                val parsed = try {
                    json.parseToJsonElement(body) as? JsonObject
                        ?: return@withContext RemoteConfigResult.ParseError
                } catch (_: Exception) {
                    return@withContext RemoteConfigResult.ParseError
                }
                RemoteConfigResult.Success(parsed)
            }
        } catch (_: IOException) {
            RemoteConfigResult.NetworkError
        }
    }
}

fun JsonObject.getString(key: String, default: String): String =
    this[key]?.jsonPrimitive?.content ?: default

fun JsonObject.getBoolean(key: String, default: Boolean): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: default

fun JsonObject.getInt(key: String, default: Int): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: default
