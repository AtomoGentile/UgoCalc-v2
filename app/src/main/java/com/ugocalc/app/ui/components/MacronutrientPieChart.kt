package com.ugocalc.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ugocalc.app.ui.theme.UgoGrey
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MacronutrientPieChart(
    choPercent: Double,
    grassiPercent: Double,
    modifier: Modifier = Modifier,
    choGrams: Double = 0.0,
) {
    val hasData = choPercent + grassiPercent > 0.0
    val choColor = MaterialTheme.colorScheme.primary
    val grassiColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(140.dp)) {
                if (!hasData) {
                    drawArc(color = UgoGrey, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = center - androidx.compose.ui.geometry.Offset(50.dp.toPx(), 50.dp.toPx()), size = androidx.compose.ui.geometry.Size(100.dp.toPx(), 100.dp.toPx()))
                } else {
                    val radius = 50.dp.toPx()
                    val innerRadius = 30.dp.toPx()
                    val choSweep = (choPercent / 100.0 * 360.0).toFloat()

                    drawArc(color = choColor, startAngle = -90f, sweepAngle = choSweep, useCenter = false, topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                    drawArc(color = grassiColor, startAngle = -90f + choSweep, sweepAngle = 360f - choSweep, useCenter = false, topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))

                    drawCircle(color = surfaceColor, center = center, radius = innerRadius)
                }
            }
            if (hasData) {
                Text(
                    text = "${"%.0f".format(choGrams)}\ng",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            LegendRow(color = choColor, label = "CHO", percent = choPercent)
            LegendRow(color = grassiColor, label = "Grassi", percent = grassiPercent)
            if (!hasData) {
                Text("Nessun dato disponibile", style = MaterialTheme.typography.bodySmall, color = UgoGrey)
            }
        }
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, label: String, percent: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Text(
            text = "$label: ${"%.0f".format(percent)}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
