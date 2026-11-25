package com.ugocalc.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ugocalc.app.data.remote.github.ApkDownloadState
import com.ugocalc.app.data.remote.github.ApkInstallLaunchResult
import com.ugocalc.app.data.remote.github.ApkUpdateDownloader
import com.ugocalc.app.data.repository.AppUpdateStatus
import java.io.File
import kotlin.math.roundToInt

/**
 * Dialog aggiornamento app: mostra changelog, scarica l'APK direttamente in-app con barra di
 * progresso live e avvia l'installer Android tramite FileProvider (con fallback al browser GitHub).
 */
@Composable
fun AppUpdateDialog(
    status: AppUpdateStatus,
    downloadState: ApkDownloadState = ApkDownloadState.Idle,
    onStartDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onLaunchInstall: (Context, File) -> ApkInstallLaunchResult = { _, _ -> ApkInstallLaunchResult.FAILED },
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var installHint by remember { mutableStateOf<String?>(null) }
    var autoLaunchedPath by remember(status.latestVersion) { mutableStateOf<String?>(null) }

    val matchingDownload = when (downloadState) {
        is ApkDownloadState.Downloading -> downloadState.takeIf { it.version == status.latestVersion }
        is ApkDownloadState.Ready -> downloadState.takeIf { it.version == status.latestVersion }
        is ApkDownloadState.Error -> downloadState.takeIf { it.version == status.latestVersion }
        ApkDownloadState.Idle -> null
    }

    LaunchedEffect(matchingDownload) {
        if (matchingDownload is ApkDownloadState.Ready && autoLaunchedPath != matchingDownload.apkFile.absolutePath) {
            autoLaunchedPath = matchingDownload.apkFile.absolutePath
            installHint = when (onLaunchInstall(context, matchingDownload.apkFile)) {
                ApkInstallLaunchResult.LAUNCHED -> null
                ApkInstallLaunchResult.PERMISSION_REQUIRED ->
                    "Abilita \"Consenti da questa origine\" nelle impostazioni appena aperte, poi tocca \"Installa APK\"."
                ApkInstallLaunchResult.FAILED ->
                    "Impossibile avviare l'installer di sistema. Usa \"Apri su GitHub\" qui sotto."
            }
        }
    }

    val isDownloading = matchingDownload is ApkDownloadState.Downloading
    val canDismiss = !status.mandatory && !isDownloading

    AlertDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (status.mandatory) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = if (status.mandatory) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (status.mandatory) "Aggiornamento richiesto" else "Aggiornamento disponibile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "Versione ${status.latestVersion}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    status.apkSizeBytes?.takeIf { it > 0L }?.let { size ->
                        Text(
                            text = ApkUpdateDownloader.formatMegabytes(size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (status.changelog.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = "Novità",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = status.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                when (matchingDownload) {
                    is ApkDownloadState.Downloading -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val progress = matchingDownload.progress
                                val pctText = progress?.let { "${(it * 100f).roundToInt()}%" } ?: "…"
                                val sizeText = if (matchingDownload.totalBytes > 0L) {
                                    "${ApkUpdateDownloader.formatMegabytes(matchingDownload.bytesRead)} / ${ApkUpdateDownloader.formatMegabytes(matchingDownload.totalBytes)}"
                                } else {
                                    ApkUpdateDownloader.formatMegabytes(matchingDownload.bytesRead)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Download APK in corso…",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = pctText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (progress != null) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(50)),
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(50)),
                                    )
                                }
                                Text(
                                    text = sizeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    is ApkDownloadState.Ready -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = installHint ?: "APK scaricato e pronto per l'installazione.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    is ApkDownloadState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = matchingDownload.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    ApkDownloadState.Idle, null -> Unit
                }

                if (status.mandatory) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = "Questa versione non è più supportata. Aggiorna l'app per continuare.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (matchingDownload) {
                is ApkDownloadState.Downloading -> {
                    TextButton(onClick = onCancelDownload) {
                        Text("Annulla download")
                    }
                }
                is ApkDownloadState.Ready -> {
                    Button(
                        onClick = {
                            installHint = when (onLaunchInstall(context, matchingDownload.apkFile)) {
                                ApkInstallLaunchResult.LAUNCHED -> null
                                ApkInstallLaunchResult.PERMISSION_REQUIRED ->
                                    "Abilita \"Consenti da questa origine\", poi tocca di nuovo \"Installa APK\"."
                                ApkInstallLaunchResult.FAILED ->
                                    "Impossibile avviare l'installer. Apri la release su GitHub."
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Installa APK")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            installHint = null
                            if (status.hasDirectApkDownload) {
                                onStartDownload()
                            } else {
                                val target = status.releasePageUrl.ifBlank { status.apkUrl }
                                if (target.isNotBlank()) {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            when {
                                matchingDownload is ApkDownloadState.Error -> "Riprova download"
                                status.hasDirectApkDownload -> "Scarica in app"
                                else -> "Apri su GitHub"
                            },
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (matchingDownload is ApkDownloadState.Error || installHint != null) {
                    TextButton(
                        onClick = {
                            val target = status.releasePageUrl.ifBlank { status.apkUrl }
                            if (target.isNotBlank()) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) }
                            }
                        },
                    ) {
                        Text("GitHub")
                    }
                }
                if (canDismiss) {
                    TextButton(onClick = onDismiss) {
                        Text("Più tardi")
                    }
                }
            }
        },
    )
}
