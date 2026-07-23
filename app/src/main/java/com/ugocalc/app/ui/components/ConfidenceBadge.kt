package com.ugocalc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.ugocalc.app.domain.model.ConfidenceScore
import com.ugocalc.app.domain.model.ConfidenceBand

@Composable
fun ConfidenceBadge(
    confidence: ConfidenceScore,
    alertThresholdPercent: Int = ConfidenceScore.DEFAULT_ALERT_THRESHOLD_PERCENT,
    modifier: Modifier = Modifier,
) {
    val band = ConfidenceScore.band(confidence)
    val bandLabel = ConfidenceScore.bandLabel(confidence)
    val showAlert = ConfidenceScore.needsAlert(confidence, alertThresholdPercent)
    val bgColor = when (band) {
        ConfidenceBand.GREEN -> Color(0xFF4CAF50)
        ConfidenceBand.AMBER -> Color(0xFFFFC107)
        ConfidenceBand.ORANGE -> Color(0xFFFF9800)
        ConfidenceBand.RED -> Color(0xFFF44336)
    }
    val textColor = if (band == ConfidenceBand.AMBER) Color.Black else Color.White

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (showAlert) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Avvertimento",
                tint = textColor,
                modifier = Modifier.padding(end = 4.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = "${confidence.percent}% — $bandLabel",
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
