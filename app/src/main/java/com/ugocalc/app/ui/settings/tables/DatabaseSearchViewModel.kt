package com.ugocalc.app.ui.settings.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DatabaseSearchViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    /** Stellina sul risultato di ricerca (§ richiesta utente): preferito da database, peso facoltativo. */
    fun addComboFavorite(foodName: String, grams: Double?, choPer100g: Double, grassiPer100g: Double) {
        viewModelScope.launch {
            val nextOrder = favoritesRepository.observeFavorites().first().size
            val label = if (grams != null) "$foodName (${grams.toInt()}g)" else foodName
            favoritesRepository.addComboFavorite(label, grams, choPer100g, grassiPer100g, nextOrder)
        }
    }
}
