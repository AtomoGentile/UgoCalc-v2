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
import com.ugocalc.app.domain.model.AiProviderType
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
    val statusMessage: String? = null,
    val normalizationProgress: Map<Long, String> = emptyMap(),
    val availableModels: List<AiModel> = emptyList(),
    val selectedModel: AiModel? = null,
)

/**
 * Sezione "Tabelle valori ufficiali" delle Impostazioni (§2.2). Cliccando l'icona "+" si apre lo
 * Storage Access Framework per scegliere un file; cliccando una voce della lista si apre
 * [TableEditorScreen] per consultare/modificare quella specifica tabella.
 */
@HiltViewModel
class OfficialTablesListViewModel @Inject constructor(
    private val tableRepository: TableRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    private val _selectedModel = MutableStateFlow<AiModel?>(null)

    val uiState: StateFlow<TablesListUiState> = combine(
        tableRepository.observeTables(),
        _errorMessage,
        _statusMessage,
        tableRepository.normalizationProgress,
        combine(_availableModels, _selectedModel) { avail, sel -> avail to sel },
    ) { tables, error, status, progress, (availableModels, selectedModel) ->
        TablesListUiState(
            tables = tables,
            errorMessage = error,
            statusMessage = status,
            normalizationProgress = progress,
            availableModels = availableModels,
            selectedModel = selectedModel,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TablesListUiState())

    init {
        viewModelScope.launch {
            combine(settingsRepository.settings, AiModelCatalog.catalogVersion) { settings, _ ->
                settings
            }.collect { settings ->
                val enabledIds = settings.enabledNormalizationModelIds
                val models = if (enabledIds.isNotEmpty()) {
                    AiModelCatalog.ALL.filter { (it.uid in enabledIds || it.id in enabledIds) && hasCredentialsForModel(it) }
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
        AiProviderType.BEDROCK_CLAUDE,
        AiProviderType.BEDROCK_GPT -> settingsRepository.hasBedrockCredentials()
        AiProviderType.BEDROCK_IAM_CLAUDE,
        AiProviderType.BEDROCK_IAM_GPT,
        AiProviderType.BEDROCK_IAM_AMAZON -> settingsRepository.hasBedrockIamCredentials()
        AiProviderType.VERTEX_AI -> settingsRepository.hasVertexAiCredentials()
        else -> settingsRepository.getApiKey(model.provider) != null
    }

    fun selectModel(model: AiModel) {
        _selectedModel.value = model
    }

    fun restoreStarterTable() {
        viewModelScope.launch {
            try {
                tableRepository.restoreStarterTable()
                _errorMessage.value = null
                _statusMessage.value = "Tabella base italiana (CREA) ripristinata."
            } catch (e: Exception) {
                _errorMessage.value = "Errore ripristino tabella base: ${e.message ?: "errore sconosciuto"}"
            }
        }
    }

    fun importFiles(uris: List<Uri>, displayName: String, fileType: OfficialFileType) {
        val model = _selectedModel.value
        if (model == null) {
            _errorMessage.value = "Configura almeno una API key in Impostazioni > API prima di caricare una tabella"
            return
        }
        viewModelScope.launch {
            try {
                tableRepository.importAndNormalize(uris, displayName, fileType, model)
                _errorMessage.value = null
                _statusMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Errore durante l'import: ${e.message}"
            }
        }
    }

    fun importFile(uri: Uri, displayName: String, fileType: OfficialFileType) =
        importFiles(listOf(uri), displayName, fileType)

    /** Import "già pronto" (§ richiesta utente): niente AI, il file deve avere già le colonne giuste. */
    fun importPreFormatted(uri: Uri, displayName: String) {
        viewModelScope.launch {
            try {
                tableRepository.importPreFormatted(uri, displayName)
                _errorMessage.value = null
                _statusMessage.value = null
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
