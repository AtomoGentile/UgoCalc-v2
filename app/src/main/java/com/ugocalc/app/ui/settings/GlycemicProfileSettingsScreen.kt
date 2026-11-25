package com.ugocalc.app.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.domain.glycemic.GlycemicProfilePreset
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.EditorialDetailScaffold
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.GlycemicCurveCard
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import kotlin.math.roundToInt

private val SAMPLE_ITALIAN_MEAL_LINES = listOf(
    CalculatedFoodLine(
        recognizedFood = RecognizedFood(
            nome = "Pasta di semola",
            pesoStimatoG = 80.0,
            commento = "",
            confidenza = Confidence.ALTO,
        ),
        match = FoodMatch("Pasta di semola", "Pasta di semola", false, Confidence.ALTO),
        tableEntry = NormalizedFoodEntry(
            nome = "Pasta di semola",
            choPer100g = 72.0,
            grassiPer100g = 1.5,
            proteinePer100g = 12.5,
            indiceGlicemico = 49,
        ),
    ),
    CalculatedFoodLine(
        recognizedFood = RecognizedFood(
            nome = "Olio extravergine d'oliva",
            pesoStimatoG = 10.0,
            commento = "",
            confidenza = Confidence.ALTO,
        ),
        match = FoodMatch("Olio extravergine d'oliva", "Olio extravergine d'oliva", false, Confidence.ALTO),
        tableEntry = NormalizedFoodEntry(
            nome = "Olio extravergine d'oliva",
            choPer100g = 0.0,
            grassiPer100g = 99.9,
            proteinePer100g = 0.0,
            indiceGlicemico = 0,
        ),
    ),
    CalculatedFoodLine(
        recognizedFood = RecognizedFood(
            nome = "Petto di pollo",
            pesoStimatoG = 100.0,
            commento = "",
            confidenza = Confidence.ALTO,
        ),
        match = FoodMatch("Petto di pollo", "Petto di pollo", false, Confidence.ALTO),
        tableEntry = NormalizedFoodEntry(
            nome = "Petto di pollo",
            choPer100g = 0.0,
            grassiPer100g = 1.2,
            proteinePer100g = 23.0,
            indiceGlicemico = 0,
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GlycemicProfileSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val settings = state.userSettings
    var previewBaselineMgDl by remember { mutableIntStateOf(100) }
    val profile = remember(
        settings.glycemicProfilePreset,
        settings.carbSensitivityMgDlPer10g,
        settings.targetPostPrandialMaxMgDl,
        previewBaselineMgDl,
    ) {
        settings.toGlycemicProfile().copy(baselineGlucoseMgDl = previewBaselineMgDl.toDouble())
    }
    val sampleSimulation = remember(profile) {
        GlycemicCurveCalculator.simulateMeal(SAMPLE_ITALIAN_MEAL_LINES, profile)
    }
    val haptic = LocalHapticFeedback.current

    EditorialDetailScaffold(
        title = "Profilo Glicemico",
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "toggle_curve_card") {
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Mostra curva glicemica nei risultati",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Disattivala se usi insulina: i risultati restano più puliti con CHO, Grassi, Proteine e Carico Glicemico.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.showGlycemicCurve,
                            onCheckedChange = viewModel::setShowGlycemicCurve,
                        )
                    }
                }
            }

            item(key = "preset_card") {
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    UgoSectionHeader(
                        title = "Preset fisiologico",
                        subtitle = "Imposta rapidamente sensibilità ai carboidrati e soglia post-prandiale",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlycemicProfilePreset.entries.forEach { preset ->
                            val selected = profile.preset == preset
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.applyGlycemicProfilePreset(preset)
                                },
                                label = { Text(preset.label) },
                            )
                        }
                    }
                }
            }

            item(key = "sliders_card") {
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    UgoSectionHeader(
                        title = "Parametri personalizzati",
                        subtitle = "Affina i valori fissi del tuo profilo (la glicemia pre-pasto si inserisce in Analisi AI)",
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    ParameterSliderWithSteppers(
                        label = "Sensibilità carboidrati",
                        valueText = "+${settings.carbSensitivityMgDlPer10g} mg/dL per 10g CHO",
                        value = settings.carbSensitivityMgDlPer10g.toFloat(),
                        valueRange = 10f..60f,
                        step = 2,
                        onValueChange = { viewModel.setCarbSensitivityMgDlPer10g(it.roundToInt()) },
                        onStep = { delta ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setCarbSensitivityMgDlPer10g(settings.carbSensitivityMgDlPer10g + delta)
                        },
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ParameterSliderWithSteppers(
                        label = "Target massimo post-prandiale",
                        valueText = "≤ ${settings.targetPostPrandialMaxMgDl} mg/dL",
                        value = settings.targetPostPrandialMaxMgDl.toFloat(),
                        valueRange = 120f..200f,
                        step = 5,
                        onValueChange = { viewModel.setTargetPostPrandialMaxMgDl(it.roundToInt()) },
                        onStep = { delta ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setTargetPostPrandialMaxMgDl(settings.targetPostPrandialMaxMgDl + delta)
                        },
                    )
                }
            }

            item(key = "preview_header") {
                UgoSectionHeader(
                    title = "Anteprima live su pasto tipo",
                    subtitle = "80g pasta + 10g olio EVO + 100g petto di pollo",
                )
            }

            item(key = "preview_curve") {
                GlycemicCurveCard(
                    simulation = sampleSimulation,
                    targetMaxMgDl = profile.targetPostPrandialMaxMgDl,
                    onAdjustBaseline = { delta ->
                        previewBaselineMgDl = (previewBaselineMgDl + delta).coerceIn(40, 400)
                    },
                    onSetBaseline = { value ->
                        previewBaselineMgDl = value.coerceIn(40, 400)
                    },
                )
            }

            item(key = "disclaimer") {
                StatusBanner(
                    icon = Icons.Outlined.Info,
                    title = "Curva senza insulina / uso educativo",
                    subtitle = "Con il preset \"Diabete / Impatto pieno\" la curva mostra l'assorbimento cumulativo senza rientro spontaneo (in assenza di bolo). La timeline giornaliera resta comunque nascosta con questo preset o quando la curva è disattivata. Non sostituisce glucometro, CGM o indicazioni mediche.",
                    tone = BannerTone.INFO,
                )
            }
        }
    }
}

@Composable
private fun ParameterSliderWithSteppers(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Int,
    onValueChange: (Float) -> Unit,
    onStep: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepperChip(
                text = "-$step",
                enabled = value > valueRange.start,
                onClick = { onStep(-step) },
            )
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f),
            )
            StepperChip(
                text = "+$step",
                enabled = value < valueRange.endInclusive,
                onClick = { onStep(step) },
            )
        }
    }
}

@Composable
private fun StepperChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.7f else 0.3f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
