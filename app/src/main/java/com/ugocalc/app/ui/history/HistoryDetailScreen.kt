package com.ugocalc.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.MacronutrientPieChart
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/** Dettaglio espandibile per ciascun alimento (§2.4 punto 6), riaperto da una voce di Cronologia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    onBack: () -> Unit,
    onOpenV2Details: (Long) -> Unit,
    viewModel: HistoryDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(uiState.foodTitle) }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CHO totali: ${"%.1f".format(uiState.totalChoG)} g", style = MaterialTheme.typography.titleLarge)
                    Text("Grassi totali: ${"%.1f".format(uiState.totalGrassiG)} g", style = MaterialTheme.typography.titleLarge)
                    val macroSum = uiState.totalChoG + uiState.totalGrassiG
                    val choPercent = if (macroSum > 0) uiState.totalChoG / macroSum * 100.0 else 0.0
                    MacronutrientPieChart(
                        choPercent = choPercent,
                        grassiPercent = if (macroSum > 0) 100.0 - choPercent else 0.0,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (uiState.modelNames.isNotEmpty()) {
                        Text(
                            "Modelli: ${uiState.modelNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    uiState.v2Record?.let { record ->
                        androidx.compose.material3.Button(
                            onClick = { onOpenV2Details(record.id) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text("Mostra analisi V2 (Shadow)")
                        }
                    }
                }
            }

            Text("Dettaglio", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            LazyColumn {
                items(uiState.lines) { line ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("${line.recognizedFood.nome} — ${line.recognizedFood.pesoStimatoG.toInt()}g", fontWeight = FontWeight.Bold)
                        if (line.isExcluded) {
                            Text("Escluso dal totale: nessuna corrispondenza in tabella", color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                "CHO: ${"%.1f".format(line.choContributionG)} g · Grassi: ${"%.1f".format(line.grassiContributionG)} g",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
