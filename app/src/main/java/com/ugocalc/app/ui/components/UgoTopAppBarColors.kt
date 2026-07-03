package com.ugocalc.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/**
 * Barra in alto nel colore primario *pieno* (§ richiesta utente: "il menù, le impostazioni, i
 * pulsanti... non hanno nessun colore" / "non è chiaro, non assume uno dei colori primario-
 * secondario-terziario"): non un tono "container" derivato (troppo tenue/ambiguo da riconoscere),
 * ma esattamente il colore primario scelto in Impostazioni, così è inequivocabile quale colore
 * riflette. Usata da `TopAppBar` e `CenterAlignedTopAppBar` in tutte le schermate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ugoTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    scrolledContainerColor = MaterialTheme.colorScheme.primary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)
