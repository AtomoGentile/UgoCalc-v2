package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.repository.GroundTruthLevel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registrazione del peso vero per un pasto del benchmark V1/V2 (piano V2 §10, §13 punto 5): a
 * differenza di [GroundTruthEntryScreen] (che alimenta solo la calibrazione interna di V2), il peso
 * registrato qui confronta l'errore di entrambi i motori sullo stesso pasto nella dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkGroundTruthEntryScreen(onBack: () -> Unit, viewModel: BenchmarkGroundTruthEntryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Benchmark: registra peso vero") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Text(
                    "Pesa con la bilancia (o dichiara un peso stimato) gli alimenti di un pasto già " +
                        "analizzato: serve a confrontare l'errore del motore attuale e del nuovo motore V2 " +
                        "sullo stesso pasto, nella schermata \"Benchmark V1/V2\".",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                HorizontalDivider()
            }
            if (uiState.pending.isEmpty()) {
                item {
                    Text(
                        if (uiState.loading) "Caricamento..." else "Nessun pasto in attesa di peso vero.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(uiState.pending, key = { it.entity.sessionId }) { entry ->
                    PendingMealCard(entry, onSubmit = { pesiG, livello -> viewModel.submit(entry.entity.sessionId, pesiG, livello) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PendingMealCard(
    entry: PendingBenchmarkMeal,
    onSubmit: (Map<String, Double>, GroundTruthLevel) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.ITALIAN) }
    var livello by remember { mutableStateOf(GroundTruthLevel.BRONZE) }
    val pesiInput = remember(entry.entity.sessionId) { mutableStateOf(entry.nomiAlimenti.associateWith { "" }) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(dateFormat.format(Date(entry.entity.capturedAtEpochMillis)), fontWeight = FontWeight.Bold)
            Text("Modello: ${entry.entity.aiModelId}", style = MaterialTheme.typography.bodySmall, color = UgoGrey)

            if (entry.nomiAlimenti.isEmpty()) {
                Text(
                    "Nessun alimento ancora identificato per questo pasto (l'analisi V2 potrebbe non essere completata).",
                    style = MaterialTheme.typography.bodySmall,
                    color = UgoGrey,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            entry.nomiAlimenti.forEach { nome ->
                OutlinedTextField(
                    value = pesiInput.value[nome].orEmpty(),
                    onValueChange = { value -> pesiInput.value = pesiInput.value + (nome to value) },
                    label = { Text(nome) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            Text("Fonte del peso vero", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Row {
                GroundTruthLevel.entries.forEach { option ->
                    FilterChip(
                        selected = livello == option,
                        onClick = { livello = option },
                        label = { Text(groundTruthLevelLabel(option)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Button(
                onClick = {
                    val pesiG = pesiInput.value.mapNotNull { (nome, raw) ->
                        raw.trim().replace(',', '.').toDoubleOrNull()?.let { nome to it }
                    }.toMap()
                    onSubmit(pesiG, livello)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text("Salva peso vero")
            }
        }
    }
}

private fun groundTruthLevelLabel(level: GroundTruthLevel) = when (level) {
    GroundTruthLevel.GOLD -> "Bilancia"
    GroundTruthLevel.SILVER -> "Etichetta"
    GroundTruthLevel.BRONZE -> "Stima mia"
}
