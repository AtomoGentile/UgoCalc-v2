package com.ugocalc.app.ui.recipes

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.Recipe
import com.ugocalc.app.data.repository.RecipeIngredient
import com.ugocalc.app.data.repository.RecipeRepository
import com.ugocalc.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeEditUiState(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val isSaving: Boolean = false
)

@HiltViewModel
class RecipeEditViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = mutableStateOf(RecipeEditUiState())
    val uiState: State<RecipeEditUiState> = _uiState

    fun loadRecipe(id: Long) {
        if (id == 0L) {
            _uiState.value = RecipeEditUiState()
            return
        }
        viewModelScope.launch {
            repository.getRecipeById(id)?.let { recipe ->
                _uiState.value = RecipeEditUiState(
                    id = recipe.id,
                    name = recipe.name,
                    description = recipe.description,
                    ingredients = recipe.ingredients
                )
            }
        }
    }

    fun setName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun setDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun addIngredient(name: String, percentage: Double) {
        val newIngredient = RecipeIngredient(id = 0, name = name, percentage = percentage)
        _uiState.value = _uiState.value.copy(
            ingredients = _uiState.value.ingredients + newIngredient
        )
    }

    fun removeIngredient(index: Int) {
        val currentList = _uiState.value.ingredients.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _uiState.value = _uiState.value.copy(ingredients = currentList)
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = _uiState.value
        if (current.name.isBlank()) return

        _uiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val mode = settingsRepository.settings.first().activeSessionMode
            val recipe = Recipe(
                id = current.id,
                name = current.name,
                description = current.description,
                ingredients = current.ingredients
            )
            repository.addRecipe(recipe, mode)
            _uiState.value = _uiState.value.copy(isSaving = false)
            onSaved()
        }
    }
}
