package com.ugocalc.app.ui.results

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.LoadingOverlay
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoRed

/** Visualizzazione dei risultati del Passaggio 1, separati per modello (§2.4 punto 4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionResultsScreen(
    onBack: () -> Unit,
    onChoReceived: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToV2Details: () -> Unit = {},
    onNavigateToAmbiguity: () -> Unit = {},
    viewModel: VisionResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.matchingDone) {
        if (uiState.matchingDone) {
            onChoReceived()
            viewModel.onMatchingDoneConsumed()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Risultati analisi") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.allFailed) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "È andato tutto in mona, tutti i modelli hanno fallito.",
                        style = MaterialTheme.typography.titleMedium,
                        color = UgoRed,
                    )
                    Button(onClick = { viewModel.retryAnalysis(); onRetry() }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Riprova l'analisi")
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.outcomes) { outcome ->
                            ModelResultCard(
                                outcome = outcome,
                                isSelectedAsReference = (outcome as? ModelVisionOutcome.Success)?.model?.id == uiState.referenceModelId,
                                showReferencePicker = uiState.successfulOutcomes.size > 1,
                                onSelectAsReference = { viewModel.selectReferenceModel(outcome.model.id) },
                            )
                        }
                    }

                    if (uiState.usingV2Engine) {
                        Button(
                            onClick = onNavigateToV2Details,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) { Text("Vedi dettagli tecnici V2") }
                    }

                    if (uiState.v2AmbiguitaRisolte.isNotEmpty()) {
                        Button(
                            onClick = onNavigateToAmbiguity,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) { Text("Ambiguità risolte (${uiState.v2AmbiguitaRisolte.size})") }
                    }

                    Button(
                        onClick = { viewModel.receiveCho() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Ricevi i CHO") }

                    if (!uiState.usingV2Engine) {
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.saveWeightsOnly() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Salva solo pesi (senza CHO)", color = Color.Gray)
                        }
                    }
                }
            }

            if (uiState.isMatching) LoadingOverlay()
        }
    }
}

@Composable
private fun ModelResultCard(
    outcome: ModelVisionOutcome,
    isSelectedAsReference: Boolean,
    showReferencePicker: Boolean,
    onSelectAsReference: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(outcome.model.displayName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (showReferencePicker && outcome is ModelVisionOutcome.Success) {
                    RadioButton(selected = isSelectedAsReference, onClick = onSelectAsReference)
                    Text("usa per i CHO", style = MaterialTheme.typography.bodySmall)
                }
            }
            when (outcome) {
                is ModelVisionOutcome.Failure -> {
                    Text("Analisi non riuscita", color = UgoRed, fontWeight = FontWeight.Bold)
                    Text(outcome.reason, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                is ModelVisionOutcome.Success -> {
                    val confidenzaPeso = outcome.result.confidenzaGlobalePeso
                    if (confidenzaPeso != null) {
                        Text("Confidenza peso: ${confidenzaPeso.percent}% — ${com.ugocalc.app.domain.model.ConfidenceScore.bandLabel(confidenzaPeso)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Identificazione: ${outcome.result.confidenzaGlobale}", style = MaterialTheme.typography.bodySmall)
                    if (outcome.result.ragionamento.isNotEmpty()) {
                        Text(
                            outcome.result.ragionamento,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    outcome.result.alimenti.forEach { food ->
                        val isCautious = food.nome in outcome.result.stimeCautelative
                        val confidenzaPesoFood = food.confidenzaPeso
                        val weightDisplay = if (confidenzaPesoFood != null) {
                            "${food.pesoStimatoG.toInt()}g @ ${confidenzaPesoFood.percent}%"
                        } else {
                            "${food.pesoStimatoG.toInt()}g"
                        }
                        Text(
                            "• ${food.nome} — ${if (food.isTrascurabile) "trascurabile" else weightDisplay} (${food.confidenza})",
                            modifier = Modifier.padding(top = 4.dp),
                            color = if (isCautious) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            fontWeight = if (isCautious) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isCautious) {
                            Text(
                                "⚠️ Identificazione ambigua: è stata scelta l'alternativa con CHO più alto per sicurezza.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(food.commento, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        if (food.ragionamento.isNotEmpty()) {
                            Text(
                                food.ragionamento,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = Color.Gray.copy(alpha = 0.8f),
                            )
                        }
                        food.decomposition?.let { d ->
                            Text(
                                "Decomposed from '${d.recipeName}' — ${(d.percentageOfRecipe * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                            )
                        }
                    }
                    if (outcome.result.fattoriIncertezza.isNotEmpty()) {
                        Text(
                            "Fattori di incertezza: ${outcome.result.fattoriIncertezza.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
