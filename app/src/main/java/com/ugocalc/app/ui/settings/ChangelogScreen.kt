package com.ugocalc.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.BuildConfig
import com.ugocalc.app.data.remote.github.ApkDownloadState
import com.ugocalc.app.data.remote.github.ApkInstallLaunchResult
import com.ugocalc.app.data.remote.github.ApkUpdateDownloader
import com.ugocalc.app.data.repository.UpdateCheckResult
import com.ugocalc.app.domain.model.Changelog
import com.ugocalc.app.domain.model.ModuleVersions
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EditorialDetailScaffold
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import kotlin.math.roundToInt

/**
 * Impostazioni > Software e aggiornamenti: controlla l'ultima release su GitHub, scarica
 * l'APK direttamente in-app con progresso live, avvia l'installer di sistema e mostra lo storico.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit, viewModel: ChangelogViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val apkDownloadState by viewModel.apkDownloadState.collectAsState()
    val context = LocalContext.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var installHint by remember { mutableStateOf<String?>(null) }
    var autoLaunchedPath by remember { mutableStateOf<String?>(null) }

    val availableInfo = (uiState.updateResult as? UpdateCheckResult.UpdateAvailable)?.info
    val matchingDownload = if (availableInfo != null) {
        when (val st = apkDownloadState) {
            is ApkDownloadState.Downloading -> st.takeIf { it.version == availableInfo.version }
            is ApkDownloadState.Ready -> st.takeIf { it.version == availableInfo.version }
            is ApkDownloadState.Error -> st.takeIf { it.version == availableInfo.version }
            ApkDownloadState.Idle -> null
        }
    } else null

    LaunchedEffect(matchingDownload) {
        if (matchingDownload is ApkDownloadState.Ready && autoLaunchedPath != matchingDownload.apkFile.absolutePath) {
            autoLaunchedPath = matchingDownload.apkFile.absolutePath
            installHint = when (viewModel.launchApkInstall(context, matchingDownload.apkFile)) {
                ApkInstallLaunchResult.LAUNCHED -> null
                ApkInstallLaunchResult.PERMISSION_REQUIRED ->
                    "Abilita \"Consenti da questa origine\" nelle impostazioni appena aperte, poi tocca \"Installa APK\"."
                ApkInstallLaunchResult.FAILED ->
                    "Impossibile avviare l'installer di sistema. Usa \"Apri su GitHub\"."
            }
        }
    }

    EditorialDetailScaffold(
        title = "Software e aggiornamenti",
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = navBarBottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header_info") {
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    StatusBanner(
                        icon = Icons.Default.SystemUpdate,
                        title = "Versione installata: ${BuildConfig.VERSION_NAME}",
                        subtitle = "Gli aggiornamenti vengono verificati su GitHub Releases e scaricati direttamente in app con installazione guidata.",
                        tone = BannerTone.INFO,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            installHint = null
                            viewModel.checkForUpdate()
                        },
                        enabled = !uiState.checkingForUpdate && matchingDownload !is ApkDownloadState.Downloading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.checkingForUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Controllo in corso…")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Controlla aggiornamenti")
                        }
                    }

                    uiState.updateResult?.let { result ->
                        Spacer(modifier = Modifier.height(10.dp))
                        when (result) {
                            is UpdateCheckResult.UpToDate -> StatusBanner(
                                icon = Icons.Default.CheckCircle,
                                title = "App aggiornata",
                                subtitle = "Stai già usando l'ultima versione disponibile.",
                                tone = BannerTone.SUCCESS,
                            )
                            is UpdateCheckResult.UpdateAvailable -> {
                                val info = result.info
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = "Nuova versione ${info.version}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            info.apkSizeBytes?.takeIf { it > 0L }?.let { bytes ->
                                                Text(
                                                    text = ApkUpdateDownloader.formatMegabytes(bytes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }

                                        if (info.releaseNotes.isNotBlank()) {
                                            Text(
                                                text = info.releaseNotes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        when (matchingDownload) {
                                            is ApkDownloadState.Downloading -> {
                                                val progress = matchingDownload.progress
                                                val pct = progress?.let { "${(it * 100f).roundToInt()}%" } ?: "…"
                                                val transferred = if (matchingDownload.totalBytes > 0L) {
                                                    "${ApkUpdateDownloader.formatMegabytes(matchingDownload.bytesRead)} / ${ApkUpdateDownloader.formatMegabytes(matchingDownload.totalBytes)}"
                                                } else {
                                                    ApkUpdateDownloader.formatMegabytes(matchingDownload.bytesRead)
                                                }
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Text(
                                                            text = "Download in corso · $transferred",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Text(
                                                            text = pct,
                                                            style = MaterialTheme.typography.labelSmall,
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
                                                    OutlinedButton(
                                                        onClick = { viewModel.cancelApkDownload() },
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Text("Annulla download")
                                                    }
                                                }
                                            }
                                            is ApkDownloadState.Ready -> {
                                                if (installHint != null) {
                                                    StatusBanner(
                                                        icon = Icons.Default.SystemUpdate,
                                                        title = "Permesso installazione",
                                                        subtitle = installHint.orEmpty(),
                                                        tone = BannerTone.WARNING,
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        installHint = when (viewModel.launchApkInstall(context, matchingDownload.apkFile)) {
                                                            ApkInstallLaunchResult.LAUNCHED -> null
                                                            ApkInstallLaunchResult.PERMISSION_REQUIRED ->
                                                                "Abilita \"Consenti da questa origine\", poi tocca \"Installa APK\"."
                                                            ApkInstallLaunchResult.FAILED ->
                                                                "Impossibile avviare l'installer. Usa \"Apri su GitHub\"."
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Installa APK (${info.version})")
                                                }
                                            }
                                            is ApkDownloadState.Error -> {
                                                StatusBanner(
                                                    icon = Icons.Default.ErrorOutline,
                                                    title = "Download non riuscito",
                                                    subtitle = matchingDownload.message,
                                                    tone = BannerTone.ERROR,
                                                )
                                                if (info.apkDownloadUrl != null) {
                                                    Button(
                                                        onClick = {
                                                            installHint = null
                                                            viewModel.startApkDownload(info)
                                                        },
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Riprova download in app")
                                                    }
                                                }
                                            }
                                            ApkDownloadState.Idle, null -> {
                                                if (info.apkDownloadUrl != null) {
                                                    Button(
                                                        onClick = {
                                                            installHint = null
                                                            viewModel.startApkDownload(info)
                                                        },
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Scarica e installa in app")
                                                    }
                                                }
                                            }
                                        }

                                        TextButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl)))
                                                }
                                            },
                                            modifier = Modifier.align(Alignment.End),
                                        ) {
                                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Apri su GitHub")
                                        }
                                    }
                                }
                            }
                            is UpdateCheckResult.Error -> StatusBanner(
                                icon = Icons.Default.ErrorOutline,
                                title = "Controllo non riuscito",
                                subtitle = result.message,
                                tone = BannerTone.ERROR,
                            )
                        }
                    }
                }
            }

            item(key = "changelog_section_header") {
                UgoSectionHeader(title = "Storico versioni")
            }

            items(
                items = Changelog.ENTRIES,
                key = { it.version },
                contentType = { "changelog_entry" },
            ) { entry ->
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "Versione ${entry.version}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (entry.categories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            entry.categories.forEach { category ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    entry.highlights.forEach { highlight ->
                        Text(
                            text = "• $highlight",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    entry.moduleVersions?.let { versions ->
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        ModuleVersionsBlock(versions)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleVersionsBlock(versions: ModuleVersions) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Versioni modulo",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        versions.inferenceEngine?.let { Text("InferenceEngine: $it", style = MaterialTheme.typography.bodySmall) }
        versions.perceptionPrompt?.let { Text("PerceptionPrompt: $it", style = MaterialTheme.typography.bodySmall) }
        versions.physicalData?.let { Text("PhysicalData: $it", style = MaterialTheme.typography.bodySmall) }
        versions.biasCalculator?.let { Text("BiasCalculator: $it", style = MaterialTheme.typography.bodySmall) }
        versions.ambiguityResolver?.let { Text("AmbiguityResolver: $it", style = MaterialTheme.typography.bodySmall) }
        versions.matchStep?.let { Text("MatchStep: $it", style = MaterialTheme.typography.bodySmall) }
    }
}
