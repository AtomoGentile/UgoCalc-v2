package com.ugocalc.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.Recipe
import com.ugocalc.app.data.repository.RecipeIngredient
import com.ugocalc.app.data.repository.Recipe
import com.ugocalc.app.data.repository.RecipeIngredientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipeEditViewModel @Inject constructor(
    private val repository: RecipeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecipeEditState())
    val uiState: StateFlow<RecipeEditState> = _uiState

    data class RecipeEditState(
        val id: Long = 0L,
        val name: String = "",
        val description: String = "",
        val ingredients: List<com.ugocalc.app.data.repository.RecipeIngredient> = emptyList(),
        val isSaving: Boolean = false,
        val error: String? = null,
    )

    fun loadRecipe(recipeId: Long) {
        viewModelScope.launch {
            if (recipeId != 0L) {
    
                val recipe = repository.observeAll().first().find { it.id == recipeId }
                recipe?.let {
                    _uiState.value = _uiState.value.copy(id = it.id, name = it.name, description = it.description)
                }
            }
        }
    }

    fun setName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun setDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    // Ingredient handling
    fun setIngredients(list: List<RecipeIngredient>) {
        _uiState.value = _uiState.value.copy(ingredients = list)
    }

    fun addIngredient() {
        val current = _uiState.value.ingredients.toMutableList()
        current.add(RecipeIngredient(name = "", percentage = 0.0))
        _uiState.value = _uiState.value.copy(ingredients = current)
    }

    fun updateIngredientName(index: Int, newName: String) {
        val current = _uiState.value.ingredients.toMutableList()
        if (index in current.indices) {
            val ing = current[index]
            current[index] = ing.copy(name = newName)
            _uiState.value = _uiState.value.copy(ingredients = current)
        }
    }

    fun updateIngredientPercentage(index: Int, newPercentage: Double?) {
        val current = _uiState.value.ingredients.toMutableList()
        if (index in current.indices && newPercentage != null) {
            val ing = current[index]
            current[index] = ing.copy(percentage = newPercentage)
            _uiState.value = _uiState.value.copy(ingredients = current)
        }
    }

    fun removeIngredient(index: Int) {
        val current = _uiState.value.ingredients.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.value = _uiState.value.copy(ingredients = current)
        }
    }

    fun save(onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val state = _uiState.value
            val recipe = Recipe(name = state.name, description = state.description, ingredients = state.ingredients)
            val id = repository.addRecipe(recipe)
            _uiState.value = _uiState.value.copy(isSaving = false, id = id)
            onSaved(id)
        }
    }
}
