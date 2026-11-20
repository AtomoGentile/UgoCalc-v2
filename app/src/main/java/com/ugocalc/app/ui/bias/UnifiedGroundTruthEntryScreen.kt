package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.repository.GroundTruthLevel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.util.parseLocaleDoubleOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Schermata unificata per la registrazione del peso vero: mostra sia le analisi V2 in attesa
 * (che alimentano i fattori BIAS) sia i pasti del benchmark (che alimentano il confronto V1/V2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedGroundTruthEntryScreen(
    onBack: () -> Unit,
    viewModel: UnifiedGroundTruthEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingDeleteV2Id by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registra peso vero") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                StatusBanner(
                    icon = Icons.Default.Info,
                    title = "Calibrazione con bilancia",
                    subtitle = "Pesa gli alimenti di un'analisi per aggiornare i fattori di correzione BIAS e il confronto V1/V2.",
                    tone = BannerTone.INFO,
                )
            }

            if (!uiState.consentGiven) {
                item(key = "consent_warning") {
                    StatusBanner(
                        icon = Icons.Default.Warning,
                        title = "Consenso BIAS disattivato",
                        subtitle = "I pesi verranno salvati ma nessun fattore di correzione verrà calcolato finché non attivi il consenso.",
                        tone = BannerTone.WARNING,
                    )
                }
            }

            if (uiState.loading) {
                item(key = "loading") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.entries.isEmpty()) {
                item(key = "empty") {
                    EmptyStateMessage(
                        icon = Icons.Default.Scale,
                        title = "Nessuna analisi in attesa",
                        message = "Quando completerai nuove analisi, potrai registrare qui il peso reale della bilancia.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            } else {
                items(
                    uiState.entries,
                    key = { entry ->
                        when (entry) {
                            is UnifiedGroundTruthEntry.V2Only -> "v2_${entry.record.id}"
                            is UnifiedGroundTruthEntry.BenchmarkWithOptionalV2 -> "benchmark_${entry.meal.sessionId}"
                        }
                    },
                ) { entry ->
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
                                onRequestDelete = { pendingDeleteV2Id = entry.record.id },
                            )
                    }
                }
            }
        }
    }

    pendingDeleteV2Id?.let { id ->
        UgoConfirmDialog(
            title = "Elimina analisi in attesa",
            message = "Vuoi rimuovere questa analisi dalla coda del peso vero?",
            confirmLabel = "Elimina",
            isDestructive = true,
            onConfirm = {
                pendingDeleteV2Id = null
                viewModel.deleteV2(id)
            },
            onDismiss = { pendingDeleteV2Id = null },
        )
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

    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = dateFormat.format(Date(entry.capturedAtEpochMillis)),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = if (entry.hasV2) "BIAS + V1/V2" else "V1/V2",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = "Modello: ${entry.aiModelId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!entry.hasV2) {
            Text(
                text = "Nessuna analisi V2 in ombra registrata per questo pasto: il peso contribuirà solo al confronto V1/V2.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (entry.nomiAlimenti.isEmpty()) {
            Text(
                text = "Nessun alimento identificato per questo pasto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        GroundTruthLevelSelector(livello, onSelect = { livello = it })

        Button(
            onClick = {
                val pesiG = pesiInput.value.mapNotNull { (nome, raw) ->
                    raw.parseLocaleDoubleOrNull()?.takeIf { it >= 0.0 }?.let { nome to it }
                }.toMap()
                onSubmit(pesiG, livello)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text("Salva peso vero")
        }
    }
}

@Composable
private fun V2OnlyEntryCard(
    entry: UnifiedGroundTruthEntry.V2Only,
    onSubmit: (Map<String, Double>, GroundTruthLevel) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.ITALIAN) }
    var livello by remember { mutableStateOf(GroundTruthLevel.BRONZE) }
    val pesiInput = remember(entry.record.id) {
        mutableStateOf(entry.stime.associate { it.nome to "" })
    }

    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = dateFormat.format(Date(entry.capturedAtEpochMillis)),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "solo BIAS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = "Modello: ${entry.aiModelId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "Analisi precedente al benchmark: il peso vero aggiorna i fattori di correzione BIAS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        entry.stime.forEach { stima ->
            OutlinedTextField(
                value = pesiInput.value[stima.nome].orEmpty(),
                onValueChange = { v -> pesiInput.value = pesiInput.value + (stima.nome to v) },
                label = { Text("${stima.nome} (V2 stima ${stima.pesoCrudoG.toInt()} g crudo)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        GroundTruthLevelSelector(livello, onSelect = { livello = it })

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val pesiG = pesiInput.value.mapNotNull { (nome, raw) ->
                        raw.parseLocaleDoubleOrNull()?.takeIf { it >= 0.0 }?.let { nome to it }
                    }.toMap()
                    onSubmit(pesiG, livello)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Salva peso vero")
            }
            TextButton(
                onClick = onRequestDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Elimina")
            }
        }
    }
}

@Composable
internal fun GroundTruthLevelSelector(current: GroundTruthLevel, onSelect: (GroundTruthLevel) -> Unit) {
    Text(
        text = "Fonte del peso vero",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GroundTruthLevel.entries.forEach { option ->
            FilterChip(
                selected = current == option,
                onClick = { onSelect(option) },
                label = { Text(groundTruthLevelLabel(option), maxLines = 1) },
            )
        }
    }
}

internal fun groundTruthLevelLabel(level: GroundTruthLevel) = when (level) {
    GroundTruthLevel.GOLD -> "Bilancia"
    GroundTruthLevel.SILVER -> "Etichetta"
    GroundTruthLevel.BRONZE -> "Stima mia"
}
