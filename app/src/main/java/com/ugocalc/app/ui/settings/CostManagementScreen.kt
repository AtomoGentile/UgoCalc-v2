package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.ugocalc.app.data.local.db.entity.CostTransactionEntity
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostManagementScreen(
    onBack: () -> Unit,
    viewModel: CostManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val summary = uiState.summary
    val dateFormat = SimpleDateFormat("d MMM HH:mm", Locale.ITALIAN)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione Costi") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        }
    ) { padding ->
        if (uiState.loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Caricamento...", style = MaterialTheme.typography.bodyMedium, color = UgoGrey)
            }
        } else if (summary == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nessun dato disponibile", style = MaterialTheme.typography.bodyMedium, color = UgoGrey)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Text(
                        "Riepilogo Spese",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }

                // Card riepilogo totale
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Costo Totale", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "$${String.format(Locale.US, "%.2f", uiState.totalCostUsd)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                "${summary.transactionCount} transazioni",
                                style = MaterialTheme.typography.bodySmall,
                                color = UgoGrey,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                // Card costo mese corrente
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Costo Questo Mese", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "$${String.format(Locale.US, "%.2f", uiState.monthCostUsd)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                // Breakdown per stage
                if (summary.costByStage.isNotEmpty()) {
                    item {
                        Text(
                            "Costo per Fase",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    items(summary.costByStage.toList()) { (stage, cost) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stageDisplayName(stage), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "$${String.format(Locale.US, "%.2f", cost)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Breakdown per modello
                if (summary.costByModel.isNotEmpty()) {
                    item {
                        Text(
                            "Costo per Modello",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    items(summary.costByModel.toList()) { (modelId, cost) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(modelId, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "$${String.format(Locale.US, "%.2f", cost)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Transazioni recenti
                if (summary.recentTransactions.isNotEmpty()) {
                    item {
                        Text(
                            "Transazioni Recenti",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    items(summary.recentTransactions) { transaction ->
                        CostTransactionCard(transaction, dateFormat)
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    ) {
                        Text("Cancella Storico")
                    }
                }
            }
        }
    }
}

@Composable
private fun CostTransactionCard(transaction: CostTransactionEntity, dateFormat: SimpleDateFormat) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transaction.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        dateFormat.format(Date(transaction.transactionAtEpochMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = UgoGrey,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    "$${String.format(Locale.US, "%.4f", transaction.costUsd)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun stageDisplayName(stage: String): String = when (stage) {
    "vision" -> "Vision API"
    "inference_v1" -> "Inference V1"
    "inference_v2" -> "Inference V2"
    "matching" -> "Matching"
    "nutrition" -> "Nutrition"
    else -> stage
}
