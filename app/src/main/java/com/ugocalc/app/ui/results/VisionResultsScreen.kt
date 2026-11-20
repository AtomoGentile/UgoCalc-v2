package com.ugocalc.app.ui.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.ConfidenceScore
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.ConfidenceBadge
import com.ugocalc.app.ui.components.LoadingOverlay
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.ModelLogoBox
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors

/** Visualizzazione dei risultati del Passaggio 1, separati per modello (§2.4 punto 4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionResultsScreen(
    onBack: () -> Unit,
    onChoReceived: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToV2Details: () -> Unit = {},
    onNavigateToAmbiguity: () -> Unit = {},
    onOpenApiSettings: () -> Unit = {},
    viewModel: VisionResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.matchingDone) {
        if (uiState.matchingDone) {
            onChoReceived()
            viewModel.onMatchingDoneConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risultati analisi") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
        bottomBar = {
            if (!uiState.allFailed && uiState.successfulOutcomes.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Button(
                                onClick = { viewModel.receiveCho() },
                                enabled = !uiState.isMatching,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                            ) {
                                Text(
                                    text = "Calcola CHO e Grassi",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (!uiState.usingV2Engine) {
                                TextButton(
                                    onClick = { viewModel.saveWeightsOnly() },
                                    enabled = !uiState.isMatching,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "Salva solo pesi (senza CHO)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.allFailed) {
                AllModelsFailedView(
                    outcomes = uiState.outcomes.filterIsInstance<ModelVisionOutcome.Failure>(),
                    onRetry = {
                        viewModel.retryAnalysis()
                        onRetry()
                    },
                    onOpenApiSettings = onOpenApiSettings,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.matchingError != null) {
                        item(key = "__matching_error__") {
                            StatusBanner(
                                icon = Icons.Filled.ErrorOutline,
                                title = "Abbinamento non riuscito",
                                subtitle = uiState.matchingError.orEmpty(),
                                tone = BannerTone.ERROR,
                                actionLabel = "Chiudi",
                                onAction = viewModel::dismissMatchingError,
                            )
                        }
                    }

                    items(uiState.outcomes, key = { it.model.uid }) { outcome ->
                        val successModel = (outcome as? ModelVisionOutcome.Success)?.model
                        val isSelectedAsReference = successModel != null && (
                            successModel.uid == uiState.referenceModelId ||
                                (uiState.referenceModelId?.contains("::") != true && successModel.id == uiState.referenceModelId)
                            )
                        ModelResultCard(
                            outcome = outcome,
                            isSelectedAsReference = isSelectedAsReference,
                            showReferencePicker = uiState.successfulOutcomes.size > 1,
                            onSelectAsReference = { viewModel.selectReferenceModel(outcome.model.uid) },
                            confidenceIndicatorEnabled = uiState.confidenceIndicatorEnabled,
                            confidenceAlertThresholdPercent = uiState.confidenceAlertThresholdPercent,
                        )
                    }

                    if (uiState.usingV2Engine) {
                        item(key = "__v2_details__") {
                            Button(
                                onClick = onNavigateToV2Details,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Vedi dettagli tecnici V2")
                            }
                        }
                    }

                    if (uiState.v2AmbiguitaRisolte.isNotEmpty()) {
                        item(key = "__v2_ambiguita__") {
                            Button(
                                onClick = onNavigateToAmbiguity,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Ambiguità risolte (${uiState.v2AmbiguitaRisolte.size})")
                            }
                        }
                    }

                    if (uiState.shadowV2Results.isNotEmpty()) {
                        item(key = "__v2_shadow__") {
                            Button(
                                onClick = onNavigateToV2Details,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Vedi V2 (${uiState.shadowV2Results.size})")
                            }
                        }
                    }
                }
            }

            if (uiState.isMatching) {
                LoadingOverlay(statusText = "Abbinamento tabelle nutrizionali...")
            }
        }
    }
}

@Composable
private fun AllModelsFailedView(
    outcomes: List<ModelVisionOutcome.Failure>,
    onRetry: () -> Unit,
    onOpenApiSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__header_error__") {
            StatusBanner(
                icon = Icons.Filled.ErrorOutline,
                title = "Analisi non riuscita",
                subtitle = "Nessuno dei modelli selezionati ha completato l'analisi fotografica. Verifica i dettagli qui sotto.",
                tone = BannerTone.ERROR,
            )
        }

        item(key = "__section_title__") {
            UgoSectionHeader(
                title = "Dettaglio errori (${outcomes.size})",
                subtitle = "Motivo segnalato da ciascun provider",
            )
        }

        items(outcomes, key = { it.model.uid }) { failure ->
            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                contentPadding = PaddingValues(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.08f)),
                    ) {
                        ModelLogoBox(
                            logoRes = failure.model.logoRes,
                            displayName = failure.model.displayName,
                            subtitle = failure.model.subtitle,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = failure.model.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (failure.model.subtitle.isNotBlank()) {
                            Text(
                                text = failure.model.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = failure.reason.ifBlank { "Errore sconosciuto durante l'analisi." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item(key = "__actions__") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Riprova analisi", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenApiSettings,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Controlla API")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelResultCard(
    outcome: ModelVisionOutcome,
    isSelectedAsReference: Boolean,
    showReferencePicker: Boolean,
    onSelectAsReference: () -> Unit,
    confidenceIndicatorEnabled: Boolean = false,
    confidenceAlertThresholdPercent: Int = ConfidenceScore.DEFAULT_ALERT_THRESHOLD_PERCENT,
) {
    val isSelectedHighlight = showReferencePicker && isSelectedAsReference && outcome is ModelVisionOutcome.Success
    var showReasoning by rememberSaveable(outcome.model.uid) { mutableStateOf(false) }

    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = when {
            outcome is ModelVisionOutcome.Failure -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            isSelectedHighlight -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        elevation = if (isSelectedHighlight) 3.dp else 1.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.08f)),
            ) {
                ModelLogoBox(
                    logoRes = outcome.model.logoRes,
                    displayName = outcome.model.displayName,
                    subtitle = outcome.model.subtitle,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = outcome.model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (outcome.model.subtitle.isNotBlank()) {
                    Text(
                        text = outcome.model.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (showReferencePicker && outcome is ModelVisionOutcome.Success) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = onSelectAsReference)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    RadioButton(
                        selected = isSelectedAsReference,
                        onClick = onSelectAsReference,
                    )
                    Text(
                        text = "Usa",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelectedAsReference) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelectedAsReference) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (outcome) {
            is ModelVisionOutcome.Failure -> {
                StatusBanner(
                    icon = Icons.Filled.ErrorOutline,
                    title = "Analisi non riuscita",
                    subtitle = outcome.reason.ifBlank { "Errore durante l'analisi del modello." },
                    tone = BannerTone.ERROR,
                )
            }

            is ModelVisionOutcome.Success -> {
                if (confidenceIndicatorEnabled) {
                    val confidenceScore = outcome.result.confidenzaGlobalePeso
                        ?: ConfidenceScore(Confidence.toPercentEstimate(outcome.result.confidenzaGlobale))
                    if (ConfidenceScore.needsAlert(confidenceScore, confidenceAlertThresholdPercent)) {
                        Row(
                            modifier = Modifier.padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Affidabilità stimata:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ConfidenceBadge(
                                confidence = confidenceScore,
                                alertThresholdPercent = confidenceAlertThresholdPercent,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    outcome.result.alimenti.forEach { food ->
                        val isCautious = food.nome in outcome.result.stimeCautelative
                        val confidenzaPesoFood = food.confidenzaPeso
                        val weightDisplay = when {
                            food.isTrascurabile -> "trascurabile"
                            confidenzaPesoFood != null -> "${food.pesoStimatoG.toInt()} g · ${confidenzaPesoFood.percent}%"
                            else -> "${food.pesoStimatoG.toInt()} g"
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isCautious) {
                                    UgoSemanticColors.warning.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = food.nome,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                    )
                                    MacroPill(
                                        label = "Peso",
                                        value = weightDisplay,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    MacroPill(
                                        label = "Confidenza",
                                        value = food.confidenza.name.lowercase().replaceFirstChar { it.uppercase() },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (isCautious) {
                                        MacroPill(
                                            label = "Cautela",
                                            value = "CHO più alto",
                                            containerColor = UgoSemanticColors.warningContainer,
                                            contentColor = UgoSemanticColors.onWarningContainer,
                                        )
                                    }
                                }

                                if (isCautious) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = UgoSemanticColors.warning,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Text(
                                            text = "Identificazione ambigua: scelta l'alternativa con CHO più alto per sicurezza.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = UgoSemanticColors.warning,
                                        )
                                    }
                                }

                                if (food.commento.isNotBlank()) {
                                    Text(
                                        text = food.commento,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                food.decomposition?.let { d ->
                                    Text(
                                        text = "Da '${d.recipeName}' — ${(d.percentageOfRecipe * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                val hasReasoningNotes = outcome.result.ragionamento.isNotBlank() ||
                    outcome.result.alimenti.any { it.ragionamento.isNotBlank() } ||
                    outcome.result.fattoriIncertezza.isNotEmpty()

                if (hasReasoningNotes) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReasoning = !showReasoning }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Note e ragionamento AI",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = if (showReasoning) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showReasoning) "Nascondi note" else "Mostra note",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    AnimatedVisibility(visible = showReasoning) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            if (outcome.result.ragionamento.isNotBlank()) {
                                Text(
                                    text = outcome.result.ragionamento,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            outcome.result.alimenti
                                .filter { it.ragionamento.isNotBlank() }
                                .forEach { food ->
                                    Text(
                                        text = "• ${food.nome}: ${food.ragionamento}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            if (outcome.result.fattoriIncertezza.isNotEmpty()) {
                                Text(
                                    text = "Fattori di incertezza: ${outcome.result.fattoriIncertezza.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
