package com.ugocalc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Superficie tonale morbida con bordo sottile e clipping sugli angoli arrotondati.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = if (isDarkSurfaceTheme()) {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f)
    },
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
    elevation: Float = 8f,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color = backgroundColor, shape = shape)
            .border(width = 1.dp, color = borderColor, shape = shape),
    ) {
        content()
    }
}
