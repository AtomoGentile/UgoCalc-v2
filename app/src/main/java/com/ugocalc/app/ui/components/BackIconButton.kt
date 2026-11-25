package com.ugocalc.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Pulsante indietro circolare stile Claude per il `navigationIcon` di un `TopAppBar`. */
@Composable
fun BackIconButton(onBack: () -> Unit) {
    FloatingCircleIconButton(
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = "Indietro",
        onClick = onBack,
        size = 38.dp,
        iconSize = 19.dp,
        modifier = Modifier.padding(start = 10.dp, end = 6.dp),
    )
}
