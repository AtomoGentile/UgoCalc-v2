package com.ugocalc.app.ui.bias

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

/** Placeholder UI for showing ambiguities resolved after M5 arbitration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbiguityResolutionScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ambiguità Risolta") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
            )
        }
    ) { padding ->
        Text(
            "Elenco delle ambiguità risolte con il nome originale, il nome scelto e il CHO/100g.",
            modifier = Modifier.padding(padding).padding(16.dp)
        )
    }
}
