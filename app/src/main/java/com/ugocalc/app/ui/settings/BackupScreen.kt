package com.ugocalc.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.backup.BackupSummary
import com.ugocalc.app.data.backup.ImportValidation
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Impostazioni > Backup e ripristino (piano V2 §17).
 * Gestisce l'importazione/esportazione, la pulizia della cronologia e il ripristino di fabbrica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pair<String, BackupSummary>?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            statusMessage = try {
                val content = viewModel.exportAll()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                }
                "Backup esportato."
            } catch (e: Exception) {
                "Esportazione non riuscita: ${e.message ?: "errore sconosciuto"}"
            }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val content = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (content == null) {
                statusMessage = "Impossibile leggere il file scelto."
            } else {
                when (val validation = viewModel.previewImport(content)) {
                    is ImportValidation.Ok -> pendingImport = content to validation.summary
                    is ImportValidation.SchemaTooNew ->
                        statusMessage = "Questo backup viene da una versione più recente dell'app (formato ${validation.found}, supportato ${validation.supported}): aggiorna prima di importare."
                    is ImportValidation.Invalid ->
                        statusMessage = "File non valido: ${validation.reason}"
                }
            }
            busy = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Backup e ripristino") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(
                "Esporta o importa un unico file con cronologia, database delle tabelle, fattori di " +
                    "correzione del bias e preferiti. Le chiavi API non fanno mai parte del backup. " +
                    "Un'importazione sostituisce del tutto i dati esistenti in queste quattro categorie: " +
                    "non è una fusione, ed è per questo che chiede sempre conferma prima di procedere.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            OutlinedButton(onClick = { exportLauncher.launch("ugocalc_backup.json") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Esporta backup")
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Importa backup")
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Sezione pericolosa: Pulizia e Ripristino
            Text(
                "Gestione dati",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Cancella Cronologia
            Text(
                "Cancella tutto lo storico delle analisi salvate.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = { showClearHistoryDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancella cronologia delle analisi")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ripristino di fabbrica
            Text(
                "Rimuove tutte le personalizzazioni, i database caricati, la cronologia e le chiavi API.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ripristina tutte le impostazioni")
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text("Operazione in corso...")
                }
            }
            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = UgoGrey, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    val pending = pendingImport
    if (pending != null) {
        val (content, summary) = pending
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Sostituire i dati esistenti?") },
            text = {
                Column {
                    Text("Questa importazione sostituirà del tutto:")
                    Text("• ${summary.historyCount} analisi in cronologia", modifier = Modifier.padding(top = 8.dp))
                    Text("• ${summary.tablesCount} tabelle del database")
                    Text("• ${summary.biasCorrectionsCount} fattori di correzione del bias")
                    Text("• ${summary.favoritesCount} preferiti")
                    Text(
                        "I dati attuali in queste categorie andranno persi. L'operazione non si può annullare.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        busy = true
                        statusMessage = try {
                            viewModel.applyImport(content)
                            "Backup importato: dati sostituiti."
                        } catch (e: Exception) {
                            "Importazione non riuscita: ${e.message ?: "errore sconosciuto"}"
                        }
                        busy = false
                    }
                }) { Text("Sostituisci") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Annulla") }
            },
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Cancellare la cronologia?") },
            text = { Text("Questa azione elimina definitivamente tutte le analisi salvate.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                        statusMessage = "Cronologia cancellata."
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Cancella") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Annulla") }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Sei sicuro?") },
            text = { Text("Questa operazione cancellerà permanentemente ogni dato e configurazione dell'app. Non si può annullare.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllSettings()
                        showResetDialog = false
                        statusMessage = "Impostazioni ripristinate."
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Ripristina") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Annulla") }
            }
        )
    }
}
