package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.util.parseLocaleDoubleOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registrazione del peso vero per un pasto del benchmark V1/V2 (piano V2 §10, §13 punto 5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkGroundTruthEntryScreen(
    onBack: () -> Unit,
    viewModel: BenchmarkGroundTruthEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Benchmark: registra peso vero") },
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
                    icon = Icons.Default.CompareArrows,
                    title = "Confronto V1 vs V2",
                    subtitle = "Pesa gli alimenti di un pasto analizzato per confrontare la precisione dei due motori nella dashboard.",
                    tone = BannerTone.INFO,
                )
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
            } else if (uiState.pending.isEmpty()) {
                item(key = "empty") {
                    EmptyStateMessage(
                        icon = Icons.Default.Scale,
                        title = "Nessun pasto in attesa",
                        message = "Non ci sono pasti del benchmark in attesa di peso vero.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            } else {
                items(uiState.pending, key = { it.entity.sessionId }) { entry ->
                    PendingMealCard(
                        entry = entry,
                        onSubmit = { pesiG, livello -> viewModel.submit(entry.entity.sessionId, pesiG, livello) },
                    )
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

    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = dateFormat.format(Date(entry.entity.capturedAtEpochMillis)),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Modello: ${entry.entity.aiModelId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (entry.nomiAlimenti.isEmpty()) {
            Text(
                text = "Nessun alimento ancora identificato per questo pasto (l'analisi V2 potrebbe non essere completata).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        entry.nomiAlimenti.forEach { nome ->
            OutlinedTextField(
                value = pesiInput.value[nome].orEmpty(),
                onValueChange = { value -> pesiInput.value = pesiInput.value + (nome to value) },
                label = { Text(nome) },
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
