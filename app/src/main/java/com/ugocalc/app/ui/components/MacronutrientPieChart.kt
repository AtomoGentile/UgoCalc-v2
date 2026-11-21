package com.ugocalc.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ugocalc.app.ui.theme.UgoSemanticColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacronutrientPieChart(
    choPercent: Double,
    grassiPercent: Double,
    modifier: Modifier = Modifier,
    choGrams: Double = 0.0,
    grassiGrams: Double? = null,
    proteinePercent: Double = 0.0,
    proteineGrams: Double? = null,
) {
    val safeCho = choPercent.coerceIn(0.0, 100.0)
    val safeGrassi = grassiPercent.coerceIn(0.0, 100.0)
    val safeProteine = proteinePercent.coerceIn(0.0, 100.0)
    val total = safeCho + safeGrassi + safeProteine
    val hasData = total > 0.0

    val choColor = UgoSemanticColors.cho
    val grassiColor = UgoSemanticColors.fat
    val proteineColor = UgoSemanticColors.protein
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(136.dp),
        ) {
            Canvas(modifier = Modifier.size(136.dp)) {
                val strokeWidth = 18.dp.toPx()
                val inset = strokeWidth / 2f + 4.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

                if (!hasData) {
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                } else {
                    val choFraction = (safeCho / total).toFloat().coerceIn(0f, 1f)
                    val proteineFraction = (safeProteine / total).toFloat().coerceIn(0f, 1f)
                    val grassiFraction = (safeGrassi / total).toFloat().coerceIn(0f, 1f)
                    val segments = buildList {
                        if (choFraction > 0.005f) add(choFraction to choColor)
                        if (proteineFraction > 0.005f) add(proteineFraction to proteineColor)
                        if (grassiFraction > 0.005f) add(grassiFraction to grassiColor)
                    }
                    val gap = if (segments.size > 1) 3f else 0f
                    var currentStart = -90f
                    segments.forEach { (fraction, color) ->
                        val rawSweep = fraction * 360f
                        drawArc(
                            color = color,
                            startAngle = currentStart + gap / 2f,
                            sweepAngle = (rawSweep - gap).coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                        currentStart += rawSweep
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (hasData) {
                    val displayedChoPercent = (safeCho / total * 100.0)
                    Text(
                        text = "${"%.0f".format(displayedChoPercent)}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = choColor,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (choGrams > 0.0) "${"%.0f".format(choGrams)}g CHO" else "CHO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val normCho = if (hasData) safeCho / total * 100.0 else 0.0
            val normProt = if (hasData) safeProteine / total * 100.0 else 0.0
            val normGrassi = if (hasData) safeGrassi / total * 100.0 else 0.0

            val choLabelValue = if (choGrams > 0.0) {
                "${"%.0f".format(normCho)}% · ${"%.1f".format(choGrams)}g"
            } else {
                "${"%.0f".format(normCho)}%"
            }
            val proteineLabelValue = if (proteineGrams != null && proteineGrams > 0.0) {
                "${"%.0f".format(normProt)}% · ${"%.1f".format(proteineGrams)}g"
            } else {
                "${"%.0f".format(normProt)}%"
            }
            val grassiLabelValue = if (grassiGrams != null && grassiGrams > 0.0) {
                "${"%.0f".format(normGrassi)}% · ${"%.1f".format(grassiGrams)}g"
            } else {
                "${"%.0f".format(normGrassi)}%"
            }

            MacroPill(
                label = "CHO",
                value = choLabelValue,
                containerColor = UgoSemanticColors.choContainer,
                contentColor = UgoSemanticColors.onChoContainer,
            )
            if (safeProteine > 0.0 || proteineGrams != null) {
                MacroPill(
                    label = "Proteine",
                    value = proteineLabelValue,
                    containerColor = UgoSemanticColors.proteinContainer,
                    contentColor = UgoSemanticColors.onProteinContainer,
                )
            }
            MacroPill(
                label = "Grassi",
                value = grassiLabelValue,
                containerColor = UgoSemanticColors.fatContainer,
                contentColor = UgoSemanticColors.onFatContainer,
            )
        }

        if (!hasData) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Nessun dato disponibile",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
