package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.BuildConfig
import com.ugocalc.app.R
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.GlassmorphicCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.SpaceL
import com.ugocalc.app.ui.theme.SpaceM
import com.ugocalc.app.ui.theme.SpaceS
import com.ugocalc.app.ui.theme.SpaceXL
import com.ugocalc.app.ui.theme.UgoGrey
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable

private data class SettingsMenuItemData(
    val id: String,
    val title: String,
    val subtitle: String,
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
    onOpenShadowModel: () -> Unit,
    onOpenConfidence: () -> Unit,
    onOpenTables: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenHistoryChart: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenScaleCalibrations: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenAccount: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors()
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            val sections = listOf(
                SettingsSectionData(
                    "ACCOUNT & API",
                    listOf(
                        SettingsMenuItemData("account", "Account", "Accesso, condivisione e bias globale", onOpenAccount),
                        SettingsMenuItemData("api", "API", "Chiave API per OpenAI, Anthropic, Gemini", onOpenApi),
                    )
                ),
                SettingsSectionData(
                    "PREFERENZE",
                    listOf(
                        SettingsMenuItemData("favorites", "Preferiti", "Scorciatoie mostrate in Homepage", onOpenFavorites),
                        SettingsMenuItemData("permissions", "Permessi", "Fotocamera e notifiche", onOpenPermissions),
                        SettingsMenuItemData("accessibility", "Accessibilità", "Tema, dimensione testo, colori", onOpenAccessibility),
                    )
                ),
                SettingsSectionData(
                    "MODELLI & ANALISI",
                    listOf(
                        SettingsMenuItemData("preferred_models", "Modelli preferiti", "Se impostati, saltano del tutto la scelta modello ad ogni analisi", onOpenPreferredModel),
                        SettingsMenuItemData("shadow_models", "Modelli V2 shadow", "Seleziona i modelli da testare in shadow mode", onOpenShadowModel),
                        SettingsMenuItemData("confidence", "Livello di confidenza", "Soglia per evidenziare le analisi più affidabili", onOpenConfidence),
                        SettingsMenuItemData("history_chart", "Grafico cronologia", "Colonne CHO/grassi per pasto, con intervallo selezionabile", onOpenHistoryChart),
                    )
                ),
                SettingsSectionData(
                    "STRUMENTI",
                    listOf(
                        SettingsMenuItemData("tables", "Tabelle valori ufficiali", "Carica e modifica le tabelle CHO/grassi", onOpenTables),
                        SettingsMenuItemData("recipes", "Ricette", "Gestisci le ricette per la strategia Composto", onOpenRecipes),
                        SettingsMenuItemData("scale_calibrations", "Calibrazioni scala", "Gestisci le tue stoviglie e riferimenti corporei calibrati", onOpenScaleCalibrations),
                    )
                ),
                SettingsSectionData(
                    "AVANZATE",
                    listOf(
                        SettingsMenuItemData("backup", "Backup e ripristino", "Esporta o importa cronologia, database e dati di correzione del bias", onOpenBackup),
                        SettingsMenuItemData("changelog", "Software e aggiornamenti", "Come funzionano gli aggiornamenti e storico delle versioni", onOpenChangelog),
                    )
                ),
            )

            sections.forEachIndexed { sectionIndex, section ->
                item(key = "section_${section.title}") {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            start = SpaceM,
                            top = if (sectionIndex == 0) SpaceM else SpaceL,
                            bottom = SpaceS
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    GlassmorphicCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpaceS),
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column {
                            section.items.forEachIndexed { itemIndex, item ->
                                SettingsMenuItem(item.title, item.subtitle, item.onClick)
                                if (itemIndex < section.items.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = SpaceM),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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
                        .padding(top = SpaceXL, bottom = SpaceM),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ugocalc_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        "UgoCalc",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = SpaceS)
                    )
                    Text(
                        "Versione ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = UgoGrey,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}
