package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.repository.BiasCorrectionUsageStats
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
            if (uiState.corrections.isEmpty()) {
                item {
                    Text(
                        if (uiState.loading) "Caricamento..." else "Nessuna correzione calcolata finora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                item(key = "fattori_appresi_header") {
                    Text(
                        "Fattori appresi",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                items(
                    items = uiState.corrections,
                    key = { "${it.correction.sessionMode}_${it.correction.strategy}_${it.correction.aiModelId}_${it.correction.foodCategory}" }
                ) { correction ->
                    BiasCorrectionRow(correction, dayDateFormat)
                    HorizontalDivider()
                }
            }

            if (uiState.recentV2Records.isNotEmpty()) {
                item(key = "recent_v2_header") {
                    Text(
                        "Ultime analisi V2",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                }
                items(
                    items = uiState.recentV2Records,
                    key = { it.id }
                ) { record ->
                    V2RecordSummaryRow(record, onOpenV2Details, onDelete = { viewModel.deleteRecord(record.id) }, fullDateFormat)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun V2RecordSummaryRow(
    record: com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity,
    onClick: (Long) -> Unit,
    onDelete: () -> Unit,
    dateFormat: SimpleDateFormat,
) {
    Card(
        onClick = { onClick(record.id) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${dateFormat.format(Date(record.analyzedAtEpochMillis))} - ${record.sessionMode}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Modello: ${record.aiModelId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = UgoGrey
                )
                Text(
                    "Tocca per vedere il dettaglio tecnico e il ragionamento AI",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            }
            androidx.compose.material3.IconButton(onClick = onDelete) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Elimina",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BiasCorrectionRow(row: BiasCorrectionWithUsageStats, dateFormat: SimpleDateFormat) {
    val correction = row.correction
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${sessionModeLabel(correction.sessionMode)} - ${foodCategoryLabel(correction.foodCategory)} (${strategyLabel(correction.strategy)})",
                fontWeight = FontWeight.Bold,
            )
            Text("Modello: ${correction.aiModelId}", style = MaterialTheme.typography.bodySmall)

            when (val result = row.calculationResult) {
                is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.Applicabile -> {
                    Text(
                        "Fattore ${formatFactor(correction.factor)}x su ${correction.sampleCount} correzioni - aggiornato il ${dateFormat.format(Date(correction.updatedAtEpochMillis))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.DatiInsufficienti -> {
                    Text(
                        "STATO: CORREZIONE NON ATTIVA (Dati insufficienti)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = UgoGrey,
                    )
                    Text(
                        "Campione: ${result.campione}/${com.ugocalc.app.domain.inference.m8.BiasCorrectionCalculator.MIN_SAMPLE_COUNT} necessari. Il sistema inizierà a correggere questo segmento solo dopo aver raccolto abbastanza pesi veri.",
                        style = MaterialTheme.typography.bodySmall,
                        color = UgoGrey,
                    )
                }
                is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.BiasInstabile -> {
                    Text(
                        "STATO: CORREZIONE DISATTIVATA (Bias instabile)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Concordanza: ${(result.concordanza * 100).toInt()}% (minimo ${(com.ugocalc.app.domain.inference.m8.BiasCorrectionCalculator.MIN_SIGN_AGREEMENT * 100).toInt()}%). " +
                                "Gli errori in questo segmento non hanno una direzione costante (alcuni sovrastevano, altri sottostimavano), quindi un fattore fisso peggiorerebbe la precisione.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Sotto questa soglia il fattore è comunque "Applicabile" per il calcolatore (§8), ma un
            // campione così piccolo per una categoria specifica può anche segnalare che il
            // riconoscimento AI la incontra raramente — un avviso utile, non solo una statistica.
            if (row.calculationResult is com.ugocalc.app.domain.inference.m8.BiasCorrectionResult.Applicabile && correction.sampleCount < LOW_SAMPLE_WARNING_THRESHOLD) {
                Text(
                    "Campione ridotto per questa categoria (${correction.sampleCount} osservazioni): il fattore potrebbe non essere ancora affidabile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            row.usageStats?.let { stats ->
                BiasUsageStatsBlock(stats)
            }
        }
    }
}

@Composable
private fun BiasUsageStatsBlock(stats: BiasCorrectionUsageStats) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            "Statistiche d'uso (${stats.observationCount} osservazioni)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Pesi medi: stimato ${formatGrams(stats.meanEstimatedWeightG)} -> corretto ${formatGrams(stats.meanCorrectedWeightG)} (vero ${formatGrams(stats.meanGroundTruthWeightG)})",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Correzione stimata: ${formatSignedGrams(stats.meanAppliedCorrectionG)} per alimento",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Errore medio: ${formatSignedGrams(stats.meanErrorG)} - MAE ${formatGrams(stats.meanAbsoluteErrorG)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Errore quadratico medio: ${formatNumber(stats.meanSquaredErrorG2)} g^2 - RMSE ${formatGrams(stats.rootMeanSquaredErrorG)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Dopo correzione stimata: errore medio ${formatSignedGrams(stats.correctedMeanErrorG)} - MAE ${formatGrams(stats.correctedMeanAbsoluteErrorG)} - RMSE ${formatGrams(stats.correctedRootMeanSquaredErrorG)}",
            style = MaterialTheme.typography.bodySmall,
            color = UgoGrey,
        )
    }
}

/** Soglia puramente informativa in UI (§14): ben sopra il minimo di [com.ugocalc.app.domain.inference.m8.BiasCorrectionCalculator.MIN_SAMPLE_COUNT] richiesto per calcolare un fattore, per segnalare quando una categoria resta comunque poco osservata. */
private const val LOW_SAMPLE_WARNING_THRESHOLD = 10

private fun formatFactor(value: Double) = String.format(Locale.ITALIAN, "%.2f", value)

private fun formatNumber(value: Double) = String.format(Locale.ITALIAN, "%.1f", value)

private fun formatGrams(value: Double) = "${formatNumber(value)} g"

private fun formatSignedGrams(value: Double): String {
    val prefix = if (value >= 0.0) "+" else ""
    return "$prefix${formatGrams(value)}"
}

private fun sessionModeLabel(raw: String) = when (raw) {
    "CASA" -> "Casa"
    "RISTORANTE" -> "Ristorante"
    else -> raw
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
