package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey
import java.util.Locale

/**
 * Dashboard di confronto V1 vs V2 (piano V2 §10, §12, §13 punto 5): cresce man mano che si
 * registrano pasti pesati con [BenchmarkGroundTruthEntryScreen]. Diagnostica soltanto — non
 * decide mai automaticamente quale motore usare, la decisione resta dell'owner una volta
 * raccolti abbastanza dati (§13 punto 1, sospeso in attesa di questo benchmark).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkDashboardScreen(onBack: () -> Unit, viewModel: BenchmarkDashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Benchmark V1 vs V2") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Text(
                    "Confronto diagnostico tra il motore attuale (V1) e il nuovo motore V2, sugli stessi " +
                        "pasti a cui hai registrato un peso vero. Non decide da solo quale motore usare: " +
                        "serve solo a capire, con dati reali, se e quando conviene passare a V2.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    "Pasti raccolti: ${uiState.mealCount} (consigliati ${uiState.recommendedMinMeals}-${uiState.recommendedMaxMeals})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                HorizontalDivider()
            }
            if (uiState.loading) {
                item { Text("Caricamento...", style = MaterialTheme.typography.bodyMedium, color = UgoGrey, modifier = Modifier.padding(vertical = 16.dp)) }
            } else if (uiState.mealCount == 0) {
                item {
                    Text(
                        "Nessun pasto con peso vero ancora registrato. Usa \"Benchmark: registra peso vero\" " +
                            "dopo aver pesato un pasto con la bilancia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                item {
                    EngineStatsCard("Motore attuale (V1)", uiState.v1)
                    EngineStatsCard("Motore nuovo (V2)", uiState.v2)
                }
            }
        }
    }
}

@Composable
private fun EngineStatsCard(titolo: String, stats: EngineStats) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(titolo, fontWeight = FontWeight.Bold)
            Text(
                "Errore medio peso per alimento: ${formatGrams(stats.weightMaeG)} (${stats.weightSampleCount} alimenti pesati)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "CHO entro 10 g: ${formatPercent(stats.choWithin10gPercent)} - entro 20 g: ${formatPercent(stats.choWithin20gPercent)} (${stats.choMealCount} pasti confrontabili)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatGrams(value: Double?) = if (value == null) "n/d" else "${String.format(Locale.ITALIAN, "%.1f", value)} g"
private fun formatPercent(value: Double?) = if (value == null) "n/d" else "${String.format(Locale.ITALIAN, "%.0f", value)}%"
