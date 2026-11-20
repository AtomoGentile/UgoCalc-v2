package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.local.db.entity.CostTransactionEntity
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.MacroMetricTile
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
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
    val dateFormat = remember { SimpleDateFormat("d MMM HH:mm", Locale.ITALIAN) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione Costi") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        if (uiState.loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (summary == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyStateMessage(
                    icon = Icons.Default.Payments,
                    title = "Nessun dato disponibile",
                    message = "Le spese API appariranno qui dopo le prime analisi.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "cost_summary") {
                    UgoSectionHeader(
                        title = "Riepilogo spese",
                        subtitle = "${summary.transactionCount} transazioni registrate",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MacroMetricTile(
                            label = "Costo Totale",
                            value = "$${String.format(Locale.US, "%.4f", uiState.totalCostUsd)}",
                            unit = "USD",
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        MacroMetricTile(
                            label = "Questo Mese",
                            value = "$${String.format(Locale.US, "%.4f", uiState.monthCostUsd)}",
                            unit = "USD",
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                val allModels = summary.costByModel.keys.toList().sorted()
                if (allModels.isNotEmpty()) {
                    item(key = "model_filter") {
                        UgoCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            UgoSectionHeader(title = "Filtra per modello")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cerca modello…") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Cancella")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val filteredModels = if (searchQuery.isBlank()) {
                                allModels
                            } else {
                                allModels.filter { modelId ->
                                    val displayName = AiModelCatalog.ALL
                                        .find { it.id == modelId }?.displayName ?: modelId
                                    displayName.contains(searchQuery, ignoreCase = true) ||
                                        modelId.contains(searchQuery, ignoreCase = true)
                                }
                            }

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (searchQuery.isBlank()) {
                                    item {
                                        FilterChip(
                                            selected = uiState.selectedModel == null,
                                            onClick = { viewModel.selectModel(null) },
                                            label = { Text("Tutti", maxLines = 1) },
                                        )
                                    }
                                }
                                items(filteredModels) { modelId ->
                                    val displayName = AiModelCatalog.ALL
                                        .find { it.id == modelId }?.displayName ?: modelId
                                    FilterChip(
                                        selected = uiState.selectedModel == modelId,
                                        onClick = { viewModel.selectModel(modelId) },
                                        label = { Text(displayName, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.selectedModel == null) {
                    val modelsWithCost = summary.costByModel.values
                        .filter { it.totalCost > 0 }
                        .sortedByDescending { it.totalCost }

                    if (modelsWithCost.isNotEmpty()) {
                        item(key = "per_model_breakdown") {
                            UgoCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(14.dp),
                            ) {
                                UgoSectionHeader(title = "Riepilogo per modello")
                                Spacer(modifier = Modifier.height(8.dp))
                                modelsWithCost.forEachIndexed { index, breakdown ->
                                    val displayName = AiModelCatalog.ALL
                                        .find { it.id == breakdown.modelId }?.displayName ?: breakdown.modelId
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        )
                                        Text(
                                            "$${String.format(Locale.US, "%.4f", breakdown.totalCost)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (index < modelsWithCost.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val breakdown = summary.costByModel[uiState.selectedModel]
                    if (breakdown != null) {
                        item(key = "selected_model_breakdown") {
                            val displayName = AiModelCatalog.ALL
                                .find { it.id == breakdown.modelId }?.displayName ?: breakdown.modelId
                            UgoCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(14.dp),
                            ) {
                                UgoSectionHeader(title = "Dettaglio $displayName")
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Costo totale modello", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "$${String.format(Locale.US, "%.4f", breakdown.totalCost)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (breakdown.costByStage.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                    breakdown.costByStage.forEach { (stage, cost) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                stageDisplayName(stage),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                "$${String.format(Locale.US, "%.4f", cost)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val filteredTransactions = if (uiState.selectedModel == null) {
                    summary.recentTransactions
                } else {
                    summary.recentTransactions.filter { it.modelId == uiState.selectedModel }
                }

                if (filteredTransactions.isNotEmpty()) {
                    item(key = "tx_header") {
                        UgoSectionHeader(
                            title = "Transazioni recenti",
                            subtitle = uiState.selectedModel,
                        )
                    }
                    items(filteredTransactions, key = { it.id }) { transaction ->
                        CostTransactionCard(transaction, dateFormat)
                    }
                }

                if (summary.transactionCount > 0) {
                    item(key = "clear_history_btn") {
                        Button(
                            onClick = { showClearConfirm = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("Cancella storico costi")
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        UgoConfirmDialog(
            title = "Cancella storico costi",
            message = "Vuoi eliminare tutte le transazioni e il riepilogo spese registrato?",
            confirmLabel = "Elimina tutto",
            isDestructive = true,
            onConfirm = {
                showClearConfirm = false
                viewModel.clearHistory()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun CostTransactionCard(transaction: CostTransactionEntity, dateFormat: SimpleDateFormat) {
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = stageDisplayName(transaction.stage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (!transaction.sessionId.isNullOrBlank()) {
                    Text(
                        text = "Sessione: ${transaction.sessionId.takeLast(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = dateFormat.format(Date(transaction.transactionAtEpochMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = "$${String.format(Locale.US, "%.4f", transaction.costUsd)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun stageDisplayName(stage: String): String = when (stage) {
    "vision" -> "Vision API (V1)"
    "perception" -> "Percezione (M1)"
    "inference_v1" -> "Inference V1"
    "inference_v2" -> "Inference V2"
    "matching" -> "Matching (M6)"
    "nutrition" -> "Nutrition (M7)"
    "arbitration" -> "Arbitrato (M5)"
    "normalization" -> "Normalizzazione"
    "online_research" -> "Ricerca Online"
    else -> stage
}
