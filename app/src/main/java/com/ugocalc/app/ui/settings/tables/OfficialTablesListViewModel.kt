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

    /** Modelli selezionati per la normalizzazione (§2.2). Se nessuno è selezionato, mostra tutti quelli con chiave configurata. */
    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    private val _selectedModel = MutableStateFlow<AiModel?>(null)

    val uiState: StateFlow<TablesListUiState> = combine(
        tableRepository.observeTables(),
        _errorMessage,
        tableRepository.normalizationProgress,
        _availableModels,
        _selectedModel,
    ) { tables, error, progress, availableModels, selectedModel ->
        TablesListUiState(tables, error, progress, availableModels, selectedModel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TablesListUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val enabledIds = settings.enabledNormalizationModelIds
                val models = if (enabledIds.isNotEmpty()) {
                    AiModelCatalog.ALL.filter { it.id in enabledIds && hasCredentialsForModel(it) }
                } else {
                    AiModelCatalog.ALL.filter { hasCredentialsForModel(it) }
                }
                _availableModels.value = models
                if (_selectedModel.value == null && models.isNotEmpty()) {
                    _selectedModel.value = models.first()
                }
            }
        }
    }

    private fun hasCredentialsForModel(model: AiModel): Boolean = when (model.provider) {
        com.ugocalc.app.domain.model.AiProviderType.BEDROCK_CLAUDE,
        com.ugocalc.app.domain.model.AiProviderType.BEDROCK_GPT -> settingsRepository.hasBedrockCredentials()
        com.ugocalc.app.domain.model.AiProviderType.BEDROCK_IAM_CLAUDE,
        com.ugocalc.app.domain.model.AiProviderType.BEDROCK_IAM_GPT -> settingsRepository.hasBedrockIamCredentials()
        else -> settingsRepository.getApiKey(model.provider) != null
    }

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
            try {
                tableRepository.importAndNormalize(uri, displayName, fileType, model)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Errore durante l'import: ${e.message}"
            }
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
