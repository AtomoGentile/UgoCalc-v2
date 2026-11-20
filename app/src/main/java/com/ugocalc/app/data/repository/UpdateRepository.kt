package com.ugocalc.app.data.repository

import com.ugocalc.app.BuildConfig
import com.ugocalc.app.data.remote.github.GitHubReleaseClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Aggiornamento trovato su GitHub, più recente di quello installato. */
data class AppUpdateInfo(
    val version: String,
    val htmlUrl: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Controllo aggiornamenti su richiesta (piano § richiesta utente "check della versione ... e
 * permettono l'aggiornamento dall'app"): confronta la versione installata
 * ([BuildConfig.VERSION_NAME]) con l'ultima release pubblicata sul repository GitHub dell'app.
 * Mai automatico in background — l'utente lo avvia da Impostazioni > Software e aggiornamenti.
 * Il download/installazione dell'APK resta delegato al browser di sistema (apre
 * [AppUpdateInfo.apkDownloadUrl] o, in mancanza, la pagina della release): evita di chiedere il
 * permesso "installa app sconosciute" e di gestire download/installazione in-app, senza perdere la
 * possibilità di aggiornare avviando tutto dall'app.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val client: GitHubReleaseClient,
) {
    suspend fun checkForUpdate(): UpdateCheckResult {
        val release = try {
            client.fetchLatestRelease(GITHUB_OWNER, GITHUB_REPO)
        } catch (e: IOException) {
            return UpdateCheckResult.Error(e.message ?: "Controllo aggiornamenti non riuscito: connessione assente")
        } ?: return UpdateCheckResult.Error("Nessuna release trovata su GitHub")

        val latestVersion = release.tagName.removePrefix("v")
        return if (isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
            UpdateCheckResult.UpdateAvailable(
                AppUpdateInfo(
                    version = latestVersion,
                    htmlUrl = release.htmlUrl,
                    releaseNotes = release.body,
                    apkDownloadUrl = release.apkDownloadUrl,
                ),
            )
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    /**
     * Confronto numerico segmento per segmento: un confronto puramente testuale metterebbe
     * "1.10.0" prima di "1.2.0" (ordine lessicografico), sbagliando proprio nel caso più comune
     * dopo la decima release minore. Segmenti mancanti da un lato contano come 0 (es. "1.3" == "1.3.0").
     */
    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = candidate.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(candidateParts.size, currentParts.size)
        for (i in 0 until length) {
            val c = candidateParts.getOrElse(i) { 0 }
            val cur = currentParts.getOrElse(i) { 0 }
            if (c != cur) return c > cur
        }
        return false
    }

    companion object {
        private const val GITHUB_OWNER = GitHubReleaseClient.DEFAULT_OWNER
        private const val GITHUB_REPO = GitHubReleaseClient.DEFAULT_REPO
    }
}
