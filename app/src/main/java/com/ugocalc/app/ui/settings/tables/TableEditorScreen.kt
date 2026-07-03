package com.ugocalc.app.ui.settings.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/**
 * Editor "tipo Excel" della tabella normalizzata (requisito utente): griglia di celle modificabili
 * (nome, CHO/100g, grassi/100g), con riga aggiungibile/eliminabile. Il pulsante "Salva" compare solo
 * quando ci sono modifiche pendenti (`isDirty`), come richiesto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableEditorScreen(
    onBack: () -> Unit,
    viewModel: TableEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(uiState.tableDisplayName) }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) },
        floatingActionButton = {
            if (uiState.isDirty) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.save() },
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text("Salva") },
                    // Bug fix: secondaryContainer è un tono sfumato verso bianco/nero, non il
                    // colore secondario scelto dall'utente in Accessibilità (vedi Theme.kt).
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondary,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                IconButton(onClick = { viewModel.addRow() }) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi riga")
                }
                Text("Aggiungi riga", modifier = Modifier.align(Alignment.CenterVertically))
            }

            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    HeaderRow()
                    LazyColumn {
                        items(uiState.rows, key = { it.localKey }) { row ->
                            EditableTableRow(
                                row = row,
                                onNomeChange = { viewModel.updateNome(row.localKey, it) },
                                onChoChange = { viewModel.updateCho(row.localKey, it) },
                                onGrassiChange = { viewModel.updateGrassi(row.localKey, it) },
                                onDelete = { viewModel.deleteRow(row.localKey) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val NOME_COLUMN_WIDTH = 220.dp
private val NUMERIC_COLUMN_WIDTH = 130.dp

@Composable
private fun HeaderRow() {
    Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
        Text("Alimento", fontWeight = FontWeight.Bold, modifier = Modifier.width(NOME_COLUMN_WIDTH))
        Text("CHO/100g", fontWeight = FontWeight.Bold, modifier = Modifier.width(NUMERIC_COLUMN_WIDTH))
        Text("Grassi/100g", fontWeight = FontWeight.Bold, modifier = Modifier.width(NUMERIC_COLUMN_WIDTH))
        Text("", modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun EditableTableRow(
    row: EditableRow,
    onNomeChange: (String) -> Unit,
    onChoChange: (String) -> Unit,
    onGrassiChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = row.nome,
            onValueChange = onNomeChange,
            singleLine = true,
            modifier = Modifier.width(NOME_COLUMN_WIDTH),
        )
        OutlinedTextField(
            value = row.choPer100g,
            onValueChange = onChoChange,
            singleLine = true,
            modifier = Modifier.width(NUMERIC_COLUMN_WIDTH),
        )
        OutlinedTextField(
            value = row.grassiPer100g,
            onValueChange = onGrassiChange,
            singleLine = true,
            modifier = Modifier.width(NUMERIC_COLUMN_WIDTH),
        )
        IconButton(onClick = onDelete, modifier = Modifier.width(48.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Elimina riga")
        }
    }
}
