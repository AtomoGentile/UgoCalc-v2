package com.ugocalc.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun SplashScreen(isLoading: Boolean = true) {
	var targetAlpha by remember { mutableFloatStateOf(1f) }
	val alpha by animateFloatAsState(
		targetValue = targetAlpha,
		animationSpec = androidx.compose.animation.core.tween(
			durationMillis = if (isLoading) 0 else 600,
			easing = androidx.compose.animation.core.EaseInOutQuad
		),
		label = "splash_alpha"
	)

	LaunchedEffect(isLoading) {
		if (!isLoading) {
			targetAlpha = 0f
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color(0xFF001F1A))
			.alpha(alpha),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = "UgoCalc",
			fontSize = 48.sp,
			fontWeight = FontWeight.Bold,
			color = Color(0xFF00D97F)
		)
	}
}
