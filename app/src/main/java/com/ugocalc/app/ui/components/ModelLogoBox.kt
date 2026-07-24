package com.ugocalc.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Logo area per le model card.
 * - Se logoRes != null: mostra il drawable
 * - Altrimenti: placeholder colorato con le iniziali del provider (estratte dal subtitle)
 */
@Composable
fun ModelLogoBox(
    logoRes: Int?,
    displayName: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    if (logoRes != null) {
        val painter = remember(logoRes) { painterResource(id = logoRes) }
        Image(
            painter = painter,
            contentDescription = displayName,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        val providerName = remember(subtitle, displayName) {
            subtitle.substringBefore(" ·").trim().ifBlank { displayName }
        }
        val initials = remember(providerName) {
            providerName
                .split(" ", "-", "_")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "?" }
        }
        val bgColor = remember(providerName) { providerColor(providerName) }
        val shape = remember { RoundedCornerShape(8.dp) }
        Box(
            modifier = modifier
                .clip(shape)
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun providerColor(provider: String): Color = when {
    provider.contains("Meta", ignoreCase = true) -> Color(0xFF0064E0)
    provider.contains("Amazon", ignoreCase = true) -> Color(0xFFFF9900)
    provider.contains("Mistral", ignoreCase = true) -> Color(0xFFFF6F00)
    provider.contains("Cohere", ignoreCase = true) -> Color(0xFF39A7FF)
    provider.contains("Stability", ignoreCase = true) -> Color(0xFF6B2FFF)
    provider.contains("AI21", ignoreCase = true) -> Color(0xFF1A73E8)
    else -> Color(0xFF5C6BC0)
}
