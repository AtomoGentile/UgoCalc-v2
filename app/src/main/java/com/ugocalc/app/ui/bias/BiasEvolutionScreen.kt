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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import java.util.Date
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
    onOpenBenchmarkGroundTruthEntry: () -> Unit,
    onOpenBenchmarkDashboard: () -> Unit,
    onOpenV2Details: (Long) -> Unit,
    viewModel: BiasEvolutionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val fullDateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ITALIAN) }
    val dayDateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.ITALIAN) }

    var selectedBiasCategory by remember { mutableStateOf<String?>(null) }

    if (selectedBiasCategory != null) {
        BiasCorrectionDetailScreen(
            corrections = uiState.corrections,
            recentV2Records = uiState.recentV2Records,
            category = selectedBiasCategory!!,
            onBack = { selectedBiasCategory = null },
            onOpenV2Details = onOpenV2Details
        )
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text("BIAS ed evoluzione del comportamento") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(
                        "UgoCalc può imparare dai tuoi pesi corretti a mano per stimare meglio le porzioni " +
                            "nel tempo (motore di inferenza V2, ancora in sviluppo): confronta i pesi stimati " +
                            "con quelli che correggi tu e, solo quando lo scarto è sistematico e non un caso " +
                            "isolato, calcola un fattore di correzione per quel contesto specifico (modalità " +
                            "Casa/Ristorante, tipo di alimento, modello AI usato). Tutto resta sul telefono: " +
                            "nessun dato lascia il dispositivo per questo scopo.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Consenti l'identificazione del bias", fontWeight = FontWeight.Bold)
                            Text(
                                "Necessario perché il sistema calcoli e salvi questi fattori di correzione.",
                                style = MaterialTheme.typography.bodySmall,
                                color = UgoGrey,
                            )
                        }
                        Switch(checked = uiState.consentGiven, onCheckedChange = { viewModel.setConsentGiven(it) })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Esegui la pipeline V2 in ombra", fontWeight = FontWeight.Bold)
                            Text(
                                "Ad ogni analisi fa girare in background anche il nuovo motore V2, solo per " +
                                    "raccogliere dati di validazione: il risultato che vedi resta sempre quello " +
                                    "del motore attuale. Attiva 2 chiamate AI aggiuntive per analisi, a " +
                                    "pagamento con la tua chiave API.",
                                style = MaterialTheme.typography.bodySmall,
                                color = UgoGrey,
                            )
                        }
                        Switch(checked = uiState.v2ShadowModeEnabled, onCheckedChange = { viewModel.setV2ShadowModeEnabled(it) })
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Motore visibile: forza V2", fontWeight = FontWeight.Bold)
                                Text(
                                    "Forza l'uso della V2 anche se il benchmark non ha ancora raggiunto la soglia di precisione " +
                                        "o il numero minimo di pasti pesati (§13 punto 1).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = UgoGrey,
                                )
                            }
                            Switch(checked = uiState.v2VisibleModeEnabled, onCheckedChange = { viewModel.setV2VisibleModeEnabled(it) })
                        }
                        Text(
                            "Decisione automatica",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            "Il sistema passa da solo alla V2 quando il benchmark (\"Benchmark V1 vs V2\" sotto) mostra che supera la " +
                                "V1 sul CHO, su un numero sufficiente di pasti pesati. Finché non succede, resti " +
                                "sulla V1 (a meno di non forzarla sopra).",
                            style = MaterialTheme.typography.bodySmall,
                            color = UgoGrey,
                        )
                        Text(
                            if (uiState.v2Promoted) "STATO ATTUALE: V2 PROMOSSA" else "STATO ATTUALE: V1 (V2 non ancora pronta)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.v2Promoted) MaterialTheme.colorScheme.primary else UgoGrey,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Button(onClick = onOpenGroundTruthEntry, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text("Registra peso vero di un'analisi")
                    }
                    Button(onClick = onOpenBenchmarkGroundTruthEntry, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Benchmark: registra peso vero di un pasto")
                    }
                    Button(onClick = onOpenBenchmarkDashboard, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Benchmark V1 vs V2")
                    }
                }
                HorizontalDivider()
            }
            // Summary delle correzioni BIAS
            if (uiState.corrections.isNotEmpty()) {
                item(key = "bias_summary") {
                    BiasSummaryHeader(uiState.corrections, onSelectCategory = { selectedBiasCategory = it })
                }
            } else if (!uiState.loading) {
                item {
                    Text(
                        "Nessuna correzione calcolata finora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            // Dropdown Ultime analisi
            if (uiState.recentV2Records.isNotEmpty()) {
                item(key = "recent_v2_dropdown") {
                    UltimeAnalisiDropdown(
                        records = uiState.recentV2Records,
                        onOpenV2Details = onOpenV2Details,
                        onDelete = { viewModel.deleteRecord(it) },
                        dateFormat = fullDateFormat
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

private fun strategyLabel(raw: String) = when (raw) {
    "CONTABILE" -> "Contabile"
    "DISTESA_SUL_PIATTO" -> "Distesa sul piatto"
    "A_CONTENITORE" -> "A contenitore"
    "BLOCCO_MISURABILE" -> "Blocco misurabile"
    "COMPOSTO_OCCLUSO" -> "Composto/occluso"
    "TRASCURABILE" -> "Trascurabile"
    else -> raw
}

@Composable
private fun V2RecordDetailRow(
    record: com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity,
    onOpenDetails: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val failed = record.failureReason != null
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ITALIAN) }

    Card(
        onClick = { if (!failed) onOpenDetails(record.id) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                dateFormat.format(Date(record.analyzedAtEpochMillis)),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Modello: ${record.aiModelId}",
                style = MaterialTheme.typography.bodySmall,
                color = UgoGrey,
            )
            Text(
                "Modalità: ${record.sessionMode}",
                style = MaterialTheme.typography.bodySmall,
                color = UgoGrey,
            )
            if (failed) {
                Text(
                    "❌ Fallita: ${record.failureReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    "✓ Completata — tocca per dettagli",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

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
    recentV2Records: List<com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity> = emptyList(),
    category: String,
    onBack: () -> Unit,
    onOpenV2Details: (Long) -> Unit = {},
) {
    val categoryLabel = when (category) {
        "Applicabile" -> "Categorie Alimentari - Correzioni Attive"
        "BiasInstabile" -> "Categorie Alimentari - Correzioni Instabili"
        "DatiInsufficienti" -> "Misurazioni (Dati Insufficienti)"
        else -> "Correzioni"
    }

    val content = when (category) {
        "DatiInsufficienti" -> {
            // Mostri le misurazioni V2 senza ground truth
            val insufficientRecords = recentV2Records.filter { it.groundTruthPesiJson == null }
            Pair(insufficientRecords.isEmpty(), insufficientRecords as Any)
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
            Pair(grouped.isEmpty(), grouped as Any)
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
        } else if (category == "DatiInsufficienti") {
            // Lista misurazioni
            val records = data as List<*>
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                items(records.size) { index ->
                    val record = records[index] as com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
                    V2RecordDetailRow(record, onOpenV2Details, { /* delete */ })
                    if (index < records.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        } else {
            // Lista categorie con correzioni
            val grouped = data as Map<String, List<BiasCorrectionWithUsageStats>>
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                grouped.forEach { (foodCat, items) ->
                    item {
                        Text(
                            foodCategoryLabel(foodCat),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 0.dp),
                        )
                    }
                    items(items.size) { index ->
                        BiasCorrectionDetailItem(items[index])
                        if (index < items.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
    onSelectCategory: (String) -> Unit,
) {
    val active = corrections.count { it.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.Applicabile }
    val unstable = corrections.count { it.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.BiasInstabile }
    val insufficient = corrections.count { it.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.DatiInsufficienti }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Riepilogo correzioni BIAS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusBadgeButton(
                    label = "Attivi",
                    count = active,
                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                    onClick = { onSelectCategory("Applicabile") }
                )
                StatusBadgeButton(
                    label = "Instabili",
                    count = unstable,
                    color = MaterialTheme.colorScheme.error,
                    onClick = { onSelectCategory("BiasInstabile") }
                )
                StatusBadgeButton(
                    label = "Insufficienti",
                    count = insufficient,
                    color = UgoGrey,
                    onClick = { onSelectCategory("DatiInsufficienti") }
                )
            }
            if (corrections.isNotEmpty()) {
                Text(
                    "Tocca per dettagli",
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
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.padding(8.dp)) {
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
private fun UltimeAnalisiDropdown(
    records: List<com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity>,
    onOpenV2Details: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    dateFormat: SimpleDateFormat,
) {
    var expanded by androidx.compose.runtime.mutableStateOf(false)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column {
            androidx.compose.material3.TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(
                    "Ultime analisi (${records.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
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
    androidx.compose.material3.TextButton(
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

