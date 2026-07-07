package com.ugocalc.app.ui.bias

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Placeholder screen for displaying V2 analysis result when V2 is promoted to visible mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2ResultScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risultato V2") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
            )
        }
    ) { padding ->
        Text(
            "Qui verrà mostrato il risultato dell'analisi V2, con la catena di calcolo e i flag di verifica.",
            modifier = Modifier.padding(padding).padding(16.dp)
        )
    }
}
