package com.ugocalc.app.ui.settings

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.data.repository.ScaleCalibrationRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.domain.inference.model.SessionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleCalibrationViewModel @Inject constructor(
    private val repository: ScaleCalibrationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /** Lista osservabile delle calibrazioni, aggiornata in tempo reale. */
    val calibrations: SnapshotStateList<ScaleCalibrationEntity> = mutableStateListOf()
    
    private var currentMode: SessionMode = SessionMode.CASA

    init {
        // Osserva le calibrazioni dal repository e mantiene la lista sincronizzata
        settingsRepository.settings
            .map { it.activeSessionMode }
            .onEach { currentMode = it }
            .flatMapLatest { mode -> repository.observeAll(mode) }
            .onEach { list ->
                calibrations.clear()
                calibrations.addAll(list)
            }
            .launchIn(viewModelScope)
    }

    /** Aggiunge una nuova calibrazione. */
    fun addCalibration(
        type: ScaleAnchorType,
        name: String,
        valueCm: Double,
        widthCm: Double? = null,
        heightCm: Double? = null,
        capacityMl: Double? = null,
        imagePath: String? = null,
    ) {
        viewModelScope.launch {
            repository.addCalibration(currentMode, type, name, valueCm, widthCm, heightCm, capacityMl, imagePath)
        }
    }

    /** Aggiorna una calibrazione esistente. */
    fun updateCalibration(
        id: Long,
        type: ScaleAnchorType,
        name: String,
        valueCm: Double,
        widthCm: Double? = null,
        heightCm: Double? = null,
        capacityMl: Double? = null,
        imagePath: String? = null,
    ) {
        viewModelScope.launch {
            repository.updateCalibration(id, currentMode, type, name, valueCm, widthCm, heightCm, capacityMl, imagePath)
        }
    }

    /** Rimuove la calibrazione con l'id specificato. */
    fun deleteCalibration(id: Long) {
        viewModelScope.launch {
            repository.deleteCalibration(id)
        }
    }
}
