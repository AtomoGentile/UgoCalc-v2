package com.ugocalc.app.ui.barcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey

/**
 * Scanner codici a barre (§ richiesta utente): apre subito lo scanner di sistema fornito da Google
 * Play Services (nessuna preview custom da gestire), cerca il prodotto su OpenFoodFacts e, se
 * assente, prova con la ricerca AI. Se il codice non porta a nulla, l'utente può cercare per nome
 * (utile per prodotti confezionati senza etichetta leggibile).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(
    onBack: () -> Unit,
    onEntryReady: () -> Unit,
    viewModel: BarcodeScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var manualQuery by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is BarcodeScanUiState.Scanning) {
            GmsBarcodeScanning.getClient(context).startScan()
                .addOnSuccessListener { barcode ->
                    val value = barcode.rawValue
                    if (value != null) viewModel.onBarcodeScanned(value) else onBack()
                }
                .addOnCanceledListener { onBack() }
                .addOnFailureListener { onBack() }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scanner codici a barre") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val state = uiState) {
                is BarcodeScanUiState.Scanning -> {
                    CircularProgressIndicator()
                    Text("Apertura dello scanner...", modifier = Modifier.padding(top = 16.dp))
                }
                is BarcodeScanUiState.LookingUp -> {
                    CircularProgressIndicator()
                    Text("Ricerca del prodotto...", modifier = Modifier.padding(top = 16.dp))
                }
                is BarcodeScanUiState.Found -> {
                    Text(state.entry.nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "CHO ${"%.1f".format(state.entry.choPer100g)} g · Grassi ${"%.1f".format(state.entry.grassiPer100g)} g per 100g",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text("Fonte: ${state.source}", style = MaterialTheme.typography.bodySmall, color = UgoGrey)
                    Button(
                        onClick = { viewModel.confirmAndProceed(state.entry); onEntryReady() },
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    ) { Text("Aggiungi e continua") }
                    TextButton(onClick = viewModel::retryScan) { Text("Scansiona un altro prodotto") }
                }
                is BarcodeScanUiState.NotFound -> {
                    Text("Nessun prodotto trovato per \"${state.query}\".", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Se hai il prodotto ma non riesci a leggere l'etichetta, prova a cercarlo per nome.",
                        style = MaterialTheme.typography.bodySmall,
                        color = UgoGrey,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    OutlinedTextField(
                        value = manualQuery,
                        onValueChange = { manualQuery = it },
                        label = { Text("Cerca per nome del prodotto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.searchByName(manualQuery) },
                        enabled = manualQuery.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text("Cerca") }
                    TextButton(onClick = viewModel::retryScan) { Text("Riprova la scansione") }
                }
                is BarcodeScanUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = viewModel::retryScan, modifier = Modifier.padding(top = 16.dp)) { Text("Riprova") }
                }
            }
        }
    }
}
