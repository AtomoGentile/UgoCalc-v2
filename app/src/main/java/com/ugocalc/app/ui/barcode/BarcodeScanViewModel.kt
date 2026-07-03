package com.ugocalc.app.ui.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.BarcodeLookupResult
import com.ugocalc.app.data.repository.BarcodeRepository
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.ui.AnalysisSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BarcodeScanUiState {
    /** In attesa che l'utente inquadri un codice nello scanner di sistema (§ richiesta utente). */
    data object Scanning : BarcodeScanUiState()
    data object LookingUp : BarcodeScanUiState()
    data class Found(val entry: NormalizedFoodEntry, val source: String) : BarcodeScanUiState()
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
        _uiState.value = BarcodeScanUiState.LookingUp
        viewModelScope.launch {
            _uiState.value = when (val result = barcodeRepository.lookupBarcode(barcode)) {
                is BarcodeLookupResult.Found -> BarcodeScanUiState.Found(result.entry, result.source)
                is BarcodeLookupResult.NotFound -> BarcodeScanUiState.NotFound(barcode)
                is BarcodeLookupResult.Error -> BarcodeScanUiState.Error(result.message)
            }
        }
    }

    /** "Cerca per nome" (§ richiesta utente: cibi preconfezionati con etichetta non disponibile). */
    fun searchByName(query: String) {
        if (query.isBlank()) return
        _uiState.value = BarcodeScanUiState.LookingUp
        viewModelScope.launch {
            _uiState.value = when (val result = barcodeRepository.searchByName(query)) {
                is BarcodeLookupResult.Found -> BarcodeScanUiState.Found(result.entry, result.source)
                is BarcodeLookupResult.NotFound -> BarcodeScanUiState.NotFound(query)
                is BarcodeLookupResult.Error -> BarcodeScanUiState.Error(result.message)
            }
        }
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
