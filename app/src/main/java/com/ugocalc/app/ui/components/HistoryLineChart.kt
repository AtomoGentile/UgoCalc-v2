package com.ugocalc.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.ui.theme.UgoGrey

/**
 * Alternativa a colonne (§ richiesta utente "scegliere il grafico"): stessa fonte dati di
 * [HistoryColumnChart], ma CHO e grassi come due linee separate invece di due segmenti impilati —
 * più leggibile per seguire l'andamento nel tempo dei due valori indipendentemente.
 */
@Composable
fun HistoryLineChart(
    entries: List<AnalysisHistoryEntity>,
    modifier: Modifier = Modifier,
) {
    val choColor = MaterialTheme.colorScheme.primary
    val grassiColor = MaterialTheme.colorScheme.secondary
    val maxValue = entries.maxOfOrNull { maxOf(it.totalChoG, it.totalGrassiG) } ?: 0.0

    Column(modifier = modifier) {
        if (entries.size < 2 || maxValue <= 0.0) {
            Text(
                "Servono almeno due analisi in questo intervallo per disegnare una linea",
                style = MaterialTheme.typography.bodySmall,
                color = UgoGrey,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                val baselineY = size.height * 0.85f
                val chartHeight = baselineY
                val axisColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)
                val stepX = if (entries.size > 1) size.width / (entries.size - 1) else 0f


                // Baseline
                drawLine(color = axisColor, start = Offset(0f, baselineY), end = Offset(size.width, baselineY), strokeWidth = 1f)

                fun points(values: List<Double>) = values.mapIndexed { index, value ->
                    Offset(index * stepX, baselineY - (value / maxValue * chartHeight).toFloat())
                }

                val choPoints = points(entries.map { it.totalChoG })
                val grassiPoints = points(entries.map { it.totalGrassiG })

                // Area fill under lines (semi-transparent)
                listOf(choColor to choPoints, grassiColor to grassiPoints).forEach { (color, points) ->
                    if (points.size > 1) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points[0].x, baselineY)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, baselineY)
                            close()
                        }
                        drawPath(path, color = color.copy(alpha = 0.15f))
                    }
                }

                // Lines and points
                listOf(choColor to choPoints, grassiColor to grassiPoints).forEach { (color, points) ->
                    for (i in 0 until points.size - 1) {
                        drawLine(color = color, start = points[i], end = points[i + 1], strokeWidth = 4f)
                    }
                    points.forEach { point -> drawCircle(color = color, radius = 5f, center = point) }
                }
            }
            Row(modifier = Modifier.padding(top = 12.dp)) {
                LegendDot(choColor, "CHO")
                LegendDot(grassiColor, "Grassi", modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
    }
}
