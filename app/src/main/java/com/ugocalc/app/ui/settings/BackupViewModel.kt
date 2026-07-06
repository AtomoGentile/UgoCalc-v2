package com.ugocalc.app.ui.settings

import androidx.lifecycle.ViewModel
import com.ugocalc.app.data.backup.ImportValidation
import com.ugocalc.app.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Impostazioni > Backup e ripristino (piano V2 §17): puro passa-mano verso [BackupRepository].
 * Nessuno stato proprio — lettura/scrittura del file scelto dall'utente (Storage Access Framework)
 * resta alla Screen, che ha già il `Context`/`ContentResolver`, sullo stesso pattern di
 * [com.ugocalc.app.ui.settings.tables.OfficialTablesListScreen]. Esiste solo per portare
 * [BackupRepository] (troppe dipendenze per iniettarlo altrimenti in un Composable) dentro il
 * grafo Hilt.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
) : ViewModel() {
    suspend fun exportAll(): String = backupRepository.exportAll()
    fun previewImport(content: String): ImportValidation = backupRepository.previewImport(content)
    suspend fun applyImport(content: String) = backupRepository.applyImport(content)
}
