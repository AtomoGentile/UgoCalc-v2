package com.ugocalc.app.data.remote.aws

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
    @Named("static_version_url") private val staticVersionUrl: String,
) {
    constructor(
        okHttpClient: OkHttpClient,
        json: Json,
        baseUrl: String,
    ) : this(okHttpClient, json, baseUrl, "")

    suspend fun fetchVersion(): VersionCheckResult = withContext(Dispatchers.IO) {
        val targetUrl = resolveEndpointUrl(baseUrl, "version", staticVersionUrl)
            ?: return@withContext VersionCheckResult.NetworkError

        val request = Request.Builder()
            .url(targetUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "UgoCalc-Android")
            .build()

        val client = okHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            VersionCheckResult.NetworkError
        }
    }
}
