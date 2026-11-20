package com.ugocalc.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.backup.BackupSummary
import com.ugocalc.app.data.backup.ImportValidation
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
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
    var statusMessage by remember { mutableStateOf<Pair<String, BannerTone>?>(null) }
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
                "Backup esportato correttamente." to BannerTone.SUCCESS
            } catch (e: Exception) {
                "Esportazione non riuscita: ${e.message ?: "errore sconosciuto"}" to BannerTone.ERROR
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
                statusMessage = "Impossibile leggere il file scelto." to BannerTone.ERROR
            } else {
                when (val validation = viewModel.previewImport(content)) {
                    is ImportValidation.Ok -> pendingImport = content to validation.summary
                    is ImportValidation.SchemaTooNew ->
                        statusMessage = "Questo backup viene da una versione più recente dell'app (formato ${validation.found}, supportato ${validation.supported}): aggiorna prima di importare." to BannerTone.WARNING
                    is ImportValidation.Invalid ->
                        statusMessage = "File non valido: ${validation.reason}" to BannerTone.ERROR
                }
            }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup e ripristino") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusBanner(
                icon = Icons.Default.Backup,
                title = "Backup locale completo",
                subtitle = "Esporta cronologia, database alimenti, calibrazioni, BIAS e preferiti in un file JSON. Le chiavi API restano sempre escluse.",
                tone = BannerTone.INFO,
            )

            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                UgoSectionHeader(title = "Esporta o importa")
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { exportLauncher.launch("ugocalc_backup.json") },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Esporta backup")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importa backup")
                }
            }

            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                contentPadding = PaddingValues(16.dp),
            ) {
                UgoSectionHeader(
                    title = "Zona pericolosa",
                    subtitle = "Queste azioni eliminano dati locali in modo permanente",
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Cancella tutto lo storico delle analisi salvate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedButton(
                    onClick = { showClearHistoryDialog = true },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancella cronologia delle analisi")
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Rimuove personalizzazioni, database caricati, cronologia e chiavi API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Button(
                    onClick = { showResetDialog = true },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ripristina tutte le impostazioni")
                }
            }

            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text("Operazione in corso…")
                }
            }

            statusMessage?.let { (message, tone) ->
                StatusBanner(
                    icon = if (tone == BannerTone.ERROR) Icons.Default.Warning else Icons.Default.Info,
                    title = if (tone == BannerTone.SUCCESS) "Operazione completata" else "Avviso",
                    subtitle = message,
                    tone = tone,
                )
            }
        }
    }

    val pending = pendingImport
    if (pending != null) {
        val (content, summary) = pending
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Sostituire i dati esistenti?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Questa importazione sostituirà del tutto:")
                    Text("• ${summary.historyCount} analisi in cronologia (V1)", modifier = Modifier.padding(top = 8.dp))
                    Text("• ${summary.v2RecordsCount} analisi V2 (sezione BIAS)")
                    Text("• ${summary.tablesCount} tabelle del database")
                    Text("• ${summary.scaleCalibrationsCount} calibrazioni")
                    Text("• ${summary.biasCorrectionsCount} fattori di correzione del bias")
                    Text("• ${summary.favoritesCount} preferiti")
                    Text(
                        "I dati attuali in queste categorie andranno persi. L'operazione non si può annullare.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImport = null
                        scope.launch {
                            busy = true
                            statusMessage = try {
                                viewModel.applyImport(content)
                                "Backup importato: dati sostituiti." to BannerTone.SUCCESS
                            } catch (e: Exception) {
                                "Importazione non riuscita: ${e.message ?: "errore sconosciuto"}" to BannerTone.ERROR
                            }
                            busy = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Sostituisci")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Annulla") }
            },
        )
    }

    if (showClearHistoryDialog) {
        UgoConfirmDialog(
            title = "Cancellare la cronologia?",
            message = "Questa azione elimina definitivamente tutte le analisi salvate.",
            confirmLabel = "Cancella",
            isDestructive = true,
            onConfirm = {
                viewModel.clearHistory()
                showClearHistoryDialog = false
                statusMessage = "Cronologia cancellata." to BannerTone.SUCCESS
            },
            onDismiss = { showClearHistoryDialog = false },
        )
    }

    if (showResetDialog) {
        UgoConfirmDialog(
            title = "Ripristinare tutto?",
            message = "Questa operazione cancellerà permanentemente ogni dato, chiave API e configurazione dell'app. Non si può annullare.",
            confirmLabel = "Ripristina tutto",
            isDestructive = true,
            onConfirm = {
                viewModel.resetAllSettings()
                showResetDialog = false
                statusMessage = "Impostazioni ripristinate ai valori predefiniti." to BannerTone.SUCCESS
            },
            onDismiss = { showResetDialog = false },
        )
    }
}
