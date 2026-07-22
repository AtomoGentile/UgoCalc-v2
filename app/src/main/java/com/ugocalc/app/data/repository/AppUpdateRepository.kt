package com.ugocalc.app.data.repository

import com.ugocalc.app.BuildConfig
import com.ugocalc.app.data.remote.aws.AppVersionClient
import com.ugocalc.app.data.remote.aws.VersionCheckResult
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

@Singleton
class AppUpdateRepository @Inject constructor(
    private val versionClient: AppVersionClient,
) {
    suspend fun checkForUpdate(): AppUpdateCheckResult {
        return when (val result = versionClient.fetchVersion()) {
            is VersionCheckResult.Success -> {
                val manifest = result.manifest
                val current = BuildConfig.VERSION_NAME
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

    private fun isOlderVersion(current: String, other: String): Boolean {
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
