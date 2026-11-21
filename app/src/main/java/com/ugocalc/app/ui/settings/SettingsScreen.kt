package com.ugocalc.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.ugocalc.app.BuildConfig
import com.ugocalc.app.R
import com.ugocalc.app.data.local.prefs.AppTheme
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors

private data class SettingsMenuItemData(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val badgeText: String? = null,
    val badgePositive: Boolean = true,
    val onClick: () -> Unit,
)

private data class SettingsSectionData(
    val title: String,
    val items: List<SettingsMenuItemData>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenApi: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenPreferredModel: () -> Unit,
    onOpenNormalizationModel: () -> Unit,
    onOpenShadowModel: () -> Unit,
    onOpenConfidence: () -> Unit,
    onOpenTables: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenHistoryChart: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenScaleCalibrations: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenAccount: () -> Unit = {},
    onOpenCostManagement: () -> Unit = {},
    onOpenBiasEvolution: () -> Unit = {},
    onOpenGlycemicProfile: () -> Unit = {},
    showBackButton: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val configuredProviders by viewModel.configuredProviders.collectAsState()
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAvailableModels()
    }

    val configuredCount = configuredProviders.values.count { it }
    val settings = state.userSettings
    val activeThemeLabel = when (settings.theme) {
        AppTheme.LIGHT -> "Chiaro"
        AppTheme.DARK -> "Scuro"
        AppTheme.SYSTEM -> "Sistema"
    }

    val mainSections = listOf(
        SettingsSectionData(
            title = "Account & API",
            items = listOf(
                SettingsMenuItemData(
                    id = "account",
                    icon = Icons.Outlined.Person,
                    title = "Account",
                    subtitle = "Accesso, condivisione e bias globale",
                    onClick = onOpenAccount,
                ),
                SettingsMenuItemData(
                    id = "api",
                    icon = Icons.Outlined.Key,
                    title = "API e Provider AI",
                    subtitle = if (configuredCount == 0) {
                        "Configura Gemini, OpenAI, Anthropic o AWS"
                    } else {
                        "${availableModels.size} modelli disponibili"
                    },
                    badgeText = if (configuredCount == 0) "Richiesto" else "$configuredCount attivi",
                    badgePositive = configuredCount > 0,
                    onClick = onOpenApi,
                ),
            ),
        ),
        SettingsSectionData(
            title = "Preferenze",
            items = listOf(
                SettingsMenuItemData(
                    id = "glycemic_profile",
                    icon = Icons.Outlined.ShowChart,
                    title = "Profilo Glicemico",
                    subtitle = "${settings.toGlycemicProfile().preset.label} · Basale ${settings.baselineGlucoseMgDl} mg/dL",
                    badgeText = "≤${settings.targetPostPrandialMaxMgDl}",
                    badgePositive = true,
                    onClick = onOpenGlycemicProfile,
                ),
                SettingsMenuItemData(
                    id = "favorites",
                    icon = Icons.Outlined.StarOutline,
                    title = "Preferiti",
                    subtitle = "Scorciatoie mostrate in Homepage",
                    badgeText = if (settings.favoritesEnabled) "Attivi" else "Disattivi",
                    badgePositive = settings.favoritesEnabled,
                    onClick = onOpenFavorites,
                ),
                SettingsMenuItemData(
                    id = "permissions",
                    icon = Icons.Outlined.Security,
                    title = "Permessi",
                    subtitle = "Fotocamera e notifiche in background",
                    onClick = onOpenPermissions,
                ),
                SettingsMenuItemData(
                    id = "accessibility",
                    icon = Icons.Outlined.Palette,
                    title = "Accessibilità e tema",
                    subtitle = "Tema $activeThemeLabel · Testo ${(settings.textScale * 100).toInt()}%",
                    onClick = onOpenAccessibility,
                ),
            ),
        ),
        SettingsSectionData(
            title = "Modelli & Analisi",
            items = listOf(
                SettingsMenuItemData(
                    id = "preferred_models",
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Modelli preferiti",
                    subtitle = if (settings.preferredModelIds.isEmpty()) {
                        "Scelta manuale ad ogni analisi"
                    } else {
                        "Avvio rapido con ${settings.preferredModelIds.size} modelli"
                    },
                    badgeText = if (settings.preferredModelIds.isNotEmpty()) "${settings.preferredModelIds.size}" else null,
                    onClick = onOpenPreferredModel,
                ),
                SettingsMenuItemData(
                    id = "normalization_models",
                    icon = Icons.Outlined.Tune,
                    title = "Modelli per normalizzazione",
                    subtitle = if (settings.enabledNormalizationModelIds.isEmpty()) {
                        "Tutti i modelli attivi"
                    } else {
                        "${settings.enabledNormalizationModelIds.size} modelli selezionati"
                    },
                    onClick = onOpenNormalizationModel,
                ),
                SettingsMenuItemData(
                    id = "confidence",
                    icon = Icons.Outlined.Verified,
                    title = "Livello di confidenza",
                    subtitle = if (settings.confidenceIndicatorEnabled) {
                        "Avviso attivo sotto ${settings.confidenceAlertThresholdPercent}%"
                    } else {
                        "Avviso bassa confidenza disattivato"
                    },
                    badgeText = if (settings.confidenceIndicatorEnabled) "${settings.confidenceAlertThresholdPercent}%" else null,
                    onClick = onOpenConfidence,
                ),
                SettingsMenuItemData(
                    id = "history_chart",
                    icon = Icons.Outlined.BarChart,
                    title = "Grafico cronologia",
                    subtitle = if (settings.historyChartEnabled) "Visibile in Cronologia" else "Nascosto",
                    onClick = onOpenHistoryChart,
                ),
            ),
        ),
        SettingsSectionData(
            title = "Strumenti & Database",
            items = listOf(
                SettingsMenuItemData(
                    id = "tables",
                    icon = Icons.Outlined.TableChart,
                    title = "Tabelle valori ufficiali",
                    subtitle = "Carica, ordina e modifica tabelle CHO/grassi",
                    badgeText = "${state.officialTableCount}",
                    badgePositive = state.officialTableCount > 0,
                    onClick = onOpenTables,
                ),
                SettingsMenuItemData(
                    id = "recipes",
                    icon = Icons.Outlined.MenuBook,
                    title = "Ricette",
                    subtitle = "Gestisci le ricette per la strategia Composto",
                    onClick = onOpenRecipes,
                ),
            ),
        ),
    )

    val advancedItems = listOf(
        SettingsMenuItemData(
            id = "bias_evolution",
            icon = Icons.Outlined.Psychology,
            title = "BIAS & Evoluzione",
            subtitle = "Motore V2, peso vero e fattori appresi",
            badgeText = if (settings.v2VisibleModeEnabled) "V2" else if (settings.v2ShadowModeEnabled) "Shadow" else null,
            onClick = onOpenBiasEvolution,
        ),
        SettingsMenuItemData(
            id = "shadow_models",
            icon = Icons.Outlined.Science,
            title = "Modelli V2 shadow",
            subtitle = "Seleziona i modelli da testare in shadow mode",
            badgeText = if (settings.shadowModelIds.isNotEmpty()) "${settings.shadowModelIds.size}" else null,
            onClick = onOpenShadowModel,
        ),
        SettingsMenuItemData(
            id = "scale_calibrations",
            icon = Icons.Outlined.Straighten,
            title = "Calibrazioni scala",
            subtitle = "Stoviglie e riferimenti corporei calibrati",
            onClick = onOpenScaleCalibrations,
        ),
        SettingsMenuItemData(
            id = "cost",
            icon = Icons.Outlined.Payments,
            title = "Gestione Costi",
            subtitle = "Monitoraggio spese per API e modelli",
            onClick = onOpenCostManagement,
        ),
        SettingsMenuItemData(
            id = "backup",
            icon = Icons.Outlined.Backup,
            title = "Backup e ripristino",
            subtitle = "Esporta, importa o ripristina i dati locali",
            onClick = onOpenBackup,
        ),
        SettingsMenuItemData(
            id = "changelog",
            icon = Icons.Outlined.SystemUpdate,
            title = "Software e aggiornamenti",
            subtitle = "Versione ${BuildConfig.VERSION_NAME} e storico rilasci",
            onClick = onOpenChangelog,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    if (showBackButton) {
                        BackIconButton(onBack)
                    }
                },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            mainSections.forEach { section ->
                item(key = "section_${section.title}", contentType = "settings_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UgoSectionHeader(title = section.title)
                        UgoCard(modifier = Modifier.fillMaxWidth()) {
                            section.items.forEachIndexed { index, item ->
                                SettingsMenuItemRow(item = item)
                                if (index < section.items.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "advanced_section") {
                UgoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedExpanded = !advancedExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Science,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Avanzate & Motore V2",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "BIAS, modelli shadow, scala, costi, backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = if (advancedExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (advancedExpanded) "Comprimi" else "Espandi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    AnimatedVisibility(
                        visible = advancedExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            advancedItems.forEachIndexed { index, item ->
                                SettingsMenuItemRow(item = item)
                                if (index < advancedItems.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = R.drawable.ugocalc_logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "UgoCalc",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Versione ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItemRow(item: SettingsMenuItemData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        item.badgeText?.let { badge ->
            Surface(
                shape = RoundedCornerShape(50),
                color = if (item.badgePositive) {
                    UgoSemanticColors.successContainer
                } else {
                    UgoSemanticColors.warningContainer
                },
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.badgePositive) {
                        UgoSemanticColors.onSuccessContainer
                    } else {
                        UgoSemanticColors.onWarningContainer
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
