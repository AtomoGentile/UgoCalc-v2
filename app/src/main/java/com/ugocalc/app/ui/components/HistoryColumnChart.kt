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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.ui.theme.UgoGrey

/**
 * Colonna per pasto (§ richiesta utente): CHO e grassi impilati in due colori (primario/secondario
 * del tema, coerenti con [MacronutrientPieChart]), una colonna per ogni analisi salvata
 * nell'intervallo scelto dall'utente in [HistoryScreen]. Ordinare [entries] dal più vecchio al più
 * recente prima di passarle qui, così le colonne procedono da sinistra a destra nel tempo.
 */
@Composable
fun HistoryColumnChart(
    entries: List<AnalysisHistoryEntity>,
    modifier: Modifier = Modifier,
) {
    val choColor = MaterialTheme.colorScheme.primary
    val grassiColor = MaterialTheme.colorScheme.secondary
    val maxTotal = entries.maxOfOrNull { it.totalChoG + it.totalGrassiG } ?: 0.0

    Column(modifier = modifier) {
        if (entries.isEmpty() || maxTotal <= 0.0) {
            Text(
                "Nessuna analisi in questo intervallo",
                style = MaterialTheme.typography.bodySmall,
                color = UgoGrey,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                val barSlot = size.width / entries.size
                val barWidth = barSlot * 0.6f
                val baselineY = size.height * 0.85f
                val chartHeight = baselineY
                val axisColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)

                // Baseline
                drawLine(color = axisColor, start = Offset(0f, baselineY), end = Offset(size.width, baselineY), strokeWidth = 1f)

                // Bars
                entries.forEachIndexed { index, entry ->
                    val choHeight = (entry.totalChoG / maxTotal * chartHeight).toFloat()
                    val grassiHeight = (entry.totalGrassiG / maxTotal * chartHeight).toFloat()
                    val x = index * barSlot + (barSlot - barWidth) / 2f
                    drawRect(
                        color = choColor,
                        topLeft = Offset(x, baselineY - choHeight),
                        size = Size(barWidth, choHeight),
                    )
                    drawRect(
                        color = grassiColor,
                        topLeft = Offset(x, baselineY - choHeight - grassiHeight),
                        size = Size(barWidth, grassiHeight),
                    )
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
