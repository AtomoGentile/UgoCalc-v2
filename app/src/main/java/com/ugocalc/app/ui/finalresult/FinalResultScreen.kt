package com.ugocalc.app.ui.finalresult

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.OnlineFoodResearchResult
import com.ugocalc.app.domain.model.OnlineResearchReliability
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.FoodSearchPicker
import com.ugocalc.app.ui.components.MacronutrientPieChart
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey

/** Schermata finale: CHO/grassi complessivi, dettaglio espandibile, Salva (§2.4 punti 6-7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalResultScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: FinalResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var correctingLineIndex by remember { mutableStateOf<Int?>(null) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }
    var editingNameText by remember { mutableStateOf("") }
    var editingGramsText by remember { mutableStateOf("") }
    var addingManualFood by remember { mutableStateOf(false) }
    var pendingManualEntry by remember { mutableStateOf<NormalizedFoodEntry?>(null) }
    var pendingManualGramsText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risultato") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
                actions = {
                    IconButton(onClick = { viewModel.toggleFavoriteRequested() }) {
                        Icon(
                            if (uiState.favoriteRequested) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (uiState.favoriteRequested) "Non salvare tra i preferiti" else "Salva anche tra i preferiti",
                            tint = if (uiState.favoriteRequested) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = uiState.foodTitle,
                onValueChange = viewModel::setFoodTitle,
                label = { Text("Nome del piatto") },
                modifier = Modifier.fillMaxWidth(),
            )

            val totals = uiState.totals
            if (totals != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("CHO totali: ${"%.1f".format(totals.totalChoG)} g", style = MaterialTheme.typography.titleLarge)
                        Text("Grassi totali: ${"%.1f".format(totals.totalGrassiG)} g", style = MaterialTheme.typography.titleLarge)
                        MacronutrientPieChart(
                            choPercent = totals.choPercentOfMacros,
                            grassiPercent = totals.grassiPercentOfMacros,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (totals.excludedCount > 0) {
                            Text(
                                "${totals.excludedCount} alimento/i esclusi dal conteggio per assenza di corrispondenza in tabella",
                                style = MaterialTheme.typography.bodySmall,
                                color = UgoGrey,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                ) {
                    Text("Dettaglio", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addingManualFood = true }) { Text("+ Aggiungi alimento") }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(totals.lines) { index, line ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${line.recognizedFood.nome} — ${line.recognizedFood.pesoStimatoG.toInt()}g",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    editingNameText = line.recognizedFood.nome
                                    editingGramsText = line.recognizedFood.pesoStimatoG.toInt().toString()
                                    editingLineIndex = index
                                }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Correggi nome o peso dell'alimento",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(onClick = { viewModel.removeLine(index) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Rimuovi ${line.recognizedFood.nome}",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (line.isExcluded) {
                                Text("Nessuna corrispondenza trovata in tabella: escluso dal totale", color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
                                OnlineResearchSection(
                                    state = uiState.onlineResearch[index],
                                    onSearch = { viewModel.searchOnline(index) },
                                    onAdd = { viewModel.addOnlineResultToDatabase(index) },
                                    onDismiss = { viewModel.dismissOnlineResult(index) },
                                )
                            } else {
                                Text(
                                    "Abbinato a: ${line.match?.voceTabellaAbbinata} (confidenza ${line.match?.confidenzaAbbinamento})",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "CHO: ${"%.1f".format(line.choContributionG)} g · Grassi: ${"%.1f".format(line.grassiContributionG)} g",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { correctingLineIndex = index }) {
                                Text(if (line.isExcluded) "Cerca manualmente" else "Correggi abbinamento")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            Button(onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Salva")
            }
        }
    }

    val lineIndex = correctingLineIndex
    if (lineIndex != null) {
        ModalBottomSheet(onDismissRequest = { correctingLineIndex = null }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Cerca l'alimento corretto nelle tue tabelle",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                FoodSearchPicker(onSelect = { entry ->
                    viewModel.applyManualMatch(lineIndex, entry)
                    correctingLineIndex = null
                })
            }
        }
    }

    val editIndex = editingLineIndex
    if (editIndex != null) {
        AlertDialog(
            onDismissRequest = { editingLineIndex = null },
            title = { Text("Correggi l'alimento") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingNameText,
                        onValueChange = { editingNameText = it },
                        label = { Text("Nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editingGramsText,
                        onValueChange = { editingGramsText = it },
                        label = { Text("Grammi") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.correctRecognizedFood(editIndex, editingNameText, editingGramsText.toDoubleOrNull())
                    editingLineIndex = null
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { editingLineIndex = null }) { Text("Annulla") }
            },
        )
    }

    if (addingManualFood) {
        ModalBottomSheet(onDismissRequest = { addingManualFood = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Aggiungi un alimento dalle tue tabelle, senza passare dal riconoscimento automatico",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                FoodSearchPicker(onSelect = { entry ->
                    pendingManualEntry = entry
                    pendingManualGramsText = ""
                    addingManualFood = false
                })
            }
        }
    }

    val manualEntry = pendingManualEntry
    if (manualEntry != null) {
        AlertDialog(
            onDismissRequest = { pendingManualEntry = null },
            title = { Text("Quanti grammi di ${manualEntry.nome}?") },
            text = {
                OutlinedTextField(
                    value = pendingManualGramsText,
                    onValueChange = { pendingManualGramsText = it },
                    label = { Text("Grammi") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingManualGramsText.toDoubleOrNull()?.let { grams -> viewModel.addManualFood(manualEntry, grams) }
                    pendingManualEntry = null
                }) { Text("Aggiungi") }
            },
            dismissButton = {
                TextButton(onClick = { pendingManualEntry = null }) { Text("Annulla") }
            },
        )
    }
}

/**
 * Azione "Ricerca online la pietanza mancante" (§Pezzo E) per un alimento senza corrispondenza:
 * mostra il pulsante di avvio, poi lo stato della ricerca (in corso / errore / risultato).
 */
@Composable
private fun OnlineResearchSection(
    state: OnlineResearchUiState?,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        null -> {
            TextButton(onClick = onSearch) { Text("Ricerca online la pietanza mancante") }
        }
        OnlineResearchUiState.Loading -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Ricerca in corso...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
        is OnlineResearchUiState.Error -> {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(state.message, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        }
        is OnlineResearchUiState.Result -> {
            OnlineResearchResultCard(result = state.result, onAdd = onAdd, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun OnlineResearchResultCard(result: OnlineFoodResearchResult, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(result.alimento, fontWeight = FontWeight.Bold)
            Text(
                when (result.affidabilita) {
                    OnlineResearchReliability.SICURA -> "Affidabilità: sicura (fonte ufficiale)"
                    OnlineResearchReliability.BUONA -> "Affidabilità: buona (fonte non ufficiale ma autorevole)"
                    OnlineResearchReliability.ERRORE_DATI -> "Dati non affidabili"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (result.haDatiUtilizzabili) {
                Text(
                    "CHO ${"%.1f".format(result.choPer100g)} g · Grassi ${"%.1f".format(result.grassiPer100g)} g per 100g",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (result.fonteNome.isNotBlank()) {
                    Text(
                        "Fonte: ${result.fonteNome}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(enabled = result.fonteUrl.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.fonteUrl)))
                        },
                    )
                }
            } else {
                Text(
                    result.motivoErrore.ifBlank { "Nessun dato affidabile trovato." },
                    color = Color(0xFFC62828),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                if (result.haDatiUtilizzabili) {
                    Button(onClick = onAdd, modifier = Modifier.padding(end = 8.dp)) { Text("Aggiungi al database") }
                }
                TextButton(onClick = onDismiss) { Text("Ignora") }
            }
        }
    }
}
