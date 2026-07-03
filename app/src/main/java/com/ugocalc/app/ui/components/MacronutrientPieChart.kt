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
import androidx.compose.ui.unit.dp
import com.ugocalc.app.ui.theme.UgoGrey

/**
 * Grafico a torta a 2 spicchi CHO/grassi, dove CHO% + Grassi% = 100% (proporzione semplice tra i
 * due grammi totali, non un valore calorico). Mostrato nella schermata finale e nel dettaglio
 * cronologia, accanto ai totali in grammi già visualizzati. CHO usa il colore primario del tema e
 * grassi il secondario (§ personalizzazione colori), così restano coerenti con la scelta dell'utente.
 */
@Composable
fun MacronutrientPieChart(
    choPercent: Double,
    grassiPercent: Double,
    modifier: Modifier = Modifier,
) {
    val hasData = choPercent + grassiPercent > 0.0
    val choColor = MaterialTheme.colorScheme.primary
    val grassiColor = MaterialTheme.colorScheme.secondary

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(120.dp)) {
            if (!hasData) {
                drawArc(color = UgoGrey, startAngle = 0f, sweepAngle = 360f, useCenter = true)
            } else {
                val choSweep = (choPercent / 100.0 * 360.0).toFloat()
                drawArc(color = choColor, startAngle = -90f, sweepAngle = choSweep, useCenter = true)
                drawArc(color = grassiColor, startAngle = -90f + choSweep, sweepAngle = 360f - choSweep, useCenter = true)
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Text(
            text = "$label: ${"%.0f".format(percent)}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
