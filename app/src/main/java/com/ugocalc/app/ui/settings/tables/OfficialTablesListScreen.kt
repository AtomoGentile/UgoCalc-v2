package com.ugocalc.app.ui.settings.tables

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors

private val ACCEPTED_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "image/jpeg",
    "image/png",
)

private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * "Tabelle valori ufficiali" (§2.2): elenco dei file caricati, con priorità riordinabile e accesso
 * all'editor tipo Excel di ciascuna tabella.
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
    var pendingDeleteTable by remember { mutableStateOf<OfficialTableEntity?>(null) }

    val aiFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uris.first())
        val fileType = mimeTypeToFileType(mimeType)
        if (fileType != null) {
            val displayName = if (uris.size == 1) {
                queryDisplayName(context, uris.first()) ?: "Tabella"
            } else {
                val first = queryDisplayName(context, uris.first()) ?: "Tabella"
                "$first (+${uris.size - 1})"
            }
            viewModel.importFiles(uris, displayName, fileType)
        }
    }

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
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi tabella")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            uiState.errorMessage?.let { error ->
                item(key = "error_banner") {
                    StatusBanner(
                        icon = Icons.Default.ErrorOutline,
                        title = "Errore tabella",
                        subtitle = error,
                        tone = BannerTone.ERROR,
                    )
                }
            }

            uiState.statusMessage?.let { msg ->
                item(key = "status_banner") {
                    StatusBanner(
                        icon = Icons.Default.CheckCircle,
                        title = "Operazione completata",
                        subtitle = msg,
                        tone = BannerTone.SUCCESS,
                    )
                }
            }

            item(key = "restore_starter") {
                OutlinedButton(
                    onClick = { viewModel.restoreStarterTable() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ripristina Tabella Base")
                }
            }

            if (uiState.tables.isEmpty()) {
                item(key = "empty_tables") {
                    EmptyStateMessage(
                        icon = Icons.Default.TableChart,
                        title = "Nessuna tabella presente",
                        message = "Carica un file PDF/Excel con + oppure ripristina la Tabella Base Italiana (CREA).",
                        actionLabel = "Ripristina Tabella Base",
                        onAction = { viewModel.restoreStarterTable() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            } else {
                items(uiState.tables, key = { it.id }) { table ->
                    TableRow(
                        table = table,
                        progress = uiState.normalizationProgress[table.id],
                        onClick = { onOpenTable(table.id) },
                        onMoveUp = { viewModel.moveUp(table) },
                        onMoveDown = { viewModel.moveDown(table) },
                        onDelete = { pendingDeleteTable = table },
                        onRetry = { viewModel.retryNormalization(table.id) },
                    )
                }
            }

            item(key = "fab_spacer") {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }

    if (showImportChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showImportChoiceDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Come vuoi aggiungere la tabella?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"Normalizza con AI\" accetta PDF/Word/Excel in qualsiasi formato, ma su tabelle molto grandi può fallire per i limiti di quota dei provider. " +
                        "\"File già pronto\" salta del tutto l'AI: serve un Excel con una prima riga di intestazione che contenga colonne per nome, CHO e grassi " +
                        "(es. \"nome\", \"cho_per_100g\", \"grassi_per_100g\"), una riga per alimento, valori per 100g.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportChoiceDialog = false
                        preFormattedFilePicker.launch(arrayOf(XLSX_MIME_TYPE))
                    },
                ) { Text("File già pronto (Excel)") }
            },
            dismissButton = {
                Box {
                    TextButton(
                        onClick = {
                            if (uiState.availableModels.size > 1) {
                                showAiModelMenu = true
                            } else {
                                showImportChoiceDialog = false
                                aiFilePicker.launch(ACCEPTED_MIME_TYPES)
                            }
                        },
                    ) { Text("Normalizza con AI") }
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

    pendingDeleteTable?.let { table ->
        UgoConfirmDialog(
            title = "Elimina tabella",
            message = "Vuoi eliminare \"${table.displayName}\" e tutte le sue voci dal database?",
            confirmLabel = "Elimina",
            isDestructive = true,
            onConfirm = {
                pendingDeleteTable = null
                viewModel.deleteTable(table)
            },
            onDismiss = { pendingDeleteTable = null },
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
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Priorità su")
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Priorità giù")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = table.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Priorità ${table.priorityOrder + 1} · ${table.fileType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                TableStatusPill(status = table.normalizationStatus, progress = progress)
                if (table.normalizationStatus == NormalizationStatus.FAILED && !table.normalizationErrorMessage.isNullOrBlank()) {
                    Text(
                        text = table.normalizationErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (table.normalizationStatus == NormalizationStatus.FAILED) {
                IconButton(onClick = onRetry) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Riprova normalizzazione",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Elimina tabella",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TableStatusPill(status: NormalizationStatus, progress: String?) {
    val (label, containerColor, contentColor) = when (status) {
        NormalizationStatus.DONE -> Triple(
            "Pronta ✓",
            UgoSemanticColors.successContainer,
            UgoSemanticColors.onSuccessContainer,
        )
        NormalizationStatus.PENDING, NormalizationStatus.PROCESSING -> {
            val suffix = progress?.let { " ($it)" }.orEmpty()
            Triple(
                "In corso$suffix",
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        NormalizationStatus.FAILED -> Triple(
            "Errore",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun mimeTypeToFileType(mimeType: String?): OfficialFileType? = when (mimeType) {
    "application/pdf" -> OfficialFileType.PDF
    "application/msword" -> OfficialFileType.DOC
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> OfficialFileType.DOCX
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> OfficialFileType.XLSX
    "image/jpeg" -> OfficialFileType.JPG
    "image/png" -> OfficialFileType.PNG
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
