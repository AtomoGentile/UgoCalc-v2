package com.ugocalc.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.backup.ImportValidation
import com.ugocalc.app.data.repository.BackupRepository
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Impostazioni > Backup e ripristino (piano V2 §17).
 * Gestisce export/import dei dati, il reset totale delle impostazioni e la pulizia della cronologia.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {
    suspend fun exportAll(): String = backupRepository.exportAll()
    fun previewImport(content: String): ImportValidation = backupRepository.previewImport(content)
    suspend fun applyImport(content: String) = backupRepository.applyImport(content)

    fun resetAllSettings() {
        viewModelScope.launch {
            settingsRepository.resetAll()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }
}
