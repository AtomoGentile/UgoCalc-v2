package com.ugocalc.app.ui.components

import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ugocalc.app.R

@Composable
fun SplashScreen(isLoading: Boolean = true) {
    val isDark = isSystemInDarkTheme()
    var targetAlpha by remember { mutableFloatStateOf(1f) }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = if (isLoading) 0 else 500,
            easing = EaseInOutQuad,
        ),
        label = "splash_alpha",
    )

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            targetAlpha = 0f
        }
    }

    val backgroundColor = if (isDark) Color(0xFF0E1113) else Color(0xFFFAF9F6)
    val glowColor = if (isDark) Color(0xFF34D399) else Color(0xFF15803D)
    val titleColor = if (isDark) Color(0xFFF4F6F8) else Color(0xFF18181B)
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF52525B)
    val cardColor = if (isDark) Color(0xFF15191C) else Color(0xFFFFFFFF)
    val borderColor = if (isDark) Color(0xFF2A3339) else Color(0xFFE5E2DC)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        // Sottile bagliore smeraldo dietro il logo
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (isDark) 0.24f else 0.16f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Surface(
                modifier = Modifier.size(116.dp),
                shape = RoundedCornerShape(28.dp),
                color = cardColor,
                shadowElevation = if (isDark) 0.dp else 6.dp,
                border = BorderStroke(1.dp, borderColor),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ugocalc_logo),
                    contentDescription = "UgoCalc",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "UgoCalc",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = titleColor,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Calcolo CHO e Grassi intelligente",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = subtitleColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .size(width = 120.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50)),
                color = glowColor,
                trackColor = glowColor.copy(alpha = 0.2f),
            )
        }
    }
}
