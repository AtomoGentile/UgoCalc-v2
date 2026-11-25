package com.ugocalc.app.data.remote.github

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Ultima release pubblicata su GitHub: versione dichiarata, pagina della release e metadati dell'asset APK. */
data class GitHubRelease(
    val tagName: String,
    val htmlUrl: String,
    val body: String,
    val apkDownloadUrl: String?,
    val apkFileName: String? = null,
    val apkSizeBytes: Long? = null,
)

/**
 * Legge l'ultima release pubblicata su un repository GitHub tramite l'API pubblica REST
 * senza bisogno di token: il repository è pubblico e supporta il download diretto dell'APK in-app.
 */
@Singleton
class GitHubReleaseClient @Inject constructor(
    @Named("no_auth") private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun fetchLatestRelease(
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO,
    ): GitHubRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "UgoCalc-Android")
            .build()

        val client = okHttpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    when (response.code) {
                        403, 429 -> throw IOException("Limite richieste GitHub raggiunto (HTTP ${response.code}). Riprova tra qualche minuto.")
                        404 -> return@use null
                        else -> throw IOException("Errore GitHub durante il controllo aggiornamenti (HTTP ${response.code}).")
                    }
                }
                val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@use null
                val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull ?: return@use null
                val body = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val apkAsset = root["assets"]?.jsonArray.orEmpty()
                    .map { it.jsonObject }
                    .firstOrNull {
                        it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true
                    }
                val apkUrl = apkAsset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                val apkFileName = apkAsset?.get("name")?.jsonPrimitive?.contentOrNull
                val apkSizeBytes = apkAsset?.get("size")?.jsonPrimitive?.longOrNull
                GitHubRelease(
                    tagName = tagName,
                    htmlUrl = htmlUrl,
                    body = body,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkFileName,
                    apkSizeBytes = apkSizeBytes,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_OWNER = "AtomoGentile"
        const val DEFAULT_REPO = "UgoCalc-v2"
    }
}
