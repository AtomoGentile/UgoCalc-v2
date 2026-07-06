package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.ugocalc.app.data.local.db.entity.BiasCorrectionEntity
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
fun BiasEvolutionScreen(onBack: () -> Unit, viewModel: BiasEvolutionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

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
                item {
                    Text(
                        "Fattori appresi",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                items(uiState.corrections) { correction ->
                    BiasCorrectionRow(correction)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun BiasCorrectionRow(correction: BiasCorrectionEntity) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.ITALIAN) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${sessionModeLabel(correction.sessionMode)} · ${strategyLabel(correction.strategy)}",
                fontWeight = FontWeight.Bold,
            )
            Text("Modello: ${correction.aiModelId}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Fattore ${"%.2f".format(correction.factor)}× su ${correction.sampleCount} correzioni · aggiornato il ${dateFormat.format(Date(correction.updatedAtEpochMillis))}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
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
