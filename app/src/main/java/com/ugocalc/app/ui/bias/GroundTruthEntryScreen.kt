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
 * Registrazione del peso vero per un'analisi V2 già salvata (piano V2 §7, §8, §13 punto 3): l'unico
 * punto dell'app da cui l'utente può chiudere il ciclo di apprendimento, associando un peso pesato o
 * dichiarato a un record di M8. Finché non ci sono analisi V2 in ombra registrate, la lista resta
 * vuota — stato onesto, non un errore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundTruthEntryScreen(onBack: () -> Unit, viewModel: GroundTruthEntryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Registra peso vero") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Text(
                    "Confronta il peso stimato dal motore V2 con quello reale (bilancia, etichetta o una " +
                        "tua stima) per gli alimenti di un'analisi già registrata: è così che i fattori di " +
                        "correzione in \"BIAS ed evoluzione del comportamento\" vengono calcolati.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                if (!uiState.consentGiven) {
                    Text(
                        "Il consenso all'identificazione del bias non è attivo: puoi comunque registrare il " +
                            "peso vero, ma non verrà calcolato alcun fattore finché non lo attivi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = UgoGrey,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                HorizontalDivider()
            }
            if (uiState.pending.isEmpty()) {
                item {
                    Text(
                        if (uiState.loading) "Caricamento..." else "Nessuna analisi V2 in attesa di conferma peso.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(uiState.pending, key = { it.record.id }) { entry ->
                    PendingRecordCard(entry, onSubmit = { pesiG, livello -> viewModel.submit(entry.record.id, pesiG, livello) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PendingRecordCard(
    entry: PendingGroundTruthRecord,
    onSubmit: (Map<String, Double>, GroundTruthLevel) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.ITALIAN) }
    var livello by remember { mutableStateOf(GroundTruthLevel.BRONZE) }
    val pesiInput = remember(entry.record.id) { mutableStateOf(entry.stime.associate { it.nome to "" }) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(dateFormat.format(Date(entry.record.analyzedAtEpochMillis)), fontWeight = FontWeight.Bold)
            Text("Modello: ${entry.record.aiModelId}", style = MaterialTheme.typography.bodySmall, color = UgoGrey)

            entry.stime.forEach { stima ->
                OutlinedTextField(
                    value = pesiInput.value[stima.nome].orEmpty(),
                    onValueChange = { value -> pesiInput.value = pesiInput.value + (stima.nome to value) },
                    label = { Text("${stima.nome} (stimato ${stima.pesoG.toInt()} g)") },
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
