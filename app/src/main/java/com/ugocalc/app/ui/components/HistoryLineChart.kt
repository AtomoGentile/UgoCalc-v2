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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.ui.theme.UgoSemanticColors

/**
 * Grafico a linee per seguire l'andamento nel tempo di CHO e grassi separatamente.
 */
@Composable
fun HistoryLineChart(
    entries: List<AnalysisHistoryEntity>,
    modifier: Modifier = Modifier,
) {
    val choColor = UgoSemanticColors.cho
    val grassiColor = UgoSemanticColors.fat
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val maxValue = entries.maxOfOrNull { maxOf(it.totalChoG, it.totalGrassiG) } ?: 0.0

    Column(modifier = modifier) {
        if (entries.size < 2 || maxValue <= 0.0) {
            Text(
                text = "Servono almeno due analisi in questo intervallo per disegnare una linea",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                val horizontalPad = 8.dp.toPx()
                val usableWidth = (size.width - horizontalPad * 2).coerceAtLeast(1f)
                val baselineY = size.height * 0.88f
                val chartHeight = baselineY - 12.dp.toPx()
                val stepX = if (entries.size > 1) usableWidth / (entries.size - 1) else 0f

                drawLine(
                    color = axisColor,
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1.5f,
                )

                fun points(values: List<Double>) = values.mapIndexed { index, value ->
                    Offset(
                        x = horizontalPad + index * stepX,
                        y = baselineY - (value / maxValue * chartHeight).toFloat(),
                    )
                }

                val choPoints = points(entries.map { it.totalChoG })
                val grassiPoints = points(entries.map { it.totalGrassiG })

                listOf(choColor to choPoints, grassiColor to grassiPoints).forEach { (color, pts) ->
                    if (pts.size > 1) {
                        val path = Path().apply {
                            moveTo(pts[0].x, baselineY)
                            pts.forEach { lineTo(it.x, it.y) }
                            lineTo(pts.last().x, baselineY)
                            close()
                        }
                        drawPath(path, color = color.copy(alpha = 0.14f))
                    }
                }

                listOf(choColor to choPoints, grassiColor to grassiPoints).forEach { (color, pts) ->
                    for (i in 0 until pts.size - 1) {
                        drawLine(
                            color = color,
                            start = pts[i],
                            end = pts[i + 1],
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    pts.forEach { point ->
                        drawCircle(color = color, radius = 4.dp.toPx(), center = point)
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendDot(choColor, "CHO")
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
