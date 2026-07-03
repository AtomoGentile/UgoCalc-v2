package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.BuildConfig
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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Impostazioni") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsMenuItem("API", "Chiave API per OpenAI, Anthropic, Gemini", onOpenApi)
            SettingsMenuItem("Preferiti", "Scorciatoie mostrate in Homepage", onOpenFavorites)
            SettingsMenuItem("Permessi", "Fotocamera e notifiche", onOpenPermissions)
            SettingsMenuItem("Accessibilità", "Tema, dimensione testo, colori", onOpenAccessibility)
            SettingsMenuItem("Modelli preferiti", "Se impostati, saltano del tutto la scelta modello ad ogni analisi", onOpenPreferredModel)
            SettingsMenuItem("Livello di confidenza", "Soglia per evidenziare le analisi più affidabili", onOpenConfidence)
            SettingsMenuItem("Grafico cronologia", "Colonne CHO/grassi per pasto, con intervallo selezionabile", onOpenHistoryChart)
            SettingsMenuItem("Cancella cronologia delle analisi", "Cancella tutto lo storico delle analisi", onClick = { showClearHistoryDialog = true })
            SettingsMenuItem("Tabelle valori ufficiali", "Carica e modifica le tabelle CHO/grassi", onOpenTables)
            SettingsMenuItem("Software e aggiornamenti", "Come funzionano gli aggiornamenti e storico delle versioni", onOpenChangelog)

            Text(
                "Versione ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = UgoGrey,
                modifier = Modifier.padding(top = 24.dp, start = 16.dp, bottom = 8.dp),
            )
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Cancellare la cronologia?") },
            text = { Text("Questa azione elimina definitivamente tutte le analisi salvate.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); showClearHistoryDialog = false }) { Text("Cancella") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Annulla") }
            },
        )
    }
}

/**
 * Voce del menu Impostazioni (§ segnalazione utente "le Impostazioni sono ancora prive di
 * colore"): ogni riga è una Card tinta con il colore secondario del tema, così la lista intera
 * riflette i colori del tema invece di restare sui toni neutri di un ListItem semplice.
 *
 * Prima ruotava tra primario/secondario/terziario (§ segnalazione utente "rendi omogeneo il
 * colore, tutto col secondario"): ora usa sempre il secondario, lasciando il terziario per i
 * selettori (stellina preferiti, spillo, filtro attivo).
 *
 * Bug fix: usava secondaryContainer, un tono sfumato verso bianco/nero (vedi Theme.kt), non il
 * colore secondario scelto per davvero dall'utente — stesso problema già corretto sui pulsanti
 * "+"/"Salva" flottanti.
 */
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
