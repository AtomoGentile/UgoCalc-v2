package com.ugocalc.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.MacroMetricTile
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.MacronutrientPieChart
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.formatMealShareSummary
import com.ugocalc.app.ui.components.shareText
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val detailDateFormat = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.ITALIAN)

/** Dettaglio espandibile per ciascun alimento (§2.4 punto 6), riaperto da una voce di Cronologia. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryDetailScreen(
    onBack: () -> Unit,
    onOpenV2Details: (Long) -> Unit,
    viewModel: HistoryDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val macroSum = uiState.totalChoG + uiState.totalGrassiG
    val choPercent = if (macroSum > 0) uiState.totalChoG / macroSum * 100.0 else 0.0
    val grassiPercent = if (macroSum > 0) 100.0 - choPercent else 0.0
    val kcalEstimate = (uiState.totalChoG * 4.0 + uiState.totalGrassiG * 9.0).toInt()
    val isRistorante = uiState.sessionMode == SessionMode.RISTORANTE.name
    val canShare = uiState.lines.isNotEmpty() || uiState.foodTitle.isNotBlank() ||
        uiState.totalChoG > 0.0 || uiState.totalGrassiG > 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.foodTitle.ifBlank { "Dettaglio pasto" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
                actions = {
                    if (canShare) {
                        IconButton(
                            onClick = {
                                val mealTitle = uiState.foodTitle.ifBlank { "Pasto UgoCalc" }
                                val dateText = uiState.analyzedAtEpochMillis?.let { detailDateFormat.format(Date(it)) }
                                val modeText = uiState.sessionMode.takeIf { it.isNotBlank() }?.let {
                                    if (isRistorante) "Ristorante" else "Casa"
                                }
                                val summary = formatMealShareSummary(
                                    title = mealTitle,
                                    totalChoG = uiState.totalChoG,
                                    totalGrassiG = uiState.totalGrassiG,
                                    lines = uiState.lines,
                                    estimatedKcal = kcalEstimate,
                                    sessionModeLabel = modeText,
                                    dateLabel = dateText,
                                )
                                shareText(context, mealTitle, summary)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Condividi riepilogo pasto",
                            )
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Elimina analisi",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero_nutrition") {
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        uiState.analyzedAtEpochMillis?.let { millis ->
                            Text(
                                text = detailDateFormat.format(Date(millis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (uiState.sessionMode.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isRistorante) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isRistorante) Icons.Default.Restaurant else Icons.Default.Home,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = if (isRistorante) "Ristorante" else "Casa",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    MacronutrientPieChart(
                        choPercent = choPercent,
                        grassiPercent = grassiPercent,
                        choGrams = uiState.totalChoG,
                        grassiGrams = uiState.totalGrassiG,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MacroMetricTile(
                            label = "Carboidrati",
                            value = "%.1f".format(uiState.totalChoG),
                            unit = "g",
                            accentColor = UgoSemanticColors.cho,
                            modifier = Modifier.weight(1f),
                        )
                        MacroMetricTile(
                            label = "Grassi",
                            value = "%.1f".format(uiState.totalGrassiG),
                            unit = "g",
                            accentColor = UgoSemanticColors.fat,
                            modifier = Modifier.weight(1f),
                        )
                        MacroMetricTile(
                            label = "kcal (C+G)",
                            value = kcalEstimate.toString(),
                            unit = "kcal",
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (uiState.modelNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            uiState.modelNames.forEach { modelName ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = modelName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    uiState.v2Record?.let { record ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { onOpenV2Details(record.id) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Mostra analisi V2 (Shadow)")
                        }
                    }
                }
            }

            item(key = "breakdown_header") {
                UgoSectionHeader(
                    title = "Dettaglio alimenti",
                    subtitle = "${uiState.lines.size} elementi analizzati",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            itemsIndexed(uiState.lines, key = { idx, line -> "${idx}_${line.recognizedFood.nome}" }) { _, line ->
                HistoryFoodLineCard(line = line)
            }
        }
    }

    if (showDeleteConfirm) {
        UgoConfirmDialog(
            title = "Elimina analisi",
            message = "Vuoi eliminare \"${uiState.foodTitle.ifBlank { "questa analisi" }}\" dalla cronologia?",
            confirmLabel = "Elimina",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteEntry(onDeleted = onBack)
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryFoodLineCard(line: CalculatedFoodLine) {
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = line.recognizedFood.nome,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "${line.recognizedFood.pesoStimatoG.toInt()} g",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }

        if (line.isExcluded) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusBanner(
                icon = Icons.Default.Warning,
                title = "Escluso dal totale",
                subtitle = "Nessuna corrispondenza trovata in tabella",
                tone = BannerTone.ERROR,
            )
        } else {
            line.tableEntry?.nome?.takeIf { !it.equals(line.recognizedFood.nome, ignoreCase = true) }?.let { matchedName ->
                Text(
                    text = "Tabella: $matchedName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MacroPill(
                    label = "CHO",
                    value = "${"%.1f".format(line.choContributionG)} g",
                    containerColor = UgoSemanticColors.choContainer,
                    contentColor = UgoSemanticColors.onChoContainer,
                )
                MacroPill(
                    label = "Grassi",
                    value = "${"%.1f".format(line.grassiContributionG)} g",
                    containerColor = UgoSemanticColors.fatContainer,
                    contentColor = UgoSemanticColors.onFatContainer,
                )
            }
        }
    }
}
