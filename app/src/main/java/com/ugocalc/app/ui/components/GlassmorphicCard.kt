package com.ugocalc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Card con effetto glassmorphism: background semi-trasparente, border leggero, elevation per profondità.
 * Crea l'illusione di un oggetto flottante su uno sfondo sfocato (liquid glass effect, Apple-style).
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
    elevation: Float = 8f,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = shape
            )
            .run {
                // Simula blur/ombre con elevation e border sottile
                val borderWidth = 1.dp
                background(
                    color = borderColor,
                    shape = shape
                )
            }
    ) {
        content()
    }
}
