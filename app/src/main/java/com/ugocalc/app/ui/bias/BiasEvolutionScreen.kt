package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.repository.BiasCorrectionWithUsageStats
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Menu laterale > BIAS ed evoluzione del comportamento (§ richiesta utente): consenso al
 * trattamento dei dati per l'identificazione del bias (piano V2 §8) più la trasparenza su cosa il
 * sistema ha effettivamente imparato finora, segmento per segmento (modalità, strategia, modello
 * di percezione) — mai un'aggregazione cross-modello (§8 "Nessuna mescolanza tra modelli, mai").
 *
 * Finché la pipeline V2 non è collegata al flusso reale dell'app, questa lista resta vuota: non è
 * un errore, è lo stato onesto di un sistema che non ha ancora ground truth su cui imparare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiasEvolutionScreen(
    onBack: () -> Unit,
    onOpenGroundTruthEntry: () -> Unit,
    onOpenBenchmarkDashboard: () -> Unit,
    onOpenV2Details: (Long) -> Unit,
    viewModel: BiasEvolutionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val fullDateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ITALIAN) }

    var selectedBiasCategory by remember { mutableStateOf<String?>(null) }

    if (selectedBiasCategory != null) {
        BiasCorrectionDetailScreen(
            corrections = uiState.corrections,
            category = selectedBiasCategory!!,
            onBack = { selectedBiasCategory = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BIAS ed evoluzione del comportamento") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors()
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

            // ── Sezione 1: impostazioni motore V2 ────────────────────────────
            item(key = "settings_section") {
                BiasSection(title = "Motore V2") {
                    SettingsRow(
                        title = "Consenso identificazione bias",
                        subtitle = "Permette al sistema di calcolare e salvare i fattori di correzione.",
                        checked = uiState.consentGiven,
                        onCheckedChange = { viewModel.setConsentGiven(it) },
                    )
                    SettingsRow(
                        title = "Pipeline V2 in ombra",
                        subtitle = "Esegue V2 in background ad ogni analisi per raccogliere dati. Attiva 2 chiamate AI aggiuntive a pagamento.",
                        checked = uiState.v2ShadowModeEnabled,
                        onCheckedChange = { viewModel.setV2ShadowModeEnabled(it) },
                    )
                    SettingsRow(
                        title = "Forza V2 visibile",
                        subtitle = "Usa V2 come motore principale anche prima che il benchmark lo promuova automaticamente.",
                        checked = uiState.v2VisibleModeEnabled,
                        onCheckedChange = { viewModel.setV2VisibleModeEnabled(it) },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .padding(horizontal = 4.dp),
                    ) {
                        Text(
                            "Motore attivo:",
                            style = MaterialTheme.typography.bodySmall,
                            color = UgoGrey,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            if (uiState.v2Promoted) "V2" else "V1",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.v2Promoted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ── Sezione 2: azioni ────────────────────────────────────────────
            item(key = "actions_section") {
                BiasSection(title = "Misurazioni") {
                    Button(
                        onClick = onOpenGroundTruthEntry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Registra peso vero")
                    }
                    Button(
                        onClick = onOpenBenchmarkDashboard,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Benchmark V1 vs V2")
                    }
                }
            }

            // ── Sezione 3: filtro modello ────────────────────────────────────
            if (uiState.availableModels.isNotEmpty()) {
                item(key = "model_selector") {
                    BiasSection(title = "Dati per modello") {
                        ModelSelectorChipBias(
                            models = uiState.availableModels,
                            selectedModel = uiState.selectedModelId,
                            onModelSelected = { viewModel.selectModel(it) },
                        )
                    }
                }
            }

            // ── Sezione 4: riepilogo correzioni BIAS ─────────────────────────
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
                    Text(
                        "Nessuna correzione calcolata finora. Registra il peso vero di alcune analisi per iniziare.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            // ── Sezione 5: ultime analisi V2 ─────────────────────────────────
            if (uiState.allV2Records.isNotEmpty()) {
                item(key = "recent_v2_dropdown") {
                    UltimeAnalisiDropdown(
                        records = uiState.recentV2Records,
                        totalRecords = uiState.allV2Records.size,
                        displayedCount = uiState.recentV2Records.size,
                        onOpenV2Details = onOpenV2Details,
                        onDelete = { viewModel.deleteRecord(it) },
                        onLoadMore = { viewModel.loadMoreRecords() },
                        dateFormat = fullDateFormat,
                    )
                }
            }
        }
    }
}

private fun formatFactor(value: Double) = String.format(Locale.ITALIAN, "%.2f", value)

private fun formatNumber(value: Double) = String.format(Locale.ITALIAN, "%.1f", value)

private fun formatGrams(value: Double) = "${formatNumber(value)} g"

private fun formatSignedGrams(value: Double): String {
    val prefix = if (value >= 0.0) "+" else ""
    return "$prefix${formatGrams(value)}"
}

@Composable
private fun BiasSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = UgoGrey,
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = UgoGrey)
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
    "PANE", "PIZZA_FOCACCIA", "DOLCI", "TRASCURABILE", "ALTRO_BLOCCO"
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

    val content = when (category) {
        "DatiInsufficienti" -> {
            // Correzioni con dati insufficienti: pesi già registrati ma < 5 campioni per categoria
            val filtered = corrections.filter { it.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.DatiInsufficienti }
            val grouped = filtered.groupBy { it.correction.foodCategory }
            val allCategories = ALL_FOOD_CATEGORIES.associateWith { cat -> grouped[cat] ?: emptyList() }
            Pair(filtered.isEmpty(), allCategories as Any)
        }
        else -> {
            // Raggruppa per categoria alimentare
            val filtered = corrections.filter { correction ->
                when (category) {
                    "Applicabile" -> correction.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.Applicabile
                    "BiasInstabile" -> correction.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.BiasInstabile
                    else -> false
                }
            }
            val grouped = filtered.groupBy { it.correction.foodCategory }
            // Estendi con tutte le categorie, anche quelle vuote
            val allCategories = ALL_FOOD_CATEGORIES.associateWith { cat ->
                grouped[cat] ?: emptyList()
            }
            Pair(filtered.isEmpty(), allCategories as Any)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryLabel) },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors()
            )
        }
    ) { padding ->
        val (isEmpty, data) = content

        if (isEmpty) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nessun elemento in questa categoria", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            // Lista categorie con correzioni (incluse vuote)
            val grouped = data as Map<String, List<BiasCorrectionWithUsageStats>>
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                grouped.forEach { (foodCat, items) ->
                    item {
                        Text(
                            if (items.isEmpty()) "${foodCategoryLabel(foodCat)} (0)" else foodCategoryLabel(foodCat),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 0.dp),
                            color = if (items.isEmpty()) UgoGrey else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (items.isNotEmpty()) {
                        items(items.size) { index ->
                            BiasCorrectionDetailItem(items[index])
                            if (index < items.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BiasCorrectionDetailItem(row: BiasCorrectionWithUsageStats) {
    val correction = row.correction
    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            "${foodCategoryLabel(correction.foodCategory)} (${strategyLabel(correction.strategy)})",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Modello: ${correction.aiModelId}",
            style = MaterialTheme.typography.bodySmall,
            color = UgoGrey,
        )

        when (val result = row.calculationResult) {
            is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.Applicabile -> {
                Text(
                    "✓ ATTIVO — Fattore ${formatFactor(correction.factor)}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Su ${correction.sampleCount} correzioni",
                    style = MaterialTheme.typography.labelSmall,
                    color = UgoGrey,
                )
            }
            is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.BiasInstabile -> {
                Text(
                    "✗ INSTABILE — Concordanza ${(result.concordanza * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Minimo richiesto: ${(com.ugocalc.app.domain.inference.m8.BiasCorrectionCalculator.MIN_SIGN_AGREEMENT * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = UgoGrey,
                )
            }
            is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.DatiInsufficienti -> {
                Text(
                    "⊘ INSUFFICIENTE — ${result.campione}/${com.ugocalc.app.domain.inference.m8.BiasCorrectionCalculator.MIN_SAMPLE_COUNT}",
                    style = MaterialTheme.typography.bodySmall,
                    color = UgoGrey,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        row.usageStats?.let { stats ->
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "Statistiche: ${stats.observationCount} osservazioni",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = UgoGrey,
                )
                Text(
                    "Errore: MAE ${formatGrams(stats.meanAbsoluteErrorG)} | RMSE ${formatGrams(stats.rootMeanSquaredErrorG)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = UgoGrey,
                )
            }
        }
    }
}

@Composable
private fun BiasSummaryHeader(
    corrections: List<BiasCorrectionWithUsageStats>,
    recentV2Records: List<com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity> = emptyList(),
    onSelectCategory: (String) -> Unit,
) {
    val active = remember(corrections) { corrections.count { it.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.Applicabile } }
    val unstable = remember(corrections) { corrections.count { it.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.BiasInstabile } }
    val inMaturation = remember(corrections) { corrections.count { it.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.DatiInsufficienti } }
    val pendingWeight = remember(recentV2Records) { recentV2Records.count { it.groundTruthPesiJson == null } }

    // Top active factors for quick preview
    val activeFactors = remember(corrections) {
        corrections
            .filter { it.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.Applicabile }
            .sortedByDescending { (it.correction.sampleCount) }
            .take(4)
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Riepilogo correzioni BIAS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // 3 stati delle correzioni
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusBadgeButton(
                    label = "Attivi",
                    count = active,
                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                    onClick = { onSelectCategory("Applicabile") }
                )
                StatusBadgeButton(
                    label = "In maturazione",
                    count = inMaturation,
                    color = androidx.compose.ui.graphics.Color(0xFFF9A825),
                    onClick = { onSelectCategory("DatiInsufficienti") }
                )
                StatusBadgeButton(
                    label = "Instabili",
                    count = unstable,
                    color = MaterialTheme.colorScheme.error,
                    onClick = { onSelectCategory("BiasInstabile") }
                )
            }

            // Fattori attivi in preview (visibili senza dover toccare)
            if (activeFactors.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Fattori attivi:",
                    style = MaterialTheme.typography.labelSmall,
                    color = UgoGrey,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                activeFactors.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            foodCategoryLabel(row.correction.foodCategory),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${formatFactor(row.correction.factor)}×",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                        )
                    }
                }
                if (active > 4) {
                    Text(
                        "… altri ${active - 4} — tocca Attivi per la lista completa",
                        style = MaterialTheme.typography.labelSmall,
                        color = UgoGrey,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Flusso spiegato quando non c'è ancora nulla
            if (active == 0 && inMaturation == 0 && unstable == 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Come funziona: pesa gli alimenti e registra il peso reale tramite \"Registra peso vero\". " +
                        "Dopo ≥5 misurazioni per la stessa categoria alimentare il fattore di correzione diventa Attivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UgoGrey,
                )
            } else if (inMaturation > 0 && active == 0) {
                Text(
                    "Correzioni in crescita: servono ancora misurazioni per raggiungere il minimo di 5 per categoria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UgoGrey,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Analisi in attesa di peso reale (informativo, non un badge di correzioni)
            if (pendingWeight > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "$pendingWeight analisi V2 in attesa di peso reale — tocca \"Registra peso vero\" per pesarle.",
                    style = MaterialTheme.typography.labelSmall,
                    color = UgoGrey,
                )
            }

            if (active > 0 || inMaturation > 0 || unstable > 0) {
                Text(
                    "Tocca i badge per i dettagli",
                    style = MaterialTheme.typography.labelSmall,
                    color = UgoGrey,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBadgeButton(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.padding(8.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = UgoGrey,
            )
        }
    }
}

@Composable
private fun ModelSelectorChipBias(
    models: List<com.ugocalc.app.domain.model.AiModel>,
    selectedModel: String?,
    onModelSelected: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedModel == null,
                    onClick = { onModelSelected(null) },
                    label = { Text("Tutti") }
                )
            }
            items(models, key = { it.id }) { model ->
                val onClick = remember(model.id) { { onModelSelected(model.id) } }
                FilterChip(
                    selected = selectedModel == model.id,
                    onClick = onClick,
                    label = { Text(if (model.subtitle.isNotEmpty()) "${model.displayName} (${model.subtitle})" else model.displayName) }
                )
            }
        }
    }
}

@Composable
private fun UltimeAnalisiDropdown(
    records: List<com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity>,
    totalRecords: Int,
    displayedCount: Int,
    onOpenV2Details: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onLoadMore: () -> Unit,
    dateFormat: SimpleDateFormat,
) {
    var expanded by remember(totalRecords) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(
                    "Ultime analisi ($displayedCount/$totalRecords)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    textAlign = TextAlign.Start,
                )
                androidx.compose.material3.Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Espandi",
                )
            }

            if (expanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(8.dp)) {
                    records.forEach { record ->
                        V2RecordMinimalRow(record, onOpenV2Details, onDelete, dateFormat)
                    }
                    if (displayedCount < totalRecords) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text("Carica altri (${totalRecords - displayedCount} rimanenti)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V2RecordMinimalRow(
    record: com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity,
    onClick: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    dateFormat: SimpleDateFormat,
) {
    val failed = record.failureReason != null
    TextButton(
        onClick = { if (!failed) onClick(record.id) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                dateFormat.format(java.util.Date(record.analyzedAtEpochMillis)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                record.sessionMode,
                style = MaterialTheme.typography.labelSmall,
                color = UgoGrey,
            )
        }
        androidx.compose.material3.IconButton(
            onClick = { onDelete(record.id) },
            modifier = Modifier.padding(0.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Elimina",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

