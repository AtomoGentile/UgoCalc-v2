package com.ugocalc.app.ui.settings.tables

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.local.db.entity.NormalizationStatus
import com.ugocalc.app.data.local.db.entity.OfficialTableEntity
import com.ugocalc.app.domain.model.OfficialFileType
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors

private val ACCEPTED_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)

private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * "Tabelle valori ufficiali" (§2.2): elenco dei file caricati, con priorità riordinabile e accesso
 * all'editor tipo Excel di ciascuna tabella (requisito utente: piccola pagina di selezione se più
 * di un documento è presente).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialTablesListScreen(
    onBack: () -> Unit,
    onOpenTable: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: OfficialTablesListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showImportChoiceDialog by remember { mutableStateOf(false) }
    var showAiModelMenu by remember { mutableStateOf(false) }

    val aiFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri)
        val fileType = mimeTypeToFileType(mimeType)
        val displayName = queryDisplayName(context, uri) ?: "Tabella"
        if (fileType != null) viewModel.importFile(uri, displayName, fileType)
    }

    // "File già pronto" (§ richiesta utente): un Excel con colonne nome/CHO/grassi già nella
    // prima riga viene letto direttamente, senza passare dal Passaggio 2 AI — utile su tabelle
    // molto grandi dove la normalizzazione va spesso in errore per limiti di quota dei provider.
    val preFormattedFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = queryDisplayName(context, uri) ?: "Tabella"
        viewModel.importPreFormatted(uri, displayName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tabelle valori ufficiali") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Cerca nel database")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportChoiceDialog = true },
                // Bug fix: secondaryContainer è un tono sfumato verso bianco/nero, non il
                // colore secondario scelto dall'utente in Accessibilità (vedi Theme.kt).
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi tabella")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (uiState.tables.isEmpty()) {
                Text("Nessuna tabella caricata. Tocca + per aggiungerne una.")
            }
            LazyColumn {
                items(uiState.tables, key = { it.id }) { table ->
                    TableRow(
                        table = table,
                        progress = uiState.normalizationProgress[table.id],
                        onClick = { onOpenTable(table.id) },
                        onMoveUp = { viewModel.moveUp(table) },
                        onMoveDown = { viewModel.moveDown(table) },
                        onDelete = { viewModel.deleteTable(table) },
                        onRetry = { viewModel.retryNormalization(table.id) },
                    )
                }
            }
        }
    }

    if (showImportChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showImportChoiceDialog = false },
            title = { Text("Come vuoi aggiungere la tabella?") },
            text = {
                Text(
                    "\"Normalizza con AI\" accetta PDF/Word/Excel in qualsiasi formato, ma su tabelle molto grandi può fallire per i limiti di quota dei provider. " +
                        "\"File già pronto\" salta del tutto l'AI: serve un Excel con una prima riga di intestazione che contenga colonne per nome, CHO e grassi " +
                        "(es. \"nome\", \"cho_per_100g\", \"grassi_per_100g\"), una riga per alimento, valori per 100g.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportChoiceDialog = false
                    preFormattedFilePicker.launch(arrayOf(XLSX_MIME_TYPE))
                }) { Text("File già pronto (Excel)") }
            },
            dismissButton = {
                // Il modello serve solo per normalizzare con l'AI (né per "file già pronto" né per
                // la lista in sé), quindi non ha senso tenerlo sempre visibile in schermata: appare
                // qui, in un menu, solo quando l'utente sceglie esplicitamente questa via.
                Box {
                    TextButton(onClick = {
                        if (uiState.availableModels.size > 1) {
                            showAiModelMenu = true
                        } else {
                            showImportChoiceDialog = false
                            aiFilePicker.launch(ACCEPTED_MIME_TYPES)
                        }
                    }) { Text("Normalizza con AI") }
                    DropdownMenu(expanded = showAiModelMenu, onDismissRequest = { showAiModelMenu = false }) {
                        uiState.availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName) },
                                onClick = {
                                    viewModel.selectModel(model)
                                    showAiModelMenu = false
                                    showImportChoiceDialog = false
                                    aiFilePicker.launch(ACCEPTED_MIME_TYPES)
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun TableRow(
    table: OfficialTableEntity,
    progress: String?,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                IconButton(onClick = onMoveUp) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Priorità su") }
                IconButton(onClick = onMoveDown) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Priorità giù") }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp).clickableTable(onClick)) {
                Text(table.displayName, fontWeight = FontWeight.Bold)
                Text("Priorità ${table.priorityOrder + 1} · ${table.fileType}", style = MaterialTheme.typography.bodySmall)
                when (table.normalizationStatus) {
                    NormalizationStatus.PENDING, NormalizationStatus.PROCESSING -> {
                        val suffix = progress?.let { " (parte $it)" }.orEmpty()
                        Text("Normalizzazione in corso...$suffix", style = MaterialTheme.typography.bodySmall)
                    }
                    NormalizationStatus.FAILED ->
                        Text("Normalizzazione fallita: ${table.normalizationErrorMessage}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    NormalizationStatus.DONE -> Text("Pronta", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (table.normalizationStatus == NormalizationStatus.FAILED) {
                IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "Riprova normalizzazione") }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Elimina tabella") }
        }
    }
}

private fun Modifier.clickableTable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable { onClick() })

private fun mimeTypeToFileType(mimeType: String?): OfficialFileType? = when (mimeType) {
    "application/pdf" -> OfficialFileType.PDF
    "application/msword" -> OfficialFileType.DOC
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> OfficialFileType.DOCX
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> OfficialFileType.XLSX
    else -> null
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) return it.getString(nameIndex)
    }
    return null
}
