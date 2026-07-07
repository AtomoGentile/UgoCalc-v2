package com.ugocalc.app.ui.recipes

import androidx.lifecycle.ViewModel
import com.ugocalc.app.data.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val repository: RecipeRepository
) : ViewModel() {
    // Basic implementation for now
}
