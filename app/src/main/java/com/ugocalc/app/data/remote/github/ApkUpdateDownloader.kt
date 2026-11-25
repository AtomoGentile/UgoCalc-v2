package com.ugocalc.app.data.remote.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(
        val version: String,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : ApkDownloadState {
        val progress: Float?
            get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null
    }
    data class Ready(
        val version: String,
        val apkFile: File,
    ) : ApkDownloadState
    data class Error(
        val version: String,
        val message: String,
    ) : ApkDownloadState
}

enum class ApkInstallLaunchResult {
    LAUNCHED,
    PERMISSION_REQUIRED,
    FAILED,
}

/**
 * Scarica l'APK di aggiornamento direttamente in-app (con progresso reale, cancellazione e verifica
 * integrità ZIP/APK) e avvia l'installer di sistema tramite [FileProvider].
 */
@Singleton
class ApkUpdateDownloader @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @Named("no_auth") private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ApkDownloadState>(ApkDownloadState.Idle)
    val state: StateFlow<ApkDownloadState> = _state.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeCall: Call? = null

    fun startDownload(version: String, apkUrl: String, expectedSizeBytes: Long? = null) {
        val trimmedUrl = apkUrl.trim()
        if (trimmedUrl.isBlank()) {
            _state.value = ApkDownloadState.Error(version, "Link APK non disponibile per questa release.")
            return
        }

        cancelDownload()

        val safeVersion = version.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "latest" }
        val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "UgoCalc-$safeVersion.apk")

        // Se l'APK valido per questa versione è già stato scaricato in cache, riutilizzalo subito.
        if (targetFile.exists() && isValidApkFile(targetFile) && (expectedSizeBytes == null || expectedSizeBytes <= 0L || targetFile.length() == expectedSizeBytes)) {
            _state.value = ApkDownloadState.Ready(version = version, apkFile = targetFile)
            return
        }

        _state.value = ApkDownloadState.Downloading(
            version = version,
            bytesRead = 0L,
            totalBytes = expectedSizeBytes ?: -1L,
        )

        activeJob = scope.launch {
            val tempFile = File(updatesDir, "UgoCalc-$safeVersion.apk.tmp")
            try {
                // Pulisci eventuali vecchi APK residui in cache prima del nuovo download.
                updatesDir.listFiles()?.forEach { existing ->
                    if (existing.name != tempFile.name) {
                        runCatching { existing.delete() }
                    }
                }

                val downloadClient = okHttpClient.newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .callTimeout(10, TimeUnit.MINUTES)
                    .build()

                val request = Request.Builder()
                    .url(trimmedUrl)
                    .header("User-Agent", "UgoCalc-Android")
                    .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                    .build()

                val call = downloadClient.newCall(request)
                activeCall = call

                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        _state.value = ApkDownloadState.Error(
                            version = version,
                            message = "Download non riuscito (HTTP ${response.code}).",
                        )
                        return@use
                    }

                    val body = response.body ?: run {
                        _state.value = ApkDownloadState.Error(version, "Risposta vuota dal server di download.")
                        return@use
                    }

                    val totalBytes = body.contentLength().takeIf { it > 0L } ?: (expectedSizeBytes ?: -1L)
                    var bytesRead = 0L
                    var lastEmitMs = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                if (!isActive) throw CancellationException("Download annullato")
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesRead += read

                                val now = System.currentTimeMillis()
                                if (now - lastEmitMs >= 80L || (totalBytes > 0L && bytesRead >= totalBytes)) {
                                    lastEmitMs = now
                                    _state.value = ApkDownloadState.Downloading(
                                        version = version,
                                        bytesRead = bytesRead,
                                        totalBytes = totalBytes,
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    if (!isValidApkFile(tempFile)) {
                        runCatching { tempFile.delete() }
                        _state.value = ApkDownloadState.Error(
                            version = version,
                            message = "Il file scaricato non è un APK valido. Usa \"Apri su GitHub\".",
                        )
                        return@use
                    }

                    if (targetFile.exists()) runCatching { targetFile.delete() }
                    val moved = tempFile.renameTo(targetFile)
                    val finalFile = if (moved) targetFile else tempFile

                    _state.value = ApkDownloadState.Ready(version = version, apkFile = finalFile)
                }
            } catch (e: CancellationException) {
                runCatching { tempFile.delete() }
                if (_state.value is ApkDownloadState.Downloading) {
                    _state.value = ApkDownloadState.Idle
                }
            } catch (e: Exception) {
                runCatching { tempFile.delete() }
                _state.value = ApkDownloadState.Error(
                    version = version,
                    message = e.message?.takeIf { it.isNotBlank() }
                        ?: "Errore durante il download dell'aggiornamento.",
                )
            } finally {
                activeCall = null
            }
        }
    }

    fun cancelDownload() {
        runCatching { activeCall?.cancel() }
        activeCall = null
        activeJob?.cancel()
        activeJob = null
        if (_state.value is ApkDownloadState.Downloading) {
            _state.value = ApkDownloadState.Idle
        }
    }

    fun reset() {
        cancelDownload()
        _state.value = ApkDownloadState.Idle
    }

    /**
     * Avvia l'installer di Android sull'APK scaricato tramite [FileProvider].
     * Su Android 8+ verifica prima `canRequestPackageInstalls()` e apre le impostazioni dedicate se necessario.
     */
    fun launchInstall(context: Context, apkFile: File): ApkInstallLaunchResult {
        if (!apkFile.exists() || !isValidApkFile(apkFile)) {
            return ApkInstallLaunchResult.FAILED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (runCatching { context.startActivity(permissionIntent) }.isSuccess) {
                ApkInstallLaunchResult.PERMISSION_REQUIRED
            } else {
                ApkInstallLaunchResult.FAILED
            }
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            ApkInstallLaunchResult.LAUNCHED
        } catch (_: Exception) {
            ApkInstallLaunchResult.FAILED
        }
    }

    companion object {
        /** Verifica rapida che il file non sia vuoto e inizi con la signature ZIP/APK (`PK\x03\x04`). */
        internal fun isValidApkFile(file: File): Boolean {
            if (!file.exists() || file.length() < 1024L) return false
            return runCatching {
                file.inputStream().use { stream ->
                    val header = ByteArray(4)
                    val read = stream.read(header)
                    read == 4 &&
                        header[0] == 0x50.toByte() &&
                        header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() &&
                        header[3] == 0x04.toByte()
                }
            }.getOrDefault(false)
        }

        fun formatMegabytes(bytes: Long): String {
            if (bytes <= 0L) return "0 MB"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return "${"%.1f".format(Locale.ITALIAN, mb)} MB"
        }
    }
}
