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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.repository.EngineStats
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.MacroMetricTile
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors
import java.util.Locale

/**
 * Dashboard di confronto V1 vs V2 (piano V2 §10, §12, §13 punto 5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkDashboardScreen(
    onBack: () -> Unit,
    viewModel: BenchmarkDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Benchmark V1 vs V2") },
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
            item(key = "promotion_banner") {
                StatusBanner(
                    icon = if (uiState.v2Promoted) Icons.Default.Science else Icons.Default.CompareArrows,
                    title = if (uiState.v2Promoted) {
                        "Motore in produzione: V2"
                    } else {
                        "Motore in produzione: V1"
                    },
                    subtitle = if (uiState.v2Promoted) {
                        "V2 ha superato V1 sul CHO entro il 10%."
                    } else {
                        "V2 non ancora migliore o dati insufficienti (${uiState.mealCount}/${uiState.recommendedMinMeals}–${uiState.recommendedMaxMeals} pasti)."
                    },
                    tone = if (uiState.v2Promoted) BannerTone.SUCCESS else BannerTone.INFO,
                )
            }

            item(key = "summary_card") {
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Text(
                        text = "Confronto diagnostico tra il motore attuale (V1) e il nuovo motore V2 sugli stessi pasti pesati.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pasti raccolti: ${uiState.mealCount} (consigliati ${uiState.recommendedMinMeals}–${uiState.recommendedMaxMeals})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
            } else if (uiState.mealCount == 0) {
                item(key = "empty") {
                    EmptyStateMessage(
                        icon = Icons.Default.Scale,
                        title = "Nessun pasto pesato",
                        message = "Usa \"Registra peso vero\" dopo aver pesato un pasto con la bilancia per confrontare V1 e V2.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            } else {
                item(key = "v1_stats") {
                    EngineStatsCard("Motore attuale (V1)", uiState.v1)
                }
                item(key = "v2_stats") {
                    EngineStatsCard("Motore nuovo (V2)", uiState.v2)
                }
            }
        }
    }
}

@Composable
private fun EngineStatsCard(titolo: String, stats: EngineStats) {
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        UgoSectionHeader(
            title = titolo,
            subtitle = "${stats.weightSampleCount} alimenti pesati · ${stats.choMealCount} pasti confrontabili",
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroMetricTile(
                label = "MAE peso",
                value = formatGramsValue(stats.weightMaeG),
                unit = "g",
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            MacroMetricTile(
                label = "CHO ≤ 10%",
                value = formatPercentValue(stats.choWithin10gPercent),
                unit = "%",
                accentColor = UgoSemanticColors.cho,
                modifier = Modifier.weight(1f),
            )
            MacroMetricTile(
                label = "CHO ≤ 20%",
                value = formatPercentValue(stats.choWithin20gPercent),
                unit = "%",
                accentColor = UgoSemanticColors.fat,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun formatGramsValue(value: Double?) = String.format(Locale.ITALIAN, "%.1f", value ?: 0.0)
private fun formatPercentValue(value: Double?) = String.format(Locale.ITALIAN, "%.0f", value ?: 0.0)
