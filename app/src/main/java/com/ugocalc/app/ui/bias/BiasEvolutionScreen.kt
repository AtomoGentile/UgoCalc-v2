package com.ugocalc.app.ui.bias

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.data.repository.BiasCorrectionWithUsageStats
import com.ugocalc.app.domain.inference.m8.BiasCorrectionCalculator
import com.ugocalc.app.domain.inference.m8.BiasCorrectionResult
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiasEvolutionScreen(
    onBack: () -> Unit,
    onOpenGroundTruthEntry: () -> Unit,
    onOpenBenchmarkDashboard: () -> Unit,
    onOpenV2Details: (Long) -> Unit,
    showBackButton: Boolean = true,
    viewModel: BiasEvolutionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val fullDateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ITALIAN) }
    var selectedBiasCategory by remember { mutableStateOf<String?>(null) }
    var pendingDeleteRecord by remember { mutableStateOf<V2AnalysisRecordEntity?>(null) }

    if (selectedBiasCategory != null) {
        BiasCorrectionDetailScreen(
            corrections = uiState.corrections,
            category = selectedBiasCategory!!,
            onBack = { selectedBiasCategory = null },
        )
        return
    }

    com.ugocalc.app.ui.components.EditorialDetailScaffold(
        title = "BIAS ed evoluzione",
        onBack = if (showBackButton) onBack else null,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "engine_status") {
                StatusBanner(
                    icon = Icons.Default.Science,
                    title = if (uiState.v2Promoted) "Motore attivo: V2" else "Motore attivo: V1",
                    subtitle = if (uiState.v2Promoted) {
                        "Il nuovo motore fisico V2 è visibile come motore principale."
                    } else {
                        "V1 è il motore visibile; V2 può raccogliere dati in ombra per apprendere i bias."
                    },
                    tone = if (uiState.v2Promoted) BannerTone.SUCCESS else BannerTone.INFO,
                )
            }

            item(key = "settings_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UgoSectionHeader(title = "Motore V2 e consenso")
                    UgoCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        SettingsRow(
                            title = "Consenso identificazione bias",
                            subtitle = "Permette al sistema di calcolare e salvare i fattori di correzione.",
                            checked = uiState.consentGiven,
                            onCheckedChange = { viewModel.setConsentGiven(it) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        SettingsRow(
                            title = "Pipeline V2 in ombra",
                            subtitle = "Esegue V2 in background ad ogni analisi per raccogliere dati.",
                            checked = uiState.v2ShadowModeEnabled,
                            onCheckedChange = { viewModel.setV2ShadowModeEnabled(it) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        SettingsRow(
                            title = "Forza V2 visibile",
                            subtitle = "Usa V2 come motore principale anche prima della promozione automatica.",
                            checked = uiState.v2VisibleModeEnabled,
                            onCheckedChange = { viewModel.setV2VisibleModeEnabled(it) },
                        )
                    }
                }
            }

            item(key = "actions_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UgoSectionHeader(title = "Misurazioni e confronto")
                    UgoCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Button(
                            onClick = onOpenGroundTruthEntry,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Scale, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Registra peso vero")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenBenchmarkDashboard,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Benchmark V1 vs V2")
                        }
                    }
                }
            }

            if (uiState.availableModels.isNotEmpty()) {
                item(key = "model_selector") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UgoSectionHeader(title = "Dati per modello")
                        ModelSelectorChipBias(
                            models = uiState.availableModels,
                            selectedModel = uiState.selectedModelId,
                            onModelSelected = { viewModel.selectModel(it) },
                        )
                    }
                }
            }

            if (uiState.corrections.isNotEmpty() || uiState.allV2Records.isNotEmpty()) {
                item(key = "bias_summary") {
                    BiasSummaryHeader(
                        corrections = uiState.corrections,
                        recentV2Records = uiState.allV2Records,
                        onSelectCategory = { selectedBiasCategory = it },
                    )
                }
            } else if (!uiState.loading) {
                item(key = "bias_empty") {
                    EmptyStateMessage(
                        icon = Icons.Default.Insights,
                        title = "Nessuna correzione calcolata",
                        message = "Registra il peso vero di alcune analisi per iniziare a calcolare i fattori di correzione.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }
            }

            if (uiState.allV2Records.isNotEmpty()) {
                item(key = "recent_v2_dropdown") {
                    UltimeAnalisiDropdown(
                        records = uiState.recentV2Records,
                        totalRecords = uiState.allV2Records.size,
                        displayedCount = uiState.recentV2Records.size,
                        onOpenV2Details = onOpenV2Details,
                        onRequestDelete = { pendingDeleteRecord = it },
                        onLoadMore = { viewModel.loadMoreRecords() },
                        dateFormat = fullDateFormat,
                    )
                }
            }
        }
    }

    pendingDeleteRecord?.let { record ->
        UgoConfirmDialog(
            title = "Elimina record V2",
            message = "Vuoi eliminare l'analisi V2 del ${fullDateFormat.format(Date(record.analyzedAtEpochMillis))}?",
            confirmLabel = "Elimina",
            isDestructive = true,
            onConfirm = {
                pendingDeleteRecord = null
                viewModel.deleteRecord(record.id)
            },
            onDismiss = { pendingDeleteRecord = null },
        )
    }
}

private fun formatFactor(value: Double) = String.format(Locale.ITALIAN, "%.2f", value)
private fun formatNumber(value: Double) = String.format(Locale.ITALIAN, "%.1f", value)
private fun formatGrams(value: Double) = "${formatNumber(value)} g"

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun strategyLabel(raw: String) = when (raw) {
    "CONTABILE" -> "Contabile"
    "DISTESA_SUL_PIATTO" -> "Distesa sul piatto"
    "A_CONTENITORE" -> "A contenitore"
    "BLOCCO_MISURABILE" -> "Blocco misurabile"
    "COMPOSTO_OCCLUSO" -> "Composto/occluso"
    "TRASCURABILE" -> "Trascurabile"
    else -> raw
}

private val ALL_FOOD_CATEGORIES = listOf(
    "PIATTI_COMPOSTI", "BEVANDE", "ZUPPE_MINESTRE", "CONTABILE", "PASTA",
    "RISO_E_CEREALI", "PATATE", "VERDURE_FOGLIA", "VERDURE_COMPATTE", "LEGUMI",
    "FRUTTA", "CARNE", "PESCE_FILETTO", "MOLLUSCHI_GUSCIO", "FORMAGGIO",
    "PANE", "PIZZA_FOCACCIA", "DOLCI", "TRASCURABILE", "ALTRO_BLOCCO",
)

private fun foodCategoryLabel(raw: String) = when (raw) {
    "PIATTI_COMPOSTI" -> "Piatti composti"
    "BEVANDE" -> "Bevande"
    "ZUPPE_MINESTRE" -> "Zuppe e minestre"
    "CONTABILE" -> "Contabile"
    "PASTA" -> "Pasta"
    "RISO_E_CEREALI" -> "Riso e cereali"
    "PATATE" -> "Patate"
    "VERDURE_FOGLIA" -> "Verdure a foglia"
    "VERDURE_COMPATTE" -> "Verdure compatte"
    "LEGUMI" -> "Legumi"
    "FRUTTA" -> "Frutta"
    "CARNE" -> "Carne"
    "PESCE_FILETTO" -> "Pesce (filetto)"
    "MOLLUSCHI_GUSCIO" -> "Molluschi con guscio"
    "FORMAGGIO" -> "Formaggio"
    "PANE" -> "Pane"
    "PIZZA_FOCACCIA" -> "Pizza/Focaccia"
    "DOLCI" -> "Dolci"
    "TRASCURABILE" -> "Trascurabile"
    "ALTRO_BLOCCO" -> "Altro blocco"
    else -> raw
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BiasCorrectionDetailScreen(
    corrections: List<BiasCorrectionWithUsageStats>,
    category: String,
    onBack: () -> Unit,
) {
    val categoryLabel = when (category) {
        "Applicabile" -> "Correzioni BIAS attive"
        "BiasInstabile" -> "Correzioni BIAS instabili"
        "DatiInsufficienti" -> "Correzioni in maturazione"
        else -> "Correzioni"
    }

    val filtered = remember(corrections, category) {
        corrections.filter { row ->
            when (category) {
                "Applicabile" -> row.calculationResult is BiasCorrectionResult.Applicabile
                "BiasInstabile" -> row.calculationResult is BiasCorrectionResult.BiasInstabile
                "DatiInsufficienti" -> row.calculationResult is BiasCorrectionResult.DatiInsufficienti
                else -> false
            }
        }
    }
    val grouped = remember(filtered) {
        val byCat = filtered.groupBy { it.correction.foodCategory }
        ALL_FOOD_CATEGORIES.associateWith { cat -> byCat[cat].orEmpty() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryLabel) },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyStateMessage(
                    icon = Icons.Default.Insights,
                    title = "Nessun elemento",
                    message = "Non ci sono correzioni in questa categoria.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (foodCat, items) ->
                    item(key = "cat_$foodCat") {
                        Text(
                            text = if (items.isEmpty()) "${foodCategoryLabel(foodCat)} (0)" else foodCategoryLabel(foodCat),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (items.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    if (items.isNotEmpty()) {
                        items(items, key = { "${foodCat}_${it.correction.aiModelId}_${it.correction.strategy}" }) { row ->
                            UgoCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(12.dp),
                            ) {
                                BiasCorrectionDetailItem(row)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiasCorrectionDetailItem(row: BiasCorrectionWithUsageStats) {
    val correction = row.correction
    Column {
        Text(
            text = "${foodCategoryLabel(correction.foodCategory)} (${strategyLabel(correction.strategy)})",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Modello: ${correction.aiModelId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))

        when (val result = row.calculationResult) {
            is BiasCorrectionResult.Applicabile -> {
                MacroPill(
                    label = "Attivo",
                    value = "${formatFactor(correction.factor)}× (${correction.sampleCount} campioni)",
                    containerColor = UgoSemanticColors.successContainer,
                    contentColor = UgoSemanticColors.onSuccessContainer,
                )
            }
            is BiasCorrectionResult.BiasInstabile -> {
                MacroPill(
                    label = "Instabile",
                    value = "${(result.concordanza * 100).toInt()}% (min ${(BiasCorrectionCalculator.MIN_SIGN_AGREEMENT * 100).toInt()}%)",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            is BiasCorrectionResult.DatiInsufficienti -> {
                MacroPill(
                    label = "In crescita",
                    value = "${result.campione}/${BiasCorrectionCalculator.MIN_SAMPLE_COUNT}",
                    containerColor = UgoSemanticColors.warningContainer,
                    contentColor = UgoSemanticColors.onWarningContainer,
                )
            }
        }

        row.usageStats?.let { stats ->
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Statistiche: ${stats.observationCount} osservazioni",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Errore: MAE ${formatGrams(stats.meanAbsoluteErrorG)} · RMSE ${formatGrams(stats.rootMeanSquaredErrorG)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BiasSummaryHeader(
    corrections: List<BiasCorrectionWithUsageStats>,
    recentV2Records: List<V2AnalysisRecordEntity> = emptyList(),
    onSelectCategory: (String) -> Unit,
) {
    val active = remember(corrections) { corrections.count { it.calculationResult is BiasCorrectionResult.Applicabile } }
    val unstable = remember(corrections) { corrections.count { it.calculationResult is BiasCorrectionResult.BiasInstabile } }
    val inMaturation = remember(corrections) { corrections.count { it.calculationResult is BiasCorrectionResult.DatiInsufficienti } }
    val pendingWeight = remember(recentV2Records) { recentV2Records.count { it.groundTruthPesiJson == null } }

    val activeFactors = remember(corrections) {
        corrections
            .filter { it.calculationResult is BiasCorrectionResult.Applicabile }
            .sortedByDescending { it.correction.sampleCount }
            .take(4)
    }

    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        UgoSectionHeader(title = "Riepilogo correzioni BIAS")
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBadgeButton(
                label = "Attivi",
                count = active,
                color = UgoSemanticColors.success,
                modifier = Modifier.weight(1f),
                onClick = { onSelectCategory("Applicabile") },
            )
            StatusBadgeButton(
                label = "In crescita",
                count = inMaturation,
                color = UgoSemanticColors.warning,
                modifier = Modifier.weight(1f),
                onClick = { onSelectCategory("DatiInsufficienti") },
            )
            StatusBadgeButton(
                label = "Instabili",
                count = unstable,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
                onClick = { onSelectCategory("BiasInstabile") },
            )
        }

        if (activeFactors.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = "Fattori attivi:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            activeFactors.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        text = foodCategoryLabel(row.correction.foodCategory),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${formatFactor(row.correction.factor)}×",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = UgoSemanticColors.success,
                    )
                }
            }
            if (active > 4) {
                Text(
                    text = "… altri ${active - 4} — tocca Attivi per la lista completa",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (active == 0 && inMaturation == 0 && unstable == 0) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = "Pesa gli alimenti e registra il peso reale tramite \"Registra peso vero\". Dopo almeno 5 misurazioni per la stessa categoria il fattore diventa Attivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (inMaturation > 0 && active == 0) {
            Text(
                text = "Correzioni in crescita: servono almeno 5 misurazioni per categoria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (pendingWeight > 0) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = "$pendingWeight analisi V2 in attesa di peso reale — tocca \"Registra peso vero\".",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBadgeButton(
    label: String,
    count: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ModelSelectorChipBias(
    models: List<AiModel>,
    selectedModel: String?,
    onModelSelected: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedModel == null,
                onClick = { onModelSelected(null) },
                label = { Text("Tutti", maxLines = 1) },
            )
        }
        items(models, key = { it.uid }) { model ->
            FilterChip(
                selected = selectedModel == model.id,
                onClick = { onModelSelected(model.id) },
                label = {
                    Text(
                        if (model.subtitle.isNotEmpty()) "${model.displayName} (${model.subtitle})" else model.displayName,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun UltimeAnalisiDropdown(
    records: List<V2AnalysisRecordEntity>,
    totalRecords: Int,
    displayedCount: Int,
    onOpenV2Details: (Long) -> Unit,
    onRequestDelete: (V2AnalysisRecordEntity) -> Unit,
    onLoadMore: () -> Unit,
    dateFormat: SimpleDateFormat,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    UgoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ultime analisi ($displayedCount/$totalRecords)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Espandi",
            )
        }

        if (expanded) {
            HorizontalDivider()
            Column(modifier = Modifier.padding(8.dp)) {
                records.forEach { record ->
                    V2RecordMinimalRow(
                        record = record,
                        onClick = onOpenV2Details,
                        onRequestDelete = { onRequestDelete(record) },
                        dateFormat = dateFormat,
                    )
                }
                if (displayedCount < totalRecords) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = onLoadMore,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) {
                        Text("Carica altri (${totalRecords - displayedCount} rimanenti)")
                    }
                }
            }
        }
    }
}

@Composable
private fun V2RecordMinimalRow(
    record: V2AnalysisRecordEntity,
    onClick: (Long) -> Unit,
    onRequestDelete: () -> Unit,
    dateFormat: SimpleDateFormat,
) {
    val failed = record.failureReason != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !failed) { onClick(record.id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = dateFormat.format(Date(record.analyzedAtEpochMillis)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = record.sessionMode,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRequestDelete) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Elimina",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
