package com.ugocalc.app.ui.barcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.MacroMetricTile
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors

/**
 * Scanner codici a barre (§ richiesta utente): prova ad aprire lo scanner Google Play Services e,
 * in caso di annullamento o indisponibilità, ripiega automaticamente sulla UI "Barcode Manuale +
 * Ricerca Prodotto" senza uscire mai silenziosamente dalla schermata.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BarcodeScanScreen(
    onBack: () -> Unit,
    onEntryReady: () -> Unit,
    viewModel: BarcodeScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var manualBarcode by rememberSaveable { mutableStateOf("") }
    var productQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is BarcodeScanUiState.Scanning) {
            try {
                GmsBarcodeScanning.getClient(context).startScan()
                    .addOnSuccessListener { barcode ->
                        val value = barcode.rawValue
                        if (!value.isNullOrBlank()) {
                            viewModel.onBarcodeScanned(value)
                        } else {
                            viewModel.onScannerCancelled()
                        }
                    }
                    .addOnCanceledListener { viewModel.onScannerCancelled() }
                    .addOnFailureListener { e -> viewModel.onScannerFailedOrUnavailable(e.localizedMessage) }
            } catch (e: Exception) {
                viewModel.onScannerFailedOrUnavailable(e.localizedMessage)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanner & Prodotti") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is BarcodeScanUiState.Scanning -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Apertura dello scanner...",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    TextButton(
                        onClick = { viewModel.openManualSearch() },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Inserisci codice o cerca per nome")
                    }
                }
            }

            is BarcodeScanUiState.LookingUp -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Ricerca del prodotto in corso...",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            is BarcodeScanUiState.ManualSearch -> {
                ManualSearchContent(
                    infoMessage = state.infoMessage,
                    isScannerUnavailable = state.isScannerUnavailable,
                    manualBarcode = manualBarcode,
                    onBarcodeChange = { manualBarcode = it },
                    productQuery = productQuery,
                    onQueryChange = { productQuery = it },
                    onSearchBarcode = { viewModel.onBarcodeScanned(manualBarcode) },
                    onSearchByName = { viewModel.searchByName(productQuery) },
                    onLaunchScanner = viewModel::retryScan,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }

            is BarcodeScanUiState.Found -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatusBanner(
                        icon = Icons.Filled.CheckCircle,
                        title = "Prodotto trovato",
                        subtitle = "Fonte: ${state.source}",
                        tone = BannerTone.SUCCESS,
                    )

                    UgoCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Text(
                            text = state.entry.nome,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Valori nutrizionali per 100g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MacroMetricTile(
                                label = "CHO",
                                value = "%.1f".format(state.entry.choPer100g),
                                unit = "g",
                                accentColor = UgoSemanticColors.cho,
                                modifier = Modifier.weight(1f),
                            )
                            MacroMetricTile(
                                label = "Proteine",
                                value = "%.1f".format(state.entry.proteinePer100g),
                                unit = "g",
                                accentColor = UgoSemanticColors.protein,
                                modifier = Modifier.weight(1f),
                            )
                            MacroMetricTile(
                                label = "Grassi",
                                value = "%.1f".format(state.entry.grassiPer100g),
                                unit = "g",
                                accentColor = UgoSemanticColors.fat,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        state.entry.indiceGlicemico?.let { gi ->
                            Spacer(Modifier.height(10.dp))
                            MacroPill(
                                label = "IG stimato",
                                value = "$gi",
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.confirmAndProceed(state.entry)
                            onEntryReady()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(
                            text = "Scegli quantità →",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.openManualSearch() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text("Cerca un altro prodotto")
                    }

                    TextButton(
                        onClick = viewModel::retryScan,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Scansiona un altro codice")
                    }
                }
            }

            is BarcodeScanUiState.FoundList -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__header__") {
                            UgoSectionHeader(
                                title = "Risultati per \"${state.query}\"",
                                subtitle = "${state.entries.size} prodotti trovati (${state.source}) · Tocca per scegliere",
                            )
                        }

                        items(state.entries) { entry ->
                            UgoCard(
                                onClick = { viewModel.selectProduct(entry, state.source) },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(14.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.nome,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            MacroPill(
                                                label = "CHO/100g",
                                                value = "${"%.1f".format(entry.choPer100g)} g",
                                                containerColor = UgoSemanticColors.choContainer,
                                                contentColor = UgoSemanticColors.onChoContainer,
                                            )
                                            if (entry.proteinePer100g > 0.0) {
                                                MacroPill(
                                                    label = "Prot/100g",
                                                    value = "${"%.1f".format(entry.proteinePer100g)} g",
                                                    containerColor = UgoSemanticColors.proteinContainer,
                                                    contentColor = UgoSemanticColors.onProteinContainer,
                                                )
                                            }
                                            MacroPill(
                                                label = "Grassi/100g",
                                                value = "${"%.1f".format(entry.grassiPer100g)} g",
                                                containerColor = UgoSemanticColors.fatContainer,
                                                contentColor = UgoSemanticColors.onFatContainer,
                                            )
                                            entry.indiceGlicemico?.let { gi ->
                                                MacroPill(
                                                    label = "IG",
                                                    value = "$gi",
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.ArrowForward,
                                        contentDescription = "Seleziona ${entry.nome}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.openManualSearch() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .height(48.dp),
                        ) {
                            Text("Nuova ricerca")
                        }
                    }
                }
            }

            is BarcodeScanUiState.NotFound -> {
                LaunchedEffect(state.query) {
                    if (productQuery.isBlank() && state.query.any(Char::isLetter)) {
                        productQuery = state.query
                    }
                }
                ManualSearchContent(
                    infoMessage = "Nessun prodotto trovato per \"${state.query}\". Prova a cercare per nome o inserisci un altro codice.",
                    isScannerUnavailable = false,
                    isNotFound = true,
                    manualBarcode = manualBarcode,
                    onBarcodeChange = { manualBarcode = it },
                    productQuery = productQuery,
                    onQueryChange = { productQuery = it },
                    onSearchBarcode = { viewModel.onBarcodeScanned(manualBarcode) },
                    onSearchByName = { viewModel.searchByName(productQuery) },
                    onLaunchScanner = viewModel::retryScan,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }

            is BarcodeScanUiState.Error -> {
                ManualSearchContent(
                    infoMessage = state.message,
                    isScannerUnavailable = false,
                    isError = true,
                    manualBarcode = manualBarcode,
                    onBarcodeChange = { manualBarcode = it },
                    productQuery = productQuery,
                    onQueryChange = { productQuery = it },
                    onSearchBarcode = { viewModel.onBarcodeScanned(manualBarcode) },
                    onSearchByName = { viewModel.searchByName(productQuery) },
                    onLaunchScanner = viewModel::retryScan,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }
    }
}

@Composable
private fun ManualSearchContent(
    infoMessage: String?,
    isScannerUnavailable: Boolean,
    isNotFound: Boolean = false,
    isError: Boolean = false,
    manualBarcode: String,
    onBarcodeChange: (String) -> Unit,
    productQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchBarcode: () -> Unit,
    onSearchByName: () -> Unit,
    onLaunchScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!infoMessage.isNullOrBlank()) {
            val tone = when {
                isError -> BannerTone.ERROR
                isScannerUnavailable || isNotFound -> BannerTone.WARNING
                else -> BannerTone.INFO
            }
            val title = when {
                isError -> "Errore di ricerca"
                isNotFound -> "Prodotto non trovato"
                isScannerUnavailable -> "Scanner non disponibile"
                else -> "Ricerca manuale"
            }
            val icon = when {
                isError -> Icons.Filled.ErrorOutline
                isNotFound -> Icons.Filled.SearchOff
                else -> Icons.Filled.QrCodeScanner
            }
            StatusBanner(
                icon = icon,
                title = title,
                subtitle = infoMessage,
                tone = tone,
            )
        }

        // Card 1: Barcode manuale + rilancio scanner
        UgoCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
        ) {
            Text(
                text = "Codice a barre (EAN)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Inserisci le cifre sotto il codice a barre oppure riapri la fotocamera.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            OutlinedTextField(
                value = manualBarcode,
                onValueChange = onBarcodeChange,
                label = { Text("Codice EAN (es. 8001234567890)") },
                leadingIcon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { if (manualBarcode.isNotBlank()) onSearchBarcode() }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onSearchBarcode,
                    enabled = manualBarcode.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cerca codice")
                }
                OutlinedButton(
                    onClick = onLaunchScanner,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scanner")
                }
            }
        }

        // Card 2: Ricerca per nome prodotto
        UgoCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
        ) {
            Text(
                text = "Ricerca per nome prodotto",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Utile per prodotti confezionati senza codice leggibile o senza etichetta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            OutlinedTextField(
                value = productQuery,
                onValueChange = onQueryChange,
                label = { Text("Nome del prodotto (es. Biscotti Gocciole)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (productQuery.isNotBlank()) onSearchByName() }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSearchByName,
                enabled = productQuery.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cerca prodotto")
            }
        }
    }
}
