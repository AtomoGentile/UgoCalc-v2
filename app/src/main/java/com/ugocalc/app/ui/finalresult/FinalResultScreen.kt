package com.ugocalc.app.ui.finalresult

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.glycemic.GlycemicIndexResolver
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.OnlineFoodResearchResult
import com.ugocalc.app.domain.model.OnlineResearchReliability
import com.ugocalc.app.ui.components.AnimatedMacroMetricTile
import com.ugocalc.app.ui.components.AnimatedMetricValue
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EditorialDetailScaffold
import com.ugocalc.app.ui.components.FloatingCircleIconButton
import com.ugocalc.app.ui.components.FloatingSplitPillActions
import com.ugocalc.app.ui.components.FoodSearchPicker
import com.ugocalc.app.ui.components.GlycemicCurveCard
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.MacronutrientPieChart
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.formatMealShareSummary
import com.ugocalc.app.ui.components.shareText
import com.ugocalc.app.ui.theme.UgoSemanticColors
import com.ugocalc.app.util.parsePositiveLocaleDoubleOrNull
import java.util.Locale
import kotlin.math.roundToInt

private val QUICK_PORTIONS_G = listOf(50, 80, 100, 150, 200)

/** Schermata finale: riepilogo nutrizionale, dettaglio modificabile e salvataggio sicuro (§2.4 punti 6-7). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FinalResultScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRegisterGroundTruth: () -> Unit,
    onRegisterBenchmarkGroundTruth: () -> Unit,
    onOpenGlycemicSettings: () -> Unit = {},
    viewModel: FinalResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var correctingLineIndex by remember { mutableStateOf<Int?>(null) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }
    var editingNameText by remember { mutableStateOf("") }
    var editingGramsText by remember { mutableStateOf("") }
    var addingManualFood by remember { mutableStateOf(false) }
    var pendingManualEntry by remember { mutableStateOf<NormalizedFoodEntry?>(null) }
    var pendingManualGramsText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    EditorialDetailScaffold(
        title = "Risultato",
        onBack = onBack,
        trailing = {
            val shareTotals = uiState.totals
            val onShare: () -> Unit = {
                if (shareTotals != null) {
                    val mealTitle = uiState.foodTitle.ifBlank { "Pasto UgoCalc" }
                    val summary = formatMealShareSummary(
                        title = mealTitle,
                        totalChoG = shareTotals.totalChoG,
                        totalGrassiG = shareTotals.totalGrassiG,
                        lines = shareTotals.lines,
                        totalProteineG = shareTotals.totalProteineG,
                        estimatedKcal = shareTotals.estimatedKcal.roundToInt(),
                        glycemicLoad = uiState.glycemicSimulation?.totalGlycemicLoad,
                    )
                    shareText(context, mealTitle, summary)
                }
            }
            if (shareTotals != null) {
                FloatingSplitPillActions(
                    leftIcon = Icons.Filled.Share,
                    leftDescription = "Condividi riepilogo pasto",
                    onLeftClick = onShare,
                    rightIcon = if (uiState.favoriteRequested) Icons.Filled.Star else Icons.Filled.StarBorder,
                    rightDescription = if (uiState.favoriteRequested) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                    onRightClick = { viewModel.toggleFavoriteRequested() },
                    rightActive = uiState.favoriteRequested,
                    activeColor = MaterialTheme.colorScheme.tertiary,
                )
            } else {
                FloatingCircleIconButton(
                    icon = if (uiState.favoriteRequested) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (uiState.favoriteRequested) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                    onClick = { viewModel.toggleFavoriteRequested() },
                    contentColor = if (uiState.favoriteRequested) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    size = 40.dp,
                )
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    Button(
                        onClick = { viewModel.finish() },
                        enabled = !uiState.isFinishing,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .height(52.dp),
                    ) {
                        if (uiState.isFinishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Salvataggio...", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Fatto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) {
        val totals = uiState.totals
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "__title_field__") {
                OutlinedTextField(
                    value = uiState.foodTitle,
                    onValueChange = viewModel::setFoodTitle,
                    label = { Text("Nome del piatto") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (totals != null) {
                item(key = "__hero_card__") {
                    HeroNutritionCard(
                        totals = totals,
                        isSaving = uiState.isSaving,
                        hasSavedSnapshot = uiState.hasSavedSnapshot,
                    )
                }

                uiState.glycemicSimulation?.let { simulation ->
                    item(key = "__glycemic_curve_card__") {
                        GlycemicCurveCard(
                            simulation = simulation,
                            targetMaxMgDl = uiState.targetPostPrandialMaxMgDl,
                            onAdjustBaseline = viewModel::adjustBaselineBy,
                            onSetBaseline = viewModel::setPreMealGlucose,
                            onOpenGlycemicSettings = onOpenGlycemicSettings,
                        )
                    }
                }

                if (totals.excludedCount > 0) {
                    item(key = "__excluded_banner__") {
                        StatusBanner(
                            icon = Icons.Filled.Warning,
                            title = if (totals.excludedCount == 1) {
                                "1 alimento escluso dal totale"
                            } else {
                                "${totals.excludedCount} alimenti esclusi dal totale"
                            },
                            subtitle = "Abbina l'alimento alle tabelle locali o cercalo online per includerlo nei totali.",
                            tone = BannerTone.WARNING,
                        )
                    }
                }

                item(key = "__detail_header__") {
                    UgoSectionHeader(
                        title = "Dettaglio alimenti (${totals.lines.size})",
                        subtitle = "Tocca un alimento per correggere nome, peso o abbinamento",
                        actionLabel = "+ Aggiungi",
                        onAction = { addingManualFood = true },
                    )
                }

                itemsIndexed(totals.lines) { index, line ->
                    FoodLineCard(
                        line = line,
                        onlineResearchState = uiState.onlineResearch[index],
                        onAdjustWeight = { deltaGrams ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.adjustWeightBy(index, deltaGrams)
                        },
                        onEdit = {
                            editingNameText = line.recognizedFood.nome
                            editingGramsText = formatEditableGrams(line.recognizedFood.pesoStimatoG)
                            editingLineIndex = index
                        },
                        onRemove = { viewModel.removeLine(index) },
                        onMatchManually = { correctingLineIndex = index },
                        onSearchOnline = { viewModel.searchOnline(index) },
                        onAddOnlineToDb = { viewModel.addOnlineResultToDatabase(index) },
                        onDismissOnline = { viewModel.dismissOnlineResult(index) },
                    )
                }
            }

            if (uiState.showGroundTruthCta || uiState.showBenchmarkGroundTruthCta) {
                item(key = "__ground_truth__") {
                    GroundTruthPromptCard(
                        showGroundTruth = uiState.showGroundTruthCta,
                        showBenchmark = uiState.showBenchmarkGroundTruthCta,
                        onRegisterGroundTruth = onRegisterGroundTruth,
                        onRegisterBenchmarkGroundTruth = onRegisterBenchmarkGroundTruth,
                    )
                }
            }
        }
    }

    val lineIndex = correctingLineIndex
    if (lineIndex != null) {
        ModalBottomSheet(onDismissRequest = { correctingLineIndex = null }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Cerca l'alimento nelle tue tabelle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                FoodSearchPicker(
                    onSelect = { entry ->
                        viewModel.applyManualMatch(lineIndex, entry)
                        correctingLineIndex = null
                    },
                )
            }
        }
    }

    val editIndex = editingLineIndex
    if (editIndex != null) {
        val parsedEditGrams = editingGramsText.parsePositiveLocaleDoubleOrNull()
        val isGramsError = editingGramsText.isNotBlank() && parsedEditGrams == null
        val canSaveEdit = editingNameText.isNotBlank() && parsedEditGrams != null

        AlertDialog(
            onDismissRequest = { editingLineIndex = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Correggi l'alimento",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editingNameText,
                        onValueChange = { editingNameText = it },
                        label = { Text("Nome") },
                        singleLine = true,
                        isError = editingNameText.isBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editingGramsText,
                        onValueChange = { editingGramsText = it },
                        label = { Text("Grammi") },
                        placeholder = { Text("es. 80 o 80,5") },
                        singleLine = true,
                        isError = isGramsError,
                        supportingText = if (isGramsError) {
                            { Text("Inserisci un valore positivo (es. 80 o 80,5)") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canSaveEdit,
                    onClick = {
                        viewModel.correctRecognizedFood(editIndex, editingNameText, parsedEditGrams)
                        editingLineIndex = null
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLineIndex = null }) {
                    Text("Annulla")
                }
            },
        )
    }

    if (addingManualFood) {
        ModalBottomSheet(onDismissRequest = { addingManualFood = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Aggiungi un alimento dalle tue tabelle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                FoodSearchPicker(
                    onSelect = { entry ->
                        pendingManualEntry = entry
                        pendingManualGramsText = "100"
                        addingManualFood = false
                    },
                )
            }
        }
    }

    val manualEntry = pendingManualEntry
    if (manualEntry != null) {
        val parsedManualGrams = pendingManualGramsText.parsePositiveLocaleDoubleOrNull()
        val isManualGramsError = pendingManualGramsText.isNotBlank() && parsedManualGrams == null

        AlertDialog(
            onDismissRequest = { pendingManualEntry = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Quanti grammi di ${manualEntry.nome}?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pendingManualGramsText,
                        onValueChange = { pendingManualGramsText = it },
                        label = { Text("Grammi") },
                        placeholder = { Text("es. 100 o 75,5") },
                        singleLine = true,
                        isError = isManualGramsError,
                        supportingText = if (isManualGramsError) {
                            { Text("Inserisci una quantità positiva") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QUICK_PORTIONS_G.forEach { grams ->
                            val selected = parsedManualGrams == grams.toDouble()
                            FilterChip(
                                selected = selected,
                                onClick = { pendingManualGramsText = grams.toString() },
                                label = { Text("${grams}g") },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = parsedManualGrams != null,
                    onClick = {
                        parsedManualGrams?.let { grams -> viewModel.addManualFood(manualEntry, grams) }
                        pendingManualEntry = null
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Aggiungi")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingManualEntry = null }) {
                    Text("Annulla")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroNutritionCard(
    totals: AnalysisTotals,
    isSaving: Boolean,
    hasSavedSnapshot: Boolean,
) {
    val estimatedKcal = totals.estimatedKcal.roundToInt()

    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Totale del pasto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (isSaving || hasSavedSnapshot) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSaving) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        UgoSemanticColors.successContainer
                    },
                ) {
                    Text(
                        text = if (isSaving) "Salvataggio..." else "Salvato ✓",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSaving) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            UgoSemanticColors.onSuccessContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedMacroMetricTile(
                label = "CHO",
                targetValue = totals.totalChoG.toFloat(),
                decimals = 1,
                unit = "g",
                accentColor = UgoSemanticColors.cho,
                modifier = Modifier.weight(1f),
            )
            AnimatedMacroMetricTile(
                label = "Proteine",
                targetValue = totals.totalProteineG.toFloat(),
                decimals = 1,
                unit = "g",
                accentColor = UgoSemanticColors.protein,
                modifier = Modifier.weight(1f),
            )
            AnimatedMacroMetricTile(
                label = "Grassi",
                targetValue = totals.totalGrassiG.toFloat(),
                decimals = 1,
                unit = "g",
                accentColor = UgoSemanticColors.fat,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Energia stimata",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                    AnimatedMetricValue(
                        targetValue = estimatedKcal.toFloat(),
                        decimals = 0,
                        prefix = "~",
                        suffix = " kcal",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (totals.fpuCount >= 0.5) {
                MacroPill(
                    label = "FPU",
                    value = "%.1f".format(Locale.ITALIAN, totals.fpuCount),
                    containerColor = UgoSemanticColors.proteinContainer,
                    contentColor = UgoSemanticColors.onProteinContainer,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        MacronutrientPieChart(
            choPercent = totals.choPercentOfMacros,
            grassiPercent = totals.grassiPercentOfMacros,
            proteinePercent = totals.proteinePercentOfMacros,
            choGrams = totals.totalChoG,
            grassiGrams = totals.totalGrassiG,
            proteineGrams = totals.totalProteineG,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodLineCard(
    line: CalculatedFoodLine,
    onlineResearchState: OnlineResearchUiState?,
    onAdjustWeight: (Float) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMatchManually: () -> Unit,
    onSearchOnline: () -> Unit,
    onAddOnlineToDb: () -> Unit,
    onDismissOnline: () -> Unit,
) {
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (line.isExcluded) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.recognizedFood.nome,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MacroPill(
                        label = "Porzione",
                        value = if (line.recognizedFood.isTrascurabile) {
                            "trascurabile"
                        } else {
                            "${formatEditableGrams(line.recognizedFood.pesoStimatoG)} g"
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (!line.recognizedFood.isTrascurabile) {
                        WeightStepPill(
                            label = "-10g",
                            enabled = line.recognizedFood.pesoStimatoG > 5.0,
                            onClick = { onAdjustWeight(-10f) },
                        )
                        WeightStepPill(
                            label = "+10g",
                            enabled = true,
                            onClick = { onAdjustWeight(10f) },
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Correggi nome o peso di ${line.recognizedFood.nome}",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Rimuovi ${line.recognizedFood.nome}",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (line.isExcluded) {
            Text(
                text = "Nessuna corrispondenza in tabella: escluso dal totale",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onMatchManually,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cerca o crea")
                }
                if (onlineResearchState == null) {
                    OutlinedButton(
                        onClick = onSearchOnline,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cerca online")
                    }
                }
            }

            if (onlineResearchState != null) {
                Spacer(Modifier.height(8.dp))
                OnlineResearchSection(
                    state = onlineResearchState,
                    onSearch = onSearchOnline,
                    onAdd = onAddOnlineToDb,
                    onDismiss = onDismissOnline,
                )
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MacroPill(
                    label = "CHO",
                    value = "${"%.1f".format(line.choContributionG)} g",
                    containerColor = UgoSemanticColors.choContainer,
                    contentColor = UgoSemanticColors.onChoContainer,
                )
                MacroPill(
                    label = "Prot",
                    value = "${"%.1f".format(line.proteineContributionG)} g",
                    containerColor = UgoSemanticColors.proteinContainer,
                    contentColor = UgoSemanticColors.onProteinContainer,
                )
                MacroPill(
                    label = "Grassi",
                    value = "${"%.1f".format(line.grassiContributionG)} g",
                    containerColor = UgoSemanticColors.fatContainer,
                    contentColor = UgoSemanticColors.onFatContainer,
                )
                line.tableEntry?.takeIf { line.choContributionG > 0.0 }?.let { entry ->
                    val resolvedGi = GlycemicIndexResolver.resolve(entry)
                    val lineGl = resolvedGi.gi * line.choContributionG / 100.0
                    MacroPill(
                        label = "GL",
                        value = "%.1f".format(Locale.ITALIAN, lineGl),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Abbinato a: ${line.match?.voceTabellaAbbinata} (confidenza ${line.match?.confidenzaAbbinamento})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onMatchManually,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text("Correggi abbinamento")
            }
        }
    }
}

@Composable
private fun GroundTruthPromptCard(
    showGroundTruth: Boolean,
    showBenchmark: Boolean,
    onRegisterGroundTruth: () -> Unit,
    onRegisterBenchmarkGroundTruth: () -> Unit,
) {
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Scale,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aiuta UgoCalc a migliorare",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Se hai pesato il piatto con la bilancia, registra il peso reale per calibrare le stime future.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (showGroundTruth) {
            OutlinedButton(
                onClick = onRegisterGroundTruth,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Registra il peso vero di questa analisi")
            }
        }
        if (showBenchmark) {
            if (showGroundTruth) Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onRegisterBenchmarkGroundTruth,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Benchmark: registra il peso vero")
            }
        }
    }
}

/**
 * Azione "Cerca online" (§Pezzo E) per un alimento senza corrispondenza.
 */
@Composable
private fun OnlineResearchSection(
    state: OnlineResearchUiState?,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        null -> {
            TextButton(onClick = onSearch) {
                Text("Ricerca online la pietanza mancante")
            }
        }
        OnlineResearchUiState.Loading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Ricerca nutrizionale in corso...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        is OnlineResearchUiState.Error -> {
            StatusBanner(
                icon = Icons.Filled.Warning,
                title = "Ricerca non riuscita",
                subtitle = state.message,
                tone = BannerTone.ERROR,
                actionLabel = "Chiudi",
                onAction = onDismiss,
            )
        }
        is OnlineResearchUiState.Result -> {
            OnlineResearchResultCard(result = state.result, onAdd = onAdd, onDismiss = onDismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineResearchResultCard(
    result: OnlineFoodResearchResult,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        contentPadding = PaddingValues(12.dp),
    ) {
        Text(
            text = result.alimento,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when (result.affidabilita) {
                OnlineResearchReliability.SICURA -> "Affidabilità: sicura (fonte ufficiale)"
                OnlineResearchReliability.BUONA -> "Affidabilità: buona (fonte autorevole)"
                OnlineResearchReliability.ERRORE_DATI -> "Dati non affidabili"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (result.haDatiUtilizzabili) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MacroPill(
                    label = "CHO/100g",
                    value = "${"%.1f".format(result.choPer100g)} g",
                    containerColor = UgoSemanticColors.choContainer,
                    contentColor = UgoSemanticColors.onChoContainer,
                )
                result.proteinePer100g?.let { prot ->
                    MacroPill(
                        label = "Prot/100g",
                        value = "${"%.1f".format(prot)} g",
                        containerColor = UgoSemanticColors.proteinContainer,
                        contentColor = UgoSemanticColors.onProteinContainer,
                    )
                }
                MacroPill(
                    label = "Grassi/100g",
                    value = "${"%.1f".format(result.grassiPer100g)} g",
                    containerColor = UgoSemanticColors.fatContainer,
                    contentColor = UgoSemanticColors.onFatContainer,
                )
                result.indiceGlicemico?.let { gi ->
                    MacroPill(
                        label = "IG",
                        value = "$gi",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (result.fonteNome.isNotBlank()) {
                Text(
                    text = "Fonte: ${result.fonteNome}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(enabled = result.fonteUrl.isNotBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.fonteUrl)))
                            }
                        },
                )
            }
        } else {
            Text(
                text = result.motivoErrore.ifBlank { "Nessun dato affidabile trovato." },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (result.haDatiUtilizzabili) {
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aggiungi al database")
                }
            }
            TextButton(onClick = onDismiss) {
                Text("Ignora")
            }
        }
    }
}

@Composable
private fun WeightStepPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.7f else 0.3f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun formatEditableGrams(grams: Double): String =
    if (grams == grams.toLong().toDouble()) {
        grams.toLong().toString()
    } else {
        "%.1f".format(grams)
    }
