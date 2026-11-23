package com.ugocalc.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ugocalc.app.domain.glycemic.DailyGlycemicTimeline
import com.ugocalc.app.domain.glycemic.FoodGlycemicBreakdown
import com.ugocalc.app.domain.glycemic.GlycemicLoadLevel
import com.ugocalc.app.domain.glycemic.GlycemicSimulationResult
import com.ugocalc.app.ui.theme.UgoSemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Scheda interattiva della curva glicemica stimata post-prandiale (0..240 min) e Carico Glicemico.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlycemicCurveCard(
    simulation: GlycemicSimulationResult,
    targetMaxMgDl: Double,
    onAdjustBaseline: ((Int) -> Unit)? = null,
    onSetBaseline: ((Int) -> Unit)? = null,
    onOpenGlycemicSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var scrubbedIndex by remember(simulation.points) { mutableStateOf<Int?>(null) }
    var breakdownExpanded by remember { mutableStateOf(false) }
    var showBaselineDialog by remember { mutableStateOf(false) }

    val (glContainer, glContent, glLevelLabel) = when (simulation.glycemicLoadLevel) {
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

    val curveAccent = when (simulation.glycemicLoadLevel) {
        GlycemicLoadLevel.BASSO -> UgoSemanticColors.success
        GlycemicLoadLevel.MEDIO -> UgoSemanticColors.warning
        GlycemicLoadLevel.ALTO -> MaterialTheme.colorScheme.error
    }

    UgoCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = curveAccent.copy(alpha = 0.25f),
        contentPadding = PaddingValues(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(curveAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShowChart,
                        contentDescription = null,
                        tint = curveAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "Curva Glicemica & Carico",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Badges: GL badge, IG medio chip, FPU chip
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = glContainer,
            ) {
                Text(
                    text = "GL ${"%.1f".format(Locale.ITALIAN, simulation.totalGlycemicLoad)} · $glLevelLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = glContent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            MacroPill(
                label = "IG medio",
                value = "${simulation.effectiveMealGi.roundToInt()}",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            if (simulation.fpuCount >= 0.5) {
                MacroPill(
                    label = "FPU",
                    value = "%.1f".format(Locale.ITALIAN, simulation.fpuCount),
                    containerColor = UgoSemanticColors.proteinContainer,
                    contentColor = UgoSemanticColors.onProteinContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live scrub readout pill when user taps/drags on chart
        val activeScrubPoint = scrubbedIndex?.let { simulation.points.getOrNull(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (activeScrubPoint != null) {
                    "+${activeScrubPoint.minutesFromMeal} min · ${activeScrubPoint.glucoseMgDl.roundToInt()} mg/dL"
                } else {
                    "Tocca o trascina sul grafico per ispezionare"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (activeScrubPoint != null) FontWeight.Bold else FontWeight.Normal,
                color = if (activeScrubPoint != null) curveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (activeScrubPoint != null) {
                Text(
                    text = "Ripristina",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { scrubbedIndex = null }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Interactive Canvas Curve (0..240 min)
        GlycemicMealChartCanvas(
            simulation = simulation,
            targetMaxMgDl = targetMaxMgDl,
            curveColor = curveAccent,
            scrubbedIndex = scrubbedIndex,
            onScrubIndexChange = { idx -> scrubbedIndex = idx },
        )

        // Hour axis labels (0h .. 4h)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("0h", "1h", "2h", "3h", "4h").forEach { tick ->
                Text(
                    text = tick,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Legend for target & FPU curve if applicable
        if (simulation.fpuCount >= 0.5) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendSwatch(color = curveAccent, label = "Totale stimato", dashed = false)
                LegendSwatch(color = UgoSemanticColors.protein, label = "Onda grassi/proteine (FPU)", dashed = true)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4-metric strip: Picco, Tempo picco, A 2 ore, Rientro
        val returnLabel = simulation.returnToBaselineMinutes?.let { mins ->
            if (mins == 0) "Stabile" else "$mins min"
        } ?: "> 4h"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlycemicMiniMetric(
                label = "Picco",
                value = "${simulation.peakMgDl.roundToInt()}",
                unit = "mg/dL",
                accent = curveAccent,
                modifier = Modifier.weight(1f),
            )
            GlycemicMiniMetric(
                label = "Tempo picco",
                value = "${simulation.peakTimeMinutes}",
                unit = "min",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            GlycemicMiniMetric(
                label = "A 2 ore",
                value = "${simulation.glucoseAt120MinMgDl.roundToInt()}",
                unit = "mg/dL",
                accent = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            GlycemicMiniMetric(
                label = "Rientro",
                value = returnLabel,
                unit = "",
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        // Inline pre-meal glycemia stepper + Profilo link
        if (onAdjustBaseline != null || onOpenGlycemicSettings != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = onSetBaseline != null) { showBaselineDialog = true }
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = if (onSetBaseline != null) "Glicemia pre-pasto (tocca per inserire)" else "Glicemia pre-pasto",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${simulation.baselineMgDl.roundToInt()} mg/dL",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (onSetBaseline != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (onAdjustBaseline != null) {
                            BaselineStepButton(
                                label = "-10",
                                enabled = simulation.baselineMgDl > 40.0,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onAdjustBaseline(-10)
                                },
                            )
                            BaselineStepButton(
                                label = "+10",
                                enabled = simulation.baselineMgDl < 400.0,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onAdjustBaseline(10)
                                },
                            )
                        }
                        if (onOpenGlycemicSettings != null) {
                            TextButton(
                                onClick = onOpenGlycemicSettings,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Profilo",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Insight text
        if (simulation.insightText.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = simulation.insightText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Expandable per-food GI / GL breakdown
        if (simulation.perFoodLoads.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { breakdownExpanded = !breakdownExpanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Dettaglio IG / Carico per alimento (${simulation.perFoodLoads.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (breakdownExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (breakdownExpanded) "Comprimi" else "Espandi",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(visible = breakdownExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    simulation.perFoodLoads.forEach { food ->
                        PerFoodGlycemicRow(food)
                    }
                }
            }
        }

        // Disclaimer
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Curva di assorbimento del pasto senza insulina/bolo (stima educativa — non sostituisce glucometro, CGM o indicazioni mediche).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }

    if (showBaselineDialog && onSetBaseline != null) {
        var inputText by remember(simulation.baselineMgDl) {
            mutableStateOf(simulation.baselineMgDl.roundToInt().toString())
        }
        val parsedValue = inputText.trim().toIntOrNull()
        val isValid = parsedValue != null && parsedValue in 40..400
        AlertDialog(
            onDismissRequest = { showBaselineDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Glicemia pre-pasto", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Inserisci la glicemia misurata prima di questo pasto (40–400 mg/dL).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { next ->
                            inputText = next.filter { it.isDigit() }.take(3)
                        },
                        label = { Text("mg/dL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = inputText.isNotEmpty() && !isValid,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (parsedValue != null) {
                            onSetBaseline(parsedValue.coerceIn(40, 400))
                            showBaselineDialog = false
                        }
                    },
                    enabled = isValid,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Applica")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBaselineDialog = false }) {
                    Text("Annulla")
                }
            },
        )
    }
}

@Composable
private fun GlycemicMealChartCanvas(
    simulation: GlycemicSimulationResult,
    targetMaxMgDl: Double,
    curveColor: Color,
    scrubbedIndex: Int?,
    onScrubIndexChange: (Int?) -> Unit,
) {
    val points = simulation.points
    if (points.size < 2) return

    val targetBandColor = UgoSemanticColors.success.copy(alpha = 0.11f)
    val targetLineColor = UgoSemanticColors.success.copy(alpha = 0.65f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val fpCurveColor = UgoSemanticColors.protein
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    val minY = min(65.0, simulation.baselineMgDl - 10.0)
    val rawMaxY = max(targetMaxMgDl + 20.0, simulation.peakMgDl + 15.0)
    val maxY = max(rawMaxY, minY + 60.0)
    val ySpan = (maxY - minY).coerceAtLeast(1.0)

    var canvasWidthPx by remember { mutableStateOf(1f) }

    fun indexForOffsetX(x: Float): Int {
        val fraction = (x / canvasWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return (fraction * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
                .onSizeChanged { canvasWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        onScrubIndexChange(indexForOffsetX(offset.x))
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onScrubIndexChange(indexForOffsetX(change.position.x))
                    }
                },
        ) {
            val padTop = 10.dp.toPx()
            val padBottom = 8.dp.toPx()
            val padHoriz = 6.dp.toPx()
            val chartW = (size.width - padHoriz * 2).coerceAtLeast(1f)
            val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)

            fun xForMin(minutes: Int): Float =
                padHoriz + (minutes.toFloat() / 240f).coerceIn(0f, 1f) * chartW

            fun yForGlucose(mgDl: Double): Float {
                val norm = ((mgDl - minY) / ySpan).toFloat().coerceIn(0f, 1f)
                return padTop + chartH * (1f - norm)
            }

            // Target range shaded band (70 .. targetMaxMgDl)
            val bandTopY = yForGlucose(targetMaxMgDl)
            val bandBottomY = yForGlucose(70.0)
            if (bandBottomY > bandTopY) {
                drawRect(
                    color = targetBandColor,
                    topLeft = Offset(padHoriz, bandTopY),
                    size = Size(chartW, bandBottomY - bandTopY),
                )
            }

            // Hour grid lines (0h, 1h, 2h, 3h, 4h)
            listOf(0, 60, 120, 180, 240).forEach { minute ->
                val gx = xForMin(minute)
                drawLine(
                    color = gridColor,
                    start = Offset(gx, padTop),
                    end = Offset(gx, padTop + chartH),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Dashed upper threshold line (targetMaxMgDl)
            drawLine(
                color = targetLineColor,
                start = Offset(padHoriz, bandTopY),
                end = Offset(padHoriz + chartW, bandTopY),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )

            // Secondary dotted FPU curve (baseline + fatProteinDeltaMgDl) when fpuCount >= 0.5
            if (simulation.fpuCount >= 0.5) {
                val fpOffsets = points.map { pt ->
                    Offset(xForMin(pt.minutesFromMeal), yForGlucose(simulation.baselineMgDl + pt.fatProteinDeltaMgDl))
                }
                val fpPath = buildSmoothCurvePath(fpOffsets)
                drawPath(
                    path = fpPath,
                    color = fpCurveColor.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    ),
                )
            }

            // Main smooth cubic Bézier glucose curve + soft gradient fill
            val mainOffsets = points.map { pt ->
                Offset(xForMin(pt.minutesFromMeal), yForGlucose(pt.glucoseMgDl))
            }
            val linePath = buildSmoothCurvePath(mainOffsets)
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(mainOffsets.last().x, padTop + chartH)
                lineTo(mainOffsets.first().x, padTop + chartH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(curveColor.copy(alpha = 0.28f), curveColor.copy(alpha = 0.02f)),
                    startY = padTop,
                    endY = padTop + chartH,
                ),
            )
            drawPath(
                path = linePath,
                color = curveColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )

            // Peak marker dot
            val peakOffset = Offset(xForMin(simulation.peakTimeMinutes), yForGlucose(simulation.peakMgDl))
            drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = peakOffset)
            drawCircle(color = curveColor, radius = 4.dp.toPx(), center = peakOffset)

            // Interactive scrub indicator line & active dot
            if (scrubbedIndex != null && scrubbedIndex in mainOffsets.indices) {
                val scrubOffset = mainOffsets[scrubbedIndex]
                drawLine(
                    color = curveColor.copy(alpha = 0.7f),
                    start = Offset(scrubOffset.x, padTop),
                    end = Offset(scrubOffset.x, padTop + chartH),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(color = surfaceColor, radius = 7.dp.toPx(), center = scrubOffset)
                drawCircle(color = curveColor, radius = 5.dp.toPx(), center = scrubOffset)
            }
        }

        // Subtle target label inside top-right corner of chart
        Text(
            text = "Target ≤ ${targetMaxMgDl.roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 6.dp, top = 2.dp),
        )
    }
}

/**
 * Scheda compatta della timeline glicemica giornaliera multi-pasto per Home e Cronologia.
 */
@Composable
fun DailyGlycemicTimelineCard(
    timeline: DailyGlycemicTimeline,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (timeline.points.size < 2) return

    var scrubbedIndex by remember(timeline.points) { mutableStateOf<Int?>(null) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.ITALIAN) }
    val overTarget = timeline.peakMgDl > timeline.targetMaxMgDl
    val curveColor = if (overTarget) UgoSemanticColors.warning else UgoSemanticColors.success

    UgoCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = curveColor.copy(alpha = 0.25f),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Timeline glicemica di oggi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Picco ${timeline.peakMgDl.roundToInt()} mg/dL · GL totale ${"%.1f".format(Locale.ITALIAN, timeline.totalDailyGl)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onOpenSettings != null) {
                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Profilo")
                }
            }
        }

        val activePoint = scrubbedIndex?.let { timeline.points.getOrNull(it) }
        if (activePoint != null) {
            Text(
                text = "${timeFormat.format(Date(activePoint.epochMillis))} · ${activePoint.glucoseMgDl.roundToInt()} mg/dL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = curveColor,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val points = timeline.points
        val startMs = points.first().epochMillis
        val endMs = points.last().epochMillis
        val spanMs = (endMs - startMs).coerceAtLeast(1L).toDouble()
        val minY = min(65.0, points.minOf { it.glucoseMgDl } - 8.0)
        val maxY = max(timeline.targetMaxMgDl + 20.0, timeline.peakMgDl + 15.0)
        val ySpan = (maxY - minY).coerceAtLeast(1.0)

        val targetBandColor = UgoSemanticColors.success.copy(alpha = 0.11f)
        val targetLineColor = UgoSemanticColors.success.copy(alpha = 0.6f)
        val markerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        val surfaceColor = MaterialTheme.colorScheme.surface
        var canvasWidthPx by remember { mutableStateOf(1f) }

        fun indexForOffsetX(x: Float): Int {
            val fraction = (x / canvasWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            return (fraction * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .onSizeChanged { canvasWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        scrubbedIndex = indexForOffsetX(offset.x)
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        scrubbedIndex = indexForOffsetX(change.position.x)
                    }
                },
        ) {
            val padTop = 8.dp.toPx()
            val padBottom = 6.dp.toPx()
            val padHoriz = 6.dp.toPx()
            val chartW = (size.width - padHoriz * 2).coerceAtLeast(1f)
            val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)

            fun xForEpoch(epochMillis: Long): Float =
                padHoriz + (((epochMillis - startMs) / spanMs).toFloat().coerceIn(0f, 1f)) * chartW

            fun yForGlucose(mgDl: Double): Float {
                val norm = ((mgDl - minY) / ySpan).toFloat().coerceIn(0f, 1f)
                return padTop + chartH * (1f - norm)
            }

            val bandTopY = yForGlucose(timeline.targetMaxMgDl)
            val bandBottomY = yForGlucose(70.0)
            if (bandBottomY > bandTopY) {
                drawRect(
                    color = targetBandColor,
                    topLeft = Offset(padHoriz, bandTopY),
                    size = Size(chartW, bandBottomY - bandTopY),
                )
            }
            drawLine(
                color = targetLineColor,
                start = Offset(padHoriz, bandTopY),
                end = Offset(padHoriz + chartW, bandTopY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )

            // Meal time vertical markers
            timeline.mealMarkers.forEach { (mealEpoch, _) ->
                val mx = xForEpoch(mealEpoch)
                drawLine(
                    color = markerColor,
                    start = Offset(mx, padTop),
                    end = Offset(mx, padTop + chartH),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                )
            }

            val offsets = points.map { pt -> Offset(xForEpoch(pt.epochMillis), yForGlucose(pt.glucoseMgDl)) }
            val linePath = buildSmoothCurvePath(offsets)
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(offsets.last().x, padTop + chartH)
                lineTo(offsets.first().x, padTop + chartH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(curveColor.copy(alpha = 0.25f), curveColor.copy(alpha = 0.02f)),
                    startY = padTop,
                    endY = padTop + chartH,
                ),
            )
            drawPath(
                path = linePath,
                color = curveColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )

            scrubbedIndex?.let { idx ->
                if (idx in offsets.indices) {
                    val scrubOffset = offsets[idx]
                    drawLine(
                        color = curveColor.copy(alpha = 0.7f),
                        start = Offset(scrubOffset.x, padTop),
                        end = Offset(scrubOffset.x, padTop + chartH),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = scrubOffset)
                    drawCircle(color = curveColor, radius = 4.dp.toPx(), center = scrubOffset)
                }
            }
        }

        // Meal markers summary row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = timeFormat.format(Date(startMs)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${timeline.mealMarkers.size} ${if (timeline.mealMarkers.size == 1) "pasto" else "pasti"} oggi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = timeFormat.format(Date(endMs)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildSmoothCurvePath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (i in 0 until points.lastIndex) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val midX = (p0.x + p1.x) / 2f
        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
    }
}

@Composable
private fun GlycemicMiniMetric(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (unit.isNotBlank()) "$value $unit" else value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BaselineStepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.75f else 0.3f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun PerFoodGlycemicRow(item: FoodGlycemicBreakdown) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = item.foodName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.weightG.roundToInt()} g · CHO ${"%.1f".format(Locale.ITALIAN, item.choG)} g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = if (item.isGiEstimated) "IG ${item.gi} · stimato" else "IG ${item.gi}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Text(
                    text = "GL ${"%.1f".format(Locale.ITALIAN, item.glycemicLoad)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun LegendSwatch(
    color: Color,
    label: String,
    dashed: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dashed) {
            Canvas(modifier = Modifier.width(14.dp).height(4.dp)) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
