package com.ugocalc.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * - Se logoRes != null: mostra il drawable con angoli arrotondati
 * - Altrimenti: badge colorato con le iniziali del provider
 */
@Composable
fun ModelLogoBox(
    logoRes: Int?,
    displayName: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val shape = remember { RoundedCornerShape(10.dp) }
    if (logoRes != null) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = displayName,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Inside,
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
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun providerColor(provider: String): Color = when {
    provider.contains("Anthropic", ignoreCase = true) || provider.contains("Claude", ignoreCase = true) -> Color(0xFF1F2937)
    provider.contains("OpenAI", ignoreCase = true) || provider.contains("GPT", ignoreCase = true) -> Color(0xFF10A37F)
    provider.contains("Google", ignoreCase = true) || provider.contains("Gemini", ignoreCase = true) || provider.contains("Vertex", ignoreCase = true) -> Color(0xFF1A73E8)
    provider.contains("Meta", ignoreCase = true) -> Color(0xFF0064E0)
    provider.contains("Amazon", ignoreCase = true) -> Color(0xFFD97706)
    provider.contains("Mistral", ignoreCase = true) -> Color(0xFFEA580C)
    provider.contains("Cohere", ignoreCase = true) -> Color(0xFF0284C7)
    provider.contains("Stability", ignoreCase = true) -> Color(0xFF6B2FFF)
    provider.contains("AI21", ignoreCase = true) -> Color(0xFF1A73E8)
    provider.contains("DeepSeek", ignoreCase = true) -> Color(0xFF2E3440)
    provider.contains("Qwen", ignoreCase = true) -> Color(0xFF4F46E5)
    provider.contains("Bedrock", ignoreCase = true) -> Color(0xFFD97706)
    else -> Color(0xFF5C6BC0)
}
