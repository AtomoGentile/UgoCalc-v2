package com.ugocalc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ugocalc.app.domain.model.Confidence

/**
 * Indicatore "livello di sicurezza" (§2.2): colorato con il colore primario dell'app se la stima
 * percentuale della confidenza supera la soglia impostata dall'utente, altrimenti in grigio neutro.
 * Visibile solo se l'utente ha attivato l'opzione nelle Impostazioni (controllo lasciato al chiamante).
 */
@Composable
fun ConfidenceBadge(
    confidence: Confidence,
    thresholdPercent: Int,
    modifier: Modifier = Modifier,
) {
    val percent = Confidence.toPercentEstimate(confidence)
    val isAboveThreshold = percent >= thresholdPercent
    val color = if (isAboveThreshold) MaterialTheme.colorScheme.primary else Color(0xFF9E9E9E)
    Text(
        text = "Confidenza: ${confidence.label()} (~$percent%)",
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun Confidence.label(): String = when (this) {
    Confidence.ALTO -> "Alta"
    Confidence.MEDIO -> "Media"
    Confidence.BASSO -> "Bassa"
}
