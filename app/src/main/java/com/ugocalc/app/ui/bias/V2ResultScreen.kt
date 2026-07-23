package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.inference.model.CalculationChain
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey

/**
 * Dettaglio tecnico della stima V2 (piano V2 §13.1): mostra la catena di calcolo completa
 * per ogni alimento, inclusi i valori geometrici e le fonti dei dati fisici.
 * Trasparenza totale su come il motore è arrivato ai grammi finali.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2ResultScreen(
    onBack: () -> Unit,
    viewModel: V2ResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dettaglio Tecnico V2") },
                navigationIcon = { BackIconButton(onBack) },
                actions = {
                    androidx.compose.material3.IconButton(onClick = { viewModel.deleteRecord(onDeleted = onBack) }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Elimina analisi",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = ugoTopAppBarColors()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    "Spiegazione deterministica del calcolo del peso effettuato dal Motore Fisico (M3). " +
                            "Ogni valore numerico deriva da misure visive (M1) o tabelle di densità locali.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                if (uiState.reasoning.isNotEmpty()) {
                    Text(
                        "RAGIONAMENTO AI:\n${uiState.reasoning}",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                HorizontalDivider()
            }

            if (uiState.v2Stime.isEmpty()) {
                item {
                    Text(
                        "Nessun dato V2 disponibile per questa sessione.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(uiState.v2Stime) { stima ->
                    V2WeightEstimateCard(stima, onUpdateDensity = viewModel::updateDensity)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun V2WeightEstimateCard(
    stima: WeightEstimate,
    onUpdateDensity: (String, Double) -> Unit
) {
    var showDensityDialog by remember { mutableStateOf(false) }
    var newDensityText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stima.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (stima.ragionamento.isNotEmpty()) {
                Text(
                    stima.ragionamento,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = UgoGrey,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Text(
                "Strategia: ${stima.strategia.name}",
                style = MaterialTheme.typography.bodySmall,
                color = UgoGrey
            )
            Text(
                "Peso stimato: ${stima.pesoCrudoG.toInt()} g crudo (${stima.pesoG.toInt()} g cotto — Range: ${stima.intervallo.minG.toInt()}-${stima.intervallo.maxG.toInt()} g crudo)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            CalculationChainBlock(stima.catenaCalcolo)

            if (stima.usoDefaultFisico) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val currentDensity = stima.catenaCalcolo.valori["densità"] ?: 1.0
                        newDensityText = currentDensity.toString()
                        showDensityDialog = true
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("⚠️ Densità di default: inserisci valore reale", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDensityDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDensityDialog = false },
            title = { Text("Correggi densità per ${stima.nome}") },
            text = {
                Column {
                    Text(
                        "Inserisci la densità in g/cm³ (es. 0.82 per riso). Questo valore verrà salvato e usato per le prossime analisi di questo alimento.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newDensityText,
                        onValueChange = { newDensityText = it },
                        label = { Text("Densità (g/cm³)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    newDensityText.toDoubleOrNull()?.let { density ->
                        onUpdateDensity(stima.nome, density)
                    }
                    showDensityDialog = false
                }) { Text("Salva") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDensityDialog = false }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun CalculationChainBlock(chain: CalculationChain) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text("Catena di calcolo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(
            "Formula: ${chain.formula}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
        
        Text("Valori:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        chain.valori.forEach { (nome, valore) ->
            Text("• $nome: $valore", style = MaterialTheme.typography.bodySmall)
        }

        Text("Fonti:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        chain.fonti.forEach { fonte ->
            Text("• ${fonte.fattore}: ${fonte.valore} (${fonte.fonte})", style = MaterialTheme.typography.bodySmall)
        }
        
        Text(
            "Sintesi: ${chain.sintesi}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
