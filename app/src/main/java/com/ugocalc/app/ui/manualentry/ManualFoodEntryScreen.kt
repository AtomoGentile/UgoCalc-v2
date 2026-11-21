package com.ugocalc.app.ui.manualentry

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.domain.glycemic.GlycemicLoadLevel
import com.ugocalc.app.ui.components.AnimatedMacroMetricTile
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.FoodSearchPicker
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors
import java.util.Locale

private val QUICK_PORTIONS_G = listOf(50, 80, 100, 150, 200)

/**
 * Composizione manuale di un pasto (§ ricerca tra i cibi del database): in due passi —
 * 1. Scegli alimenti → 2. Indica le quantità. Nessuna chiamata AI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualFoodEntryScreen(
    onProceed: () -> Unit,
    onBack: () -> Unit,
    viewModel: ManualFoodEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState.step) {
        ManualEntryStep.SELECT -> SelectStep(uiState = uiState, viewModel = viewModel, onBack = onBack)
        ManualEntryStep.QUANTITY -> QuantityStep(uiState = uiState, viewModel = viewModel, onBack = onBack, onProceed = onProceed)
    }
}

@Composable
private fun ManualStepIndicator(
    currentStep: ManualEntryStep,
    skipSelectStep: Boolean,
    modifier: Modifier = Modifier,
) {
    val step1Done = currentStep == ManualEntryStep.QUANTITY
    val step2Active = currentStep == ManualEntryStep.QUANTITY

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepBadge(
            number = 1,
            label = if (skipSelectStep) "Prodotto scelto" else "Scegli alimenti",
            isActive = currentStep == ManualEntryStep.SELECT,
            isDone = step1Done,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        StepBadge(
            number = 2,
            label = "Indica le quantità",
            isActive = step2Active,
            isDone = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StepBadge(
    number: Int,
    label: String,
    isActive: Boolean,
    isDone: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isDone -> UgoSemanticColors.successContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        isDone -> UgoSemanticColors.onSuccessContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        isDone -> UgoSemanticColors.success.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = if (isActive || isDone) contentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Text(
                            text = number.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectStep(
    uiState: ManualFoodEntryUiState,
    viewModel: ManualFoodEntryViewModel,
    onBack: () -> Unit,
) {
    val selectedCount = uiState.selected.size
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedCount == 0) {
                            "Aggiungi alimenti"
                        } else if (selectedCount == 1) {
                            "1 alimento selezionato"
                        } else {
                            "$selectedCount selezionati"
                        }
                    )
                },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                        ) {
                            Text("Indietro")
                        }
                        Button(
                            enabled = uiState.selected.isNotEmpty(),
                            onClick = viewModel::confirmSelection,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(50.dp),
                        ) {
                            Text(
                                text = if (selectedCount > 0) "Continua ($selectedCount)" else "Continua",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ManualStepIndicator(
                currentStep = ManualEntryStep.SELECT,
                skipSelectStep = uiState.skipSelectStep,
            )
            FoodSearchPicker(
                onSelect = {},
                selectedEntryIds = uiState.selected.map { it.entryId }.toSet(),
                onToggleSelect = viewModel::toggleSelect,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                fillAvailableHeight = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuantityStep(
    uiState: ManualFoodEntryUiState,
    viewModel: ManualFoodEntryViewModel,
    onBack: () -> Unit,
    onProceed: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val handleBack: () -> Unit = {
        if (uiState.skipSelectStep) onBack() else viewModel.backToSelection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Indica le quantità") },
                navigationIcon = { BackIconButton(handleBack) },
                colors = ugoTopAppBarColors(),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = handleBack,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                        ) {
                            Text("Indietro")
                        }
                        Button(
                            enabled = uiState.canProceed,
                            onClick = { if (viewModel.confirmAndProceed()) onProceed() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(50.dp),
                        ) {
                            Text("Calcola", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ManualStepIndicator(
                currentStep = ManualEntryStep.QUANTITY,
                skipSelectStep = uiState.skipSelectStep,
            )

            // Live CHO + Proteine + Grassi + Carico Glicemico preview total banner
            if (uiState.rows.isNotEmpty()) {
                val glLevel = GlycemicCurveCalculator.classifyGlycemicLoad(uiState.previewGlycemicLoad)
                val (glBg, glFg, glLabel) = when (glLevel) {
                    GlycemicLoadLevel.BASSO -> Triple(
                        UgoSemanticColors.successContainer,
                        UgoSemanticColors.onSuccessContainer,
                        "Basso",
                    )
                    GlycemicLoadLevel.MEDIO -> Triple(
                        UgoSemanticColors.warningContainer,
                        UgoSemanticColors.onWarningContainer,
                        "Medio",
                    )
                    GlycemicLoadLevel.ALTO -> Triple(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        "Alto",
                    )
                }
                UgoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Anteprima totale",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = glBg,
                        ) {
                            Text(
                                text = "Carico Glicemico ${"%.1f".format(Locale.ITALIAN, uiState.previewGlycemicLoad)} · $glLabel",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = glFg,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnimatedMacroMetricTile(
                            label = "CHO",
                            targetValue = uiState.previewChoG.toFloat(),
                            decimals = 1,
                            unit = "g",
                            accentColor = UgoSemanticColors.cho,
                            modifier = Modifier.weight(1f),
                        )
                        AnimatedMacroMetricTile(
                            label = "Proteine",
                            targetValue = uiState.previewProteineG.toFloat(),
                            decimals = 1,
                            unit = "g",
                            accentColor = UgoSemanticColors.protein,
                            modifier = Modifier.weight(1f),
                        )
                        AnimatedMacroMetricTile(
                            label = "Grassi",
                            targetValue = uiState.previewGrassiG.toFloat(),
                            decimals = 1,
                            unit = "g",
                            accentColor = UgoSemanticColors.fat,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (uiState.rows.isEmpty()) {
                EmptyStateMessage(
                    icon = Icons.Filled.Restaurant,
                    title = "Nessun alimento rimasto",
                    message = "Torna allo step precedente per scegliere almeno un alimento.",
                    actionLabel = if (uiState.skipSelectStep) null else "Scegli alimenti",
                    onAction = if (uiState.skipSelectStep) null else viewModel::backToSelection,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(uiState.rows) { index, row ->
                        val gramsValid = row.gramsOrNull != null
                        UgoCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (!gramsValid) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                            },
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                ) {
                                    Text(
                                        text = row.entry.nome,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val giSuffix = row.resolvedGi.let { resolved ->
                                        if (resolved.gi > 0) {
                                            if (resolved.isEstimated) " · IG ~${resolved.gi}" else " · IG ${resolved.gi}"
                                        } else {
                                            ""
                                        }
                                    }
                                    Text(
                                        text = "Per 100g: CHO ${"%.1f".format(row.entry.choPer100g)}g · Prot ${"%.1f".format(row.entry.proteinePer100g)}g · Grassi ${"%.1f".format(row.entry.grassiPer100g)}g$giSuffix",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                OutlinedTextField(
                                    value = row.gramsText,
                                    onValueChange = { viewModel.setGrams(index, it) },
                                    label = { Text("g") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    isError = !gramsValid,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.width(96.dp),
                                )
                                IconButton(onClick = { viewModel.removeFood(index) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Rimuovi ${row.entry.nome}",
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                QUICK_PORTIONS_G.forEach { portionG ->
                                    val isSelected = row.gramsOrNull == portionG.toDouble()
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.setGrams(index, portionG.toString())
                                        },
                                        label = { Text("${portionG}g") },
                                    )
                                }
                            }

                            if (gramsValid) {
                                Spacer(Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    MacroPill(
                                        label = "CHO",
                                        value = "${"%.1f".format(row.previewChoG)} g",
                                        containerColor = UgoSemanticColors.choContainer,
                                        contentColor = UgoSemanticColors.onChoContainer,
                                    )
                                    MacroPill(
                                        label = "Prot",
                                        value = "${"%.1f".format(row.previewProteineG)} g",
                                        containerColor = UgoSemanticColors.proteinContainer,
                                        contentColor = UgoSemanticColors.onProteinContainer,
                                    )
                                    MacroPill(
                                        label = "Grassi",
                                        value = "${"%.1f".format(row.previewGrassiG)} g",
                                        containerColor = UgoSemanticColors.fatContainer,
                                        contentColor = UgoSemanticColors.onFatContainer,
                                    )
                                    if (row.previewGlycemicLoad > 0.0) {
                                        MacroPill(
                                            label = "GL",
                                            value = "%.1f".format(Locale.ITALIAN, row.previewGlycemicLoad),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Inserisci una quantità valida (es. 80 o 80,5)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
