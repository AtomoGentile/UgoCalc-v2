package com.ugocalc.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ugocalc.app.domain.model.LoadingMessages
import kotlinx.coroutines.delay

/**
 * Overlay mostrato durante l'attesa delle chiamate AI (§2.4 punti 3 e 5), con messaggio informale
 * scelto a rotazione casuale tra [LoadingMessages.ALL] ogni pochi secondi, per "alleggerire l'attesa".
 */
@Composable
fun LoadingOverlay(modifier: Modifier = Modifier) {
    var isVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(LoadingMessages.randomMessage()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
        while (true) {
            delay(2500)
            message = LoadingMessages.randomMessage()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(400)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
