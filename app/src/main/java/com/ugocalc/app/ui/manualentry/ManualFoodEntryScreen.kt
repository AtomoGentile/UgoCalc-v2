package com.ugocalc.app.ui.manualentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.FoodSearchPicker
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import androidx.compose.ui.text.style.TextOverflow


/**
 * Composizione manuale di un pasto (§ ricerca tra i cibi del database): alternativa alla foto, in
 * due passi — SELEZIONA (§ richiesta utente "lascia fare solo la selezione") sceglie gli alimenti
 * dalle proprie tabelle, poi QUANTITÀ indica il peso di ognuno. Nessuna chiamata AI, il risultato
 * va dritto alla schermata finale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualFoodEntryScreen(
    onProceed: () -> Unit,
    onBack: () -> Unit,
    viewModel: ManualFoodEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState.step) {
        ManualEntryStep.SELECT -> SelectStep(uiState = uiState, viewModel = viewModel, onBack = onBack)
        ManualEntryStep.QUANTITY -> QuantityStep(uiState = uiState, viewModel = viewModel, onBack = onBack, onProceed = onProceed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectStep(
    uiState: ManualFoodEntryUiState,
    viewModel: ManualFoodEntryViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                // In alto il numero di selezionati (§ richiesta utente), il nome resta nella riga
                // del risultato come già oggi.
                title = { Text(if (uiState.selected.isEmpty()) "Aggiungi alimenti manualmente" else "${uiState.selected.size} selezionati") },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            FoodSearchPicker(
                onSelect = {},
                selectedEntryIds = uiState.selected.map { it.entryId }.toSet(),
                onToggleSelect = viewModel::toggleSelect,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                fillAvailableHeight = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onBack) { Text("Indietro") }
                Button(
                    enabled = uiState.selected.isNotEmpty(),
                    onClick = viewModel::confirmSelection,
                ) { Text("Continua") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuantityStep(
    uiState: ManualFoodEntryUiState,
    viewModel: ManualFoodEntryViewModel,
    onBack: () -> Unit,
    onProceed: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Indica le quantità") }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(uiState.rows) { index, row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = row.entry.nome,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "CHO ${"%.1f".format(row.entry.choPer100g)} g · Grassi ${"%.1f".format(row.entry.grassiPer100g)} g per 100g",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedTextField(
                            value = row.gramsText,
                            onValueChange = { viewModel.setGrams(index, it) },
                            label = { Text("g") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = row.gramsOrNull == null,
                            modifier = Modifier.width(90.dp),
                        )
                        IconButton(onClick = { viewModel.removeFood(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Rimuovi ${row.entry.nome}")
                        }
                    }
                    HorizontalDivider()
                }
            }
            if (uiState.rows.isEmpty()) {
                Spacer(modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = { if (uiState.skipSelectStep) onBack() else viewModel.backToSelection() }) { Text("Indietro") }
                Button(
                    enabled = uiState.canProceed,
                    onClick = { if (viewModel.confirmAndProceed()) onProceed() },
                ) { Text("Calcola") }
            }
        }
    }
}
