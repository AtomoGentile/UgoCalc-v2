package com.ugocalc.app.ui.recipes

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeEditUiState(
    val id: Long = 0L,
    val name: String = "",
    val description: String = "",
    val isSaving: Boolean = false
)

@HiltViewModel
class RecipeEditViewModel @Inject constructor(
    private val repository: RecipeRepository
) : ViewModel() {

    private val _uiState = mutableStateOf(RecipeEditUiState())
    val uiState: State<RecipeEditUiState> = _uiState

    fun loadRecipe(id: Long) {
        if (id == 0L) return
        viewModelScope.launch {
            // Loading logic would go here
            _uiState.value = _uiState.value.copy(id = id)
        }
    }

    fun setName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun setDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun save(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            // Save logic would go here
            onSaved(_uiState.value.id)
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }
}
