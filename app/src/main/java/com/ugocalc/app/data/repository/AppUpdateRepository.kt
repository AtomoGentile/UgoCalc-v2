package com.ugocalc.app.data.repository

import com.ugocalc.app.BuildConfig
import com.ugocalc.app.data.remote.aws.AppVersionClient
import com.ugocalc.app.data.remote.aws.VersionCheckResult
import com.ugocalc.app.data.remote.github.GitHubReleaseClient
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateStatus(
    val latestVersion: String,
    val changelog: String,
    val apkUrl: String,
    val mandatory: Boolean,
)

sealed interface AppUpdateCheckResult {
    data class UpdateAvailable(val status: AppUpdateStatus) : AppUpdateCheckResult
    data object UpToDate : AppUpdateCheckResult
    data object Unavailable : AppUpdateCheckResult
}

/**
 * Controllo aggiornamenti all'avvio (100% indipendente da AWS):
 * 1. Usa **GitHub Releases** (`GitHubReleaseClient`) come fonte primaria gratuita e autonoma.
 * 2. Se GitHub Releases non è raggiungibile o non ha release, ripiega silenziosamente su
 *    `AppVersionClient` (es. `STATIC_VERSION_URL` su GitHub Raw o Cloudflare Worker se configurati).
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    private val versionClient: AppVersionClient,
    private val gitHubReleaseClient: GitHubReleaseClient,
) {
    suspend fun checkForUpdate(): AppUpdateCheckResult {
        val current = BuildConfig.VERSION_NAME

        // 1. Fonte primaria autonoma: GitHub Releases (0 € / nessun backend richiesto)
        val release = try {
            gitHubReleaseClient.fetchLatestRelease()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        if (release != null) {
            val latestVersion = release.tagName.removePrefix("v").trim()
            if (latestVersion.isNotBlank()) {
                return if (isOlderVersion(current, latestVersion)) {
                    AppUpdateCheckResult.UpdateAvailable(
                        AppUpdateStatus(
                            latestVersion = latestVersion,
                            changelog = release.body,
                            apkUrl = release.apkDownloadUrl ?: release.htmlUrl,
                            mandatory = false,
                        ),
                    )
                } else {
                    AppUpdateCheckResult.UpToDate
                }
            }
        }

        // 2. Fallback opzionale: manifest JSON statico (GitHub Raw) o backend configurato
        return when (val result = versionClient.fetchVersion()) {
            is VersionCheckResult.Success -> {
                val manifest = result.manifest
                val isMandatoryOutdated = isOlderVersion(current, manifest.minRequiredVersion)
                val isNewerAvailable = isOlderVersion(current, manifest.latestVersion)
                when {
                    isMandatoryOutdated || (isNewerAvailable && manifest.mandatory) ->
                        AppUpdateCheckResult.UpdateAvailable(
                            AppUpdateStatus(
                                latestVersion = manifest.latestVersion,
                                changelog = manifest.changelog,
                                apkUrl = manifest.apkUrl,
                                mandatory = true,
                            ),
                        )
                    isNewerAvailable ->
                        AppUpdateCheckResult.UpdateAvailable(
                            AppUpdateStatus(
                                latestVersion = manifest.latestVersion,
                                changelog = manifest.changelog,
                                apkUrl = manifest.apkUrl,
                                mandatory = false,
                            ),
                        )
                    else -> AppUpdateCheckResult.UpToDate
                }
            }
            is VersionCheckResult.NetworkError, is VersionCheckResult.ParseError -> AppUpdateCheckResult.Unavailable
        }
    }

    internal fun isOlderVersion(current: String, other: String): Boolean {
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        val o = other.split(".").mapNotNull { it.toIntOrNull() }
        val len = maxOf(c.size, o.size)
        for (i in 0 until len) {
            val ci = c.getOrElse(i) { 0 }
            val oi = o.getOrElse(i) { 0 }
            if (ci != oi) return ci < oi
        }
        return false
    }
}
