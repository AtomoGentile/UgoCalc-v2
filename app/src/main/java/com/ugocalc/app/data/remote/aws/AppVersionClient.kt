package com.ugocalc.app.data.remote.aws

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
data class VersionManifest(
    val latestVersion: String,
    val minRequiredVersion: String,
    val changelog: String = "",
    val apkUrl: String = "",
    val mandatory: Boolean = false,
)

sealed class VersionCheckResult {
    data class Success(val manifest: VersionManifest) : VersionCheckResult()
    data object NetworkError : VersionCheckResult()
    data object ParseError : VersionCheckResult()
}

@Singleton
class AppVersionClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @Named("aws_base_url") private val baseUrl: String,
) {
    suspend fun fetchVersion(): VersionCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/version")
            .header("Accept", "application/json")
            .header("User-Agent", "UgoCalc-Android")
            .build()

        val client = okHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext VersionCheckResult.NetworkError
                val body = response.body?.string().orEmpty()
                val manifest = try {
                    json.decodeFromString<VersionManifest>(body)
                } catch (_: Exception) {
                    return@withContext VersionCheckResult.ParseError
                }
                VersionCheckResult.Success(manifest)
            }
        } catch (_: IOException) {
            VersionCheckResult.NetworkError
        }
    }
}
