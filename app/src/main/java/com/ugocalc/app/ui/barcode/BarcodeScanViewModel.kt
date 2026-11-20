package com.ugocalc.app.ui.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.BarcodeLookupResult
import com.ugocalc.app.data.repository.BarcodeRepository
import com.ugocalc.app.data.repository.BarcodeSearchListResult
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.ui.AnalysisSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BarcodeScanUiState {
    /** In attesa che l'utente inquadri un codice nello scanner di sistema. */
    data object Scanning : BarcodeScanUiState()

    /** Fallback manuale + ricerca per nome (usato se lo scanner fallisce, non è disponibile o viene annullato). */
    data class ManualSearch(
        val infoMessage: String? = null,
        val isScannerUnavailable: Boolean = false,
    ) : BarcodeScanUiState()

    data object LookingUp : BarcodeScanUiState()
    data class Found(val entry: NormalizedFoodEntry, val source: String) : BarcodeScanUiState()
    data class FoundList(
        val entries: List<NormalizedFoodEntry>,
        val source: String,
        val query: String,
    ) : BarcodeScanUiState()
    data class NotFound(val query: String) : BarcodeScanUiState()
    data class Error(val message: String) : BarcodeScanUiState()
}

@HiltViewModel
class BarcodeScanViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val sessionStore: AnalysisSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BarcodeScanUiState>(BarcodeScanUiState.Scanning)
    val uiState: StateFlow<BarcodeScanUiState> = _uiState

    fun onBarcodeScanned(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) {
            openManualSearch("Inserisci un codice a barre valido.")
            return
        }
        _uiState.value = BarcodeScanUiState.LookingUp
        viewModelScope.launch {
            _uiState.value = when (val result = barcodeRepository.lookupBarcode(trimmed)) {
                is BarcodeLookupResult.Found -> BarcodeScanUiState.Found(result.entry, result.source)
                is BarcodeLookupResult.NotFound -> BarcodeScanUiState.NotFound(trimmed)
                is BarcodeLookupResult.Error -> BarcodeScanUiState.Error(result.message)
            }
        }
    }

    /** Cerca prodotti confezionati per nome restituendo una lista selezionabile (`FoundList`). */
    fun searchByName(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _uiState.value = BarcodeScanUiState.LookingUp
        viewModelScope.launch {
            _uiState.value = when (val result = barcodeRepository.searchByNameList(trimmed)) {
                is BarcodeSearchListResult.FoundList -> {
                    if (result.entries.isEmpty()) {
                        BarcodeScanUiState.NotFound(trimmed)
                    } else {
                        BarcodeScanUiState.FoundList(result.entries, result.source, trimmed)
                    }
                }
                is BarcodeSearchListResult.NotFound -> BarcodeScanUiState.NotFound(trimmed)
                is BarcodeSearchListResult.Error -> BarcodeScanUiState.Error(result.message)
            }
        }
    }

    fun selectProduct(entry: NormalizedFoodEntry, source: String) {
        _uiState.value = BarcodeScanUiState.Found(entry, source)
    }

    fun onScannerFailedOrUnavailable(reason: String? = null) {
        val detail = reason?.takeIf { it.isNotBlank() }
        val message = if (detail != null) {
            "Scanner fotocamera non disponibile ($detail). Inserisci il codice EAN o cerca per nome."
        } else {
            "Scanner fotocamera non disponibile su questo dispositivo. Inserisci il codice EAN o cerca per nome."
        }
        _uiState.value = BarcodeScanUiState.ManualSearch(
            infoMessage = message,
            isScannerUnavailable = true,
        )
    }

    fun onScannerCancelled() {
        _uiState.value = BarcodeScanUiState.ManualSearch(
            infoMessage = "Puoi inserire il codice manualmente o cercare il prodotto per nome.",
            isScannerUnavailable = false,
        )
    }

    fun openManualSearch(message: String? = null) {
        _uiState.value = BarcodeScanUiState.ManualSearch(infoMessage = message)
    }

    fun retryScan() {
        _uiState.value = BarcodeScanUiState.Scanning
    }

    /** L'utente conferma: l'alimento passa in sessione per essere raccolto da ManualFoodEntryScreen. */
    fun confirmAndProceed(entry: NormalizedFoodEntry) {
        sessionStore.reset()
        sessionStore.setPendingManualEntry(entry)
    }
}
