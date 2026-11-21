package com.ugocalc.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.ui.theme.UgoSemanticColors

/**
 * Colonna per pasto: CHO, proteine e grassi impilati, una colonna per ogni analisi salvata nell'intervallo.
 */
@Composable
fun HistoryColumnChart(
    entries: List<AnalysisHistoryEntity>,
    modifier: Modifier = Modifier,
) {
    val choColor = UgoSemanticColors.cho
    val protColor = UgoSemanticColors.protein
    val grassiColor = UgoSemanticColors.fat
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val maxTotal = entries.maxOfOrNull { it.totalChoG + it.totalProteineG + it.totalGrassiG } ?: 0.0

    Column(modifier = modifier) {
        if (entries.isEmpty() || maxTotal <= 0.0) {
            Text(
                text = "Nessuna analisi in questo intervallo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                val barSlot = size.width / entries.size
                val barWidth = (barSlot * 0.58f).coerceAtMost(36.dp.toPx())
                val baselineY = size.height * 0.88f
                val chartHeight = baselineY - 8.dp.toPx()
                val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                // Baseline
                drawLine(
                    color = axisColor,
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1.5f,
                )

                entries.forEachIndexed { index, entry ->
                    val choHeight = (entry.totalChoG / maxTotal * chartHeight).toFloat()
                    val protHeight = (entry.totalProteineG / maxTotal * chartHeight).toFloat()
                    val grassiHeight = (entry.totalGrassiG / maxTotal * chartHeight).toFloat()
                    val x = index * barSlot + (barSlot - barWidth) / 2f
                    if (choHeight > 0f) {
                        drawRoundRect(
                            color = choColor,
                            topLeft = Offset(x, baselineY - choHeight),
                            size = Size(barWidth, choHeight),
                            cornerRadius = if (protHeight <= 0f && grassiHeight <= 0f) radius else CornerRadius.Zero,
                        )
                    }
                    if (protHeight > 0f) {
                        drawRoundRect(
                            color = protColor,
                            topLeft = Offset(x, baselineY - choHeight - protHeight),
                            size = Size(barWidth, protHeight),
                            cornerRadius = if (grassiHeight <= 0f) radius else CornerRadius.Zero,
                        )
                    }
                    if (grassiHeight > 0f) {
                        drawRoundRect(
                            color = grassiColor,
                            topLeft = Offset(x, baselineY - choHeight - protHeight - grassiHeight),
                            size = Size(barWidth, grassiHeight),
                            cornerRadius = radius,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendDot(choColor, "CHO")
                LegendDot(protColor, "Proteine")
                LegendDot(grassiColor, "Grassi")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
    }
}
