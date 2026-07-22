package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.BuildConfig
import com.ugocalc.app.R
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey

/** Menu principale Impostazioni (§2.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenApi: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenPreferredModel: () -> Unit,
    onOpenConfidence: () -> Unit,
    onOpenTables: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenHistoryChart: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenScaleCalibrations: () -> Unit,
    onOpenRecipes: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Impostazioni") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SettingsMenuItem("API", "Chiave API per OpenAI, Anthropic, Gemini", onOpenApi) }
            item { SettingsMenuItem("Preferiti", "Scorciatoie mostrate in Homepage", onOpenFavorites) }
            item { SettingsMenuItem("Permessi", "Fotocamera e notifiche", onOpenPermissions) }
            item { SettingsMenuItem("Accessibilità", "Tema, dimensione testo, colori", onOpenAccessibility) }
            item { SettingsMenuItem("Modelli preferiti", "Se impostati, saltano del tutto la scelta modello ad ogni analisi", onOpenPreferredModel) }
            item { SettingsMenuItem("Livello di confidenza", "Soglia per evidenziare le analisi più affidabili", onOpenConfidence) }
            item { SettingsMenuItem("Grafico cronologia", "Colonne CHO/grassi per pasto, con intervallo selezionabile", onOpenHistoryChart) }
            item { SettingsMenuItem("Tabelle valori ufficiali", "Carica e modifica le tabelle CHO/grassi", onOpenTables) }
            item { SettingsMenuItem("Backup e ripristino", "Esporta o importa cronologia, database e dati di correzione del bias", onOpenBackup) }
            item { SettingsMenuItem("Calibrazioni scala", "Gestisci le tue stoviglie e riferimenti corporei calibrati", onOpenScaleCalibrations) }
            item { SettingsMenuItem("Ricette", "Gestisci le ricette per la strategia Composto", onOpenRecipes) }
            item { SettingsMenuItem("Software e aggiornamenti", "Come funzionano gli aggiornamenti e storico delle versioni", onOpenChangelog) }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ugocalc_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        "UgoCalc",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
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
    val container = MaterialTheme.colorScheme.secondary
    val onContainer = MaterialTheme.colorScheme.onSecondary
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = onContainer,
                supportingColor = onContainer,
                trailingIconColor = onContainer,
            ),
        )
    }
}
