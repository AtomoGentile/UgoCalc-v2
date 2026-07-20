package com.ugocalc.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.Recipe
import com.ugocalc.app.data.repository.RecipeRepository
import com.ugocalc.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class RecipeListViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val recipes: StateFlow<List<Recipe>> = settingsRepository.settings
        .map { it.activeSessionMode }
        .flatMapLatest { mode -> repository.getAllRecipes(mode) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
