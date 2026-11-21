package com.ugocalc.app.ui.settings.tables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.NormalizedFoodEntryEntity
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.util.parseLocaleDoubleOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** Riga editabile dell'editor tipo Excel. `localKey` è stabile per la UI anche per righe nuove non ancora salvate. */
data class EditableRow(
    val localKey: Int,
    val dbId: Long,
    val nome: String,
    val choPer100g: String,
    val grassiPer100g: String,
    val proteinePer100g: String = "0",
    val indiceGlicemico: String = "",
)

data class TableEditorUiState(
    val tableDisplayName: String = "",
    val rows: List<EditableRow> = emptyList(),
    val isDirty: Boolean = false,
    val isLoading: Boolean = true,
)

/**
 * Editor tipo Excel per la tabella normalizzata (requisito utente, punti 3-4): consultazione e
 * correzione manuale (§2.2 "Visualizzazione e modifica"). Qualunque modifica (riga aggiunta,
 * eliminata o cella cambiata) marca [TableEditorUiState.isDirty] = true, che la UI usa per mostrare
 * il pulsante "Salva".
 */
@HiltViewModel
class TableEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tableRepository: TableRepository,
) : ViewModel() {

    private val tableId: Long = checkNotNull(savedStateHandle.get<String>("tableId")).toLong()
    private val keyGenerator = AtomicInteger(0)

    private val _uiState = MutableStateFlow(TableEditorUiState())
    val uiState: StateFlow<TableEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val table = tableRepository.getAllTablesByPriority().firstOrNull { it.id == tableId }
            val entries = tableRepository.getEntriesForTable(tableId)
            _uiState.value = TableEditorUiState(
                tableDisplayName = table?.displayName.orEmpty(),
                rows = entries.map { it.toEditableRow() },
                isDirty = false,
                isLoading = false,
            )
        }
    }

    private fun NormalizedFoodEntryEntity.toEditableRow() = EditableRow(
        localKey = keyGenerator.incrementAndGet(),
        dbId = id,
        nome = nome,
        choPer100g = formatNumber(choPer100g),
        grassiPer100g = formatNumber(grassiPer100g),
        proteinePer100g = formatNumber(proteinePer100g),
        indiceGlicemico = indiceGlicemico?.toString().orEmpty(),
    )

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    fun updateNome(localKey: Int, value: String) = updateRow(localKey) { it.copy(nome = value) }
    fun updateCho(localKey: Int, value: String) = updateRow(localKey) { it.copy(choPer100g = value) }
    fun updateGrassi(localKey: Int, value: String) = updateRow(localKey) { it.copy(grassiPer100g = value) }
    fun updateProteine(localKey: Int, value: String) = updateRow(localKey) { it.copy(proteinePer100g = value) }
    fun updateIndiceGlicemico(localKey: Int, value: String) = updateRow(localKey) { it.copy(indiceGlicemico = value) }

    private fun updateRow(localKey: Int, transform: (EditableRow) -> EditableRow) {
        _uiState.value = _uiState.value.copy(
            rows = _uiState.value.rows.map { if (it.localKey == localKey) transform(it) else it },
            isDirty = true,
        )
    }

    fun addRow() {
        val newRow = EditableRow(
            localKey = keyGenerator.incrementAndGet(),
            dbId = 0,
            nome = "",
            choPer100g = "0",
            grassiPer100g = "0",
            proteinePer100g = "0",
            indiceGlicemico = "",
        )
        _uiState.value = _uiState.value.copy(rows = _uiState.value.rows + newRow, isDirty = true)
    }

    fun deleteRow(localKey: Int) {
        _uiState.value = _uiState.value.copy(
            rows = _uiState.value.rows.filterNot { it.localKey == localKey },
            isDirty = true,
        )
    }

    fun save() {
        viewModelScope.launch {
            val entities = _uiState.value.rows
                .filter { it.nome.isNotBlank() }
                .map {
                    NormalizedFoodEntryEntity(
                        id = it.dbId,
                        tableId = tableId,
                        nome = it.nome.trim(),
                        choPer100g = it.choPer100g.parseLocaleDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        grassiPer100g = it.grassiPer100g.parseLocaleDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        proteinePer100g = it.proteinePer100g.parseLocaleDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        indiceGlicemico = it.indiceGlicemico.trim().toIntOrNull()?.takeIf { gi -> gi in 0..100 },
                        rowOrder = 0,
                        isManuallyEdited = true,
                    )
                }
            tableRepository.replaceEntries(tableId, entities)
            _uiState.value = _uiState.value.copy(isDirty = false)
        }
    }
}
