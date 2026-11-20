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
) {
    val safeCho = choPercent.coerceIn(0.0, 100.0)
    val safeGrassi = grassiPercent.coerceIn(0.0, 100.0)
    val total = safeCho + safeGrassi
    val hasData = total > 0.0

    val choColor = UgoSemanticColors.cho
    val grassiColor = UgoSemanticColors.fat
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
                    val grassiFraction = 1f - choFraction
                    val hasBoth = choFraction > 0.01f && grassiFraction > 0.01f
                    val gap = if (hasBoth) 3f else 0f

                    if (choFraction > 0f) {
                        drawArc(
                            color = choColor,
                            startAngle = -90f + gap / 2f,
                            sweepAngle = (choFraction * 360f - gap).coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                    }
                    if (grassiFraction > 0f) {
                        drawArc(
                            color = grassiColor,
                            startAngle = -90f + choFraction * 360f + gap / 2f,
                            sweepAngle = (grassiFraction * 360f - gap).coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (hasData) {
                    Text(
                        text = "${"%.0f".format(safeCho)}%",
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
            val choLabelValue = if (choGrams > 0.0) {
                "${"%.0f".format(safeCho)}% · ${"%.1f".format(choGrams)}g"
            } else {
                "${"%.0f".format(safeCho)}%"
            }
            val grassiLabelValue = if (grassiGrams != null && grassiGrams > 0.0) {
                "${"%.0f".format(safeGrassi)}% · ${"%.1f".format(grassiGrams)}g"
            } else {
                "${"%.0f".format(safeGrassi)}%"
            }

            MacroPill(
                label = "CHO",
                value = choLabelValue,
                containerColor = UgoSemanticColors.choContainer,
                contentColor = UgoSemanticColors.onChoContainer,
            )
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
