package com.ugocalc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ugocalc.app.domain.model.ConfidenceBand
import com.ugocalc.app.domain.model.ConfidenceScore

@Composable
fun ConfidenceBadge(
    confidence: ConfidenceScore,
    alertThresholdPercent: Int = ConfidenceScore.DEFAULT_ALERT_THRESHOLD_PERCENT,
    modifier: Modifier = Modifier,
) {
    val isDark = isDarkSurfaceTheme()
    val band = ConfidenceScore.band(confidence)
    val bandLabel = ConfidenceScore.bandLabel(confidence)
    val showAlert = ConfidenceScore.needsAlert(confidence, alertThresholdPercent)

    val (container, content, border) = when (band) {
        ConfidenceBand.GREEN -> if (isDark) {
            Triple(Color(0xFF064E3B), Color(0xFFA7F3D0), Color(0xFF34D399).copy(alpha = 0.45f))
        } else {
            Triple(Color(0xFFDCFCE7), Color(0xFF14532D), Color(0xFF16A34A).copy(alpha = 0.35f))
        }
        ConfidenceBand.AMBER -> if (isDark) {
            Triple(Color(0xFF78350F), Color(0xFFFEF3C7), Color(0xFFFBBF24).copy(alpha = 0.45f))
        } else {
            Triple(Color(0xFFFEF3C7), Color(0xFF78350F), Color(0xFFD97706).copy(alpha = 0.35f))
        }
        ConfidenceBand.ORANGE -> if (isDark) {
            Triple(Color(0xFF7C2D12), Color(0xFFFFEDD5), Color(0xFFFB923C).copy(alpha = 0.45f))
        } else {
            Triple(Color(0xFFFFEDD5), Color(0xFF7C2D12), Color(0xFFEA580C).copy(alpha = 0.35f))
        }
        ConfidenceBand.RED -> if (isDark) {
            Triple(Color(0xFF7F1D1D), Color(0xFFFECACA), Color(0xFFF87171).copy(alpha = 0.45f))
        } else {
            Triple(Color(0xFFFEE2E2), Color(0xFF7F1D1D), Color(0xFFDC2626).copy(alpha = 0.35f))
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (showAlert) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Avvertimento",
                    tint = content,
                    modifier = Modifier.size(14.dp),
                )
            } else if (band == ConfidenceBand.GREEN) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = "${confidence.percent}% — $bandLabel",
                color = content,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
