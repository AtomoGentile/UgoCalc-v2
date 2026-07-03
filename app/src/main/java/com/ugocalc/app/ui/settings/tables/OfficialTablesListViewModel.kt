package com.ugocalc.app.ui.settings.tables

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.OfficialTableEntity
import com.ugocalc.app.data.parsing.DocumentExtractionException
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.OfficialFileType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TablesListUiState(
    val tables: List<OfficialTableEntity> = emptyList(),
    val errorMessage: String? = null,
    val normalizationProgress: Map<Long, String> = emptyMap(),
    val availableModels: List<AiModel> = emptyList(),
    val selectedModel: AiModel? = null,
)

/**
 * Sezione "Tabelle valori ufficiali" delle Impostazioni (§2.2). Cliccando l'icona "+" si apre lo
 * Storage Access Framework per scegliere un file; cliccando una voce della lista si apre
 * [TableEditorScreen] per consultare/modificare quella specifica tabella (richiesto dall'utente:
 * piccola pagina per scegliere quale documento aprire quando ce n'è più di uno).
 */
@HiltViewModel
class OfficialTablesListViewModel @Inject constructor(
    private val tableRepository: TableRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)

    /** Provider con una API key configurata (§2.2): scelta manuale in questa schermata, non più
     * automatica sul primo disponibile — con più provider configurati (es. GitHub Models + Gemini)
     * l'utente potrebbe voler evitare quello con la quota giornaliera più bassa. */
    private val availableModels = AiModelCatalog.ALL.filter { settingsRepository.getApiKey(it.provider) != null }
    private val _selectedModel = MutableStateFlow(availableModels.firstOrNull())

    val uiState: StateFlow<TablesListUiState> = combine(
        tableRepository.observeTables(),
        _errorMessage,
        tableRepository.normalizationProgress,
        _selectedModel,
    ) { tables, error, progress, selectedModel ->
        TablesListUiState(tables, error, progress, availableModels, selectedModel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TablesListUiState())

    fun selectModel(model: AiModel) {
        _selectedModel.value = model
    }

    fun importFile(uri: Uri, displayName: String, fileType: OfficialFileType) {
        val model = _selectedModel.value
        if (model == null) {
            _errorMessage.value = "Configura almeno una API key in Impostazioni > API prima di caricare una tabella"
            return
        }
        viewModelScope.launch {
            tableRepository.importAndNormalize(uri, displayName, fileType, model)
        }
    }

    /** Import "già pronto" (§ richiesta utente): niente AI, il file deve avere già le colonne giuste. */
    fun importPreFormatted(uri: Uri, displayName: String) {
        viewModelScope.launch {
            try {
                tableRepository.importPreFormatted(uri, displayName)
            } catch (e: DocumentExtractionException) {
                _errorMessage.value = e.message
            }
        }
    }

    fun retryNormalization(tableId: Long) {
        val model = _selectedModel.value ?: return
        viewModelScope.launch { tableRepository.retryNormalization(tableId, model) }
    }

    fun deleteTable(table: OfficialTableEntity) {
        viewModelScope.launch { tableRepository.deleteTable(table) }
    }

    /** Riordino priorità (§2.2 "Priorità tra tabelle multiple"): frecce su/giù nella lista. */
    fun moveUp(table: OfficialTableEntity) = reorderRelative(table, -1)
    fun moveDown(table: OfficialTableEntity) = reorderRelative(table, +1)

    private fun reorderRelative(table: OfficialTableEntity, delta: Int) {
        viewModelScope.launch {
            val ordered = tableRepository.getAllTablesByPriority().map { it.id }.toMutableList()
            val index = ordered.indexOf(table.id)
            val newIndex = (index + delta).coerceIn(0, ordered.lastIndex)
            if (newIndex == index) return@launch
            ordered.removeAt(index)
            ordered.add(newIndex, table.id)
            tableRepository.reorderTables(ordered)
        }
    }
}
