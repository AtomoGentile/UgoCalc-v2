package com.ugocalc.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.UpdateCheckResult
import com.ugocalc.app.data.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.compose.runtime.Immutable
data class ChangelogUiState(
    val checkingForUpdate: Boolean = false,
    val updateResult: UpdateCheckResult? = null,
)

/** Impostazioni > Software e aggiornamenti: controllo aggiornamenti su richiesta (§ richiesta utente). */
@HiltViewModel
class ChangelogViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChangelogUiState())
    val uiState: StateFlow<ChangelogUiState> = _uiState

    fun checkForUpdate() {
        _uiState.value = _uiState.value.copy(checkingForUpdate = true, updateResult = null)
        viewModelScope.launch {
            val result = updateRepository.checkForUpdate()
            _uiState.value = _uiState.value.copy(checkingForUpdate = false, updateResult = result)
        }
    }
}
