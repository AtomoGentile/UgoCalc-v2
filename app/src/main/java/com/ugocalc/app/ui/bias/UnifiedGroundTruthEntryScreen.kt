package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
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
 * Schermata unificata per la registrazione del peso vero: mostra sia le analisi V2 in attesa
 * (che alimentano i fattori BIAS) sia i pasti del benchmark (che alimentano il confronto V1/V2).
 * Le vecchie analisi V2 senza benchmark associato compaiono comunque — non vanno perse,
 * semplicemente non contribuiscono al confronto V1/V2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedGroundTruthEntryScreen(
    onBack: () -> Unit,
    viewModel: UnifiedGroundTruthEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registra peso vero") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Text(
                    "Pesa con la bilancia gli alimenti di un'analisi e registra il peso reale. " +
                        "Ogni misurazione aggiorna i fattori di correzione interni (BIAS) e, " +
                        "se l'analisi appartiene al benchmark, contribuisce anche al confronto V1/V2.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                if (!uiState.consentGiven) {
                    Text(
                        "Il consenso all'identificazione del bias non è attivo: i pesi vengono salvati " +
                            "ma nessun fattore di correzione viene calcolato finché non lo attivi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = UgoGrey,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                HorizontalDivider()
            }

            if (uiState.loading) {
                item {
                    Text(
                        "Caricamento...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else if (uiState.entries.isEmpty()) {
                item {
                    Text(
                        "Nessuna analisi in attesa di peso vero.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(uiState.entries, key = { it.sessionId ?: it.capturedAtEpochMillis.toString() + it.aiModelId }) { entry ->
                    when (entry) {
                        is UnifiedGroundTruthEntry.BenchmarkWithOptionalV2 ->
                            BenchmarkEntryCard(
                                entry = entry,
                                onSubmit = { pesiG, livello ->
                                    viewModel.submitBenchmark(entry.meal.sessionId, pesiG, livello)
                                },
                            )
                        is UnifiedGroundTruthEntry.V2Only ->
                            V2OnlyEntryCard(
                                entry = entry,
                                onSubmit = { pesiG, livello ->
                                    viewModel.submitV2(entry.record.id, pesiG, livello)
                                },
                                onDelete = { viewModel.deleteV2(entry.record.id) },
                            )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun BenchmarkEntryCard(
    entry: UnifiedGroundTruthEntry.BenchmarkWithOptionalV2,
    onSubmit: (Map<String, Double>, GroundTruthLevel) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.ITALIAN) }
    var livello by remember { mutableStateOf(GroundTruthLevel.BRONZE) }
    val pesiInput = remember(entry.meal.sessionId) {
        mutableStateOf(entry.nomiAlimenti.associateWith { "" })
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    dateFormat.format(Date(entry.capturedAtEpochMillis)),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        if (entry.hasV2) "BIAS + V1/V2" else "V1/V2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text("Modello: ${entry.aiModelId}", style = MaterialTheme.typography.bodySmall, color = UgoGrey)
            if (!entry.hasV2) {
                Text(
                    "Nessuna analisi V2 in ombra registrata per questo pasto: il peso contribuirà solo al confronto V1/V2.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UgoGrey,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (entry.nomiAlimenti.isEmpty()) {
                Text(
                    "Nessun alimento identificato per questo pasto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UgoGrey,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            entry.nomiAlimenti.forEach { nome ->
                val stimaNote = entry.stime.firstOrNull { it.nome == nome }
                    ?.let { " (V2 stima ${it.pesoCrudoG.toInt()} g crudo)" } ?: ""
                OutlinedTextField(
                    value = pesiInput.value[nome].orEmpty(),
                    onValueChange = { v -> pesiInput.value = pesiInput.value + (nome to v) },
                    label = { Text("$nome$stimaNote") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            GroundTruthLevelSelector(livello, onSelect = { livello = it })

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

@Composable
private fun V2OnlyEntryCard(
    entry: UnifiedGroundTruthEntry.V2Only,
    onSubmit: (Map<String, Double>, GroundTruthLevel) -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.ITALIAN) }
    var livello by remember { mutableStateOf(GroundTruthLevel.BRONZE) }
    val pesiInput = remember(entry.record.id) {
        mutableStateOf(entry.stime.associate { it.nome to "" })
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    dateFormat.format(Date(entry.capturedAtEpochMillis)),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        "solo BIAS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("Modello: ${entry.aiModelId}", style = MaterialTheme.typography.bodySmall, color = UgoGrey)
            Text(
                "Analisi precedente al benchmark: il peso vero aggiorna i fattori di correzione BIAS ma non contribuisce al confronto V1/V2.",
                style = MaterialTheme.typography.bodySmall,
                color = UgoGrey,
                modifier = Modifier.padding(top = 4.dp),
            )

            entry.stime.forEach { stima ->
                OutlinedTextField(
                    value = pesiInput.value[stima.nome].orEmpty(),
                    onValueChange = { v -> pesiInput.value = pesiInput.value + (stima.nome to v) },
                    label = { Text("${stima.nome} (V2 stima ${stima.pesoCrudoG.toInt()} g crudo)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            GroundTruthLevelSelector(livello, onSelect = { livello = it })

            Row(modifier = Modifier.padding(top = 12.dp)) {
                Button(
                    onClick = {
                        val pesiG = pesiInput.value.mapNotNull { (nome, raw) ->
                            raw.trim().replace(',', '.').toDoubleOrNull()?.let { nome to it }
                        }.toMap()
                        onSubmit(pesiG, livello)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Salva peso vero")
                }
                androidx.compose.material3.TextButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(start = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text("Elimina")
                }
            }
        }
    }
}

@Composable
private fun GroundTruthLevelSelector(current: GroundTruthLevel, onSelect: (GroundTruthLevel) -> Unit) {
    Text(
        "Fonte del peso vero",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    Row {
        GroundTruthLevel.entries.forEach { option ->
            FilterChip(
                selected = current == option,
                onClick = { onSelect(option) },
                label = { Text(groundTruthLevelLabel(option)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

private fun groundTruthLevelLabel(level: GroundTruthLevel) = when (level) {
    GroundTruthLevel.GOLD -> "Bilancia"
    GroundTruthLevel.SILVER -> "Etichetta"
    GroundTruthLevel.BRONZE -> "Stima mia"
}
