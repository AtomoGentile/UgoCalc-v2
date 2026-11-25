package com.ugocalc.app.ui.settings.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.LoadingOverlay
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/**
 * Editor "tipo Excel" della tabella normalizzata (requisito utente): griglia di celle modificabili
 * (nome, CHO/100g, grassi/100g), con riga aggiungibile/eliminabile. Il pulsante "Salva" compare solo
 * quando ci sono modifiche pendenti (`isDirty`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableEditorScreen(
    onBack: () -> Unit,
    viewModel: TableEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    com.ugocalc.app.ui.components.EditorialDetailScaffold(
        title = uiState.tableDisplayName.ifBlank { "Editor tabella" },
        onBack = onBack,
        trailing = {
            if (uiState.isDirty) {
                com.ugocalc.app.ui.components.FloatingCircleIconButton(
                    icon = Icons.Default.Save,
                    contentDescription = "Salva tabella",
                    onClick = { viewModel.save() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    size = 40.dp,
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${uiState.rows.size} alimenti",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { viewModel.addRow() },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aggiungi riga")
                    }
                }

                UgoCard(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            HeaderRow()
                            HorizontalDivider()
                            LazyColumn {
                                items(uiState.rows, key = { it.localKey }) { row ->
                                    EditableTableRow(
                                        row = row,
                                        onNomeChange = { viewModel.updateNome(row.localKey, it) },
                                        onChoChange = { viewModel.updateCho(row.localKey, it) },
                                        onProteineChange = { viewModel.updateProteine(row.localKey, it) },
                                        onGrassiChange = { viewModel.updateGrassi(row.localKey, it) },
                                        onGiChange = { viewModel.updateIndiceGlicemico(row.localKey, it) },
                                        onDelete = { viewModel.deleteRow(row.localKey) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                LoadingOverlay(statusText = "Caricamento tabella…")
            }
        }
    }
}

private val NOME_COLUMN_WIDTH = 220.dp
private val NUMERIC_COLUMN_WIDTH = 128.dp
private val GI_COLUMN_WIDTH = 108.dp

@Composable
private fun HeaderRow() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Alimento", fontWeight = FontWeight.Bold, modifier = Modifier.width(NOME_COLUMN_WIDTH))
        Text("CHO/100g", fontWeight = FontWeight.Bold, modifier = Modifier.width(NUMERIC_COLUMN_WIDTH))
        Text("Prot/100g", fontWeight = FontWeight.Bold, modifier = Modifier.width(NUMERIC_COLUMN_WIDTH))
        Text("Grassi/100g", fontWeight = FontWeight.Bold, modifier = Modifier.width(NUMERIC_COLUMN_WIDTH))
        Text("IG (0–100)", fontWeight = FontWeight.Bold, modifier = Modifier.width(GI_COLUMN_WIDTH))
        Text("", modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun EditableTableRow(
    row: EditableRow,
    onNomeChange: (String) -> Unit,
    onChoChange: (String) -> Unit,
    onProteineChange: (String) -> Unit,
    onGrassiChange: (String) -> Unit,
    onGiChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val parsedGi = row.indiceGlicemico.trim().toIntOrNull()
    val giError = row.indiceGlicemico.isNotBlank() && (parsedGi == null || parsedGi !in 0..100)

    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = row.nome,
            onValueChange = onNomeChange,
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(NOME_COLUMN_WIDTH),
        )
        OutlinedTextField(
            value = row.choPer100g,
            onValueChange = onChoChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(NUMERIC_COLUMN_WIDTH),
        )
        OutlinedTextField(
            value = row.proteinePer100g,
            onValueChange = onProteineChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(NUMERIC_COLUMN_WIDTH),
        )
        OutlinedTextField(
            value = row.grassiPer100g,
            onValueChange = onGrassiChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(NUMERIC_COLUMN_WIDTH),
        )
        OutlinedTextField(
            value = row.indiceGlicemico,
            onValueChange = onGiChange,
            placeholder = { Text("—") },
            singleLine = true,
            isError = giError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(GI_COLUMN_WIDTH),
        )
        IconButton(onClick = onDelete, modifier = Modifier.width(44.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Elimina riga",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
