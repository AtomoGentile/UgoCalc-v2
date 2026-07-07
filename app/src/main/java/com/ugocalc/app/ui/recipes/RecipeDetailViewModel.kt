package com.ugocalc.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.RecipeRepository
import com.ugocalc.app.data.repository.Recipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val repository: RecipeRepository,
) : ViewModel() {
    /** Returns a StateFlow that emits the recipe matching the given id, or null if not found. */
    fun recipeById(recipeId: Long): StateFlow<Recipe?> = repository.observeAll()
        .map { list -> list.find { it.id == recipeId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
