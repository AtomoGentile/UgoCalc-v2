package com.ugocalc.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.BuildConfig
import com.ugocalc.app.data.repository.UpdateCheckResult
import com.ugocalc.app.domain.model.Changelog
import com.ugocalc.app.domain.model.ModuleVersions
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/**
 * Impostazioni > Software e aggiornamenti: spiega come funzionano gli aggiornamenti di UgoCalc
 * (app privata, non su Play Store), distribuiti via AWS/GitHub, e mostra lo storico ([Changelog.ENTRIES]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit, viewModel: ChangelogViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Software e aggiornamenti") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
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
                        subtitle = "UgoCalc è un'app privata distribuita direttamente via AWS/GitHub. Ogni nuova versione mostra un riepilogo al primo avvio.",
                        tone = BannerTone.INFO,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = !uiState.checkingForUpdate,
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
                            is UpdateCheckResult.UpdateAvailable -> StatusBanner(
                                icon = Icons.Default.SystemUpdate,
                                title = "Nuova versione ${result.info.version}",
                                subtitle = result.info.releaseNotes.ifBlank { "È disponibile un aggiornamento dell'app." },
                                tone = BannerTone.WARNING,
                                actionLabel = "Scarica",
                                onAction = {
                                    val targetUrl = result.info.apkDownloadUrl ?: result.info.htmlUrl
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                                    }
                                },
                            )
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
