package com.ugocalc.app.data.remote.github

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Ultima release pubblicata su GitHub: versione dichiarata, pagina della release, e link diretto all'APK se presente tra gli asset. */
data class GitHubRelease(
    val tagName: String,
    val htmlUrl: String,
    val body: String,
    val apkDownloadUrl: String?,
)

/**
 * Legge l'ultima release pubblicata su un repository GitHub tramite l'API pubblica REST
 * (§ richiesta utente "check della versione"), senza bisogno di token: il repository è pubblico e
 * il limite di 60 richieste/ora è più che sufficiente per un controllo avviato dall'utente o
 * all'avvio senza costi di server.
 */
@Singleton
class GitHubReleaseClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
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
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@use null
                val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull ?: return@use null
                val body = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val apkUrl = root["assets"]?.jsonArray.orEmpty()
                    .map { it.jsonObject }
                    .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
                    ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                GitHubRelease(tagName = tagName, htmlUrl = htmlUrl, body = body, apkDownloadUrl = apkUrl)
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
