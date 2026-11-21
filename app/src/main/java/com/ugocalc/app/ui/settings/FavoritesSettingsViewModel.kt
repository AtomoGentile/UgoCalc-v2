package com.ugocalc.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.local.db.entity.FavoriteShortcutEntity
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesSettingsState(
    val favorites: List<FavoriteShortcutEntity> = emptyList(),
    val history: List<AnalysisHistoryEntity> = emptyList(),
)

/** Impostazioni > Preferiti (§2.2): selezione delle scorciatoie da mostrare in Homepage. */
@HiltViewModel
class FavoritesSettingsViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    historyRepository: HistoryRepository,
) : ViewModel() {

    val state: StateFlow<FavoritesSettingsState> = combine(
        favoritesRepository.observeFavorites(),
        historyRepository.observeHistory(),
    ) { favorites, history -> FavoritesSettingsState(favorites, history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FavoritesSettingsState())

    fun addFavorite(history: AnalysisHistoryEntity) {
        viewModelScope.launch {
            val nextOrder = state.value.favorites.size
            favoritesRepository.addFavorite(history.id, history.foodTitle, nextOrder)
        }
    }

    /** Preferito da database (§ richiesta utente), indipendente dalla cronologia. */
    fun addComboFavorite(
        foodName: String,
        grams: Double?,
        choPer100g: Double,
        grassiPer100g: Double,
        proteinePer100g: Double = 0.0,
        indiceGlicemico: Int? = null,
    ) {
        viewModelScope.launch {
            val nextOrder = state.value.favorites.size
            val label = if (grams != null) "$foodName (${grams.toInt()}g)" else foodName
            favoritesRepository.addComboFavorite(
                label = label,
                grams = grams,
                choPer100g = choPer100g,
                grassiPer100g = grassiPer100g,
                sortOrder = nextOrder,
                proteinePer100g = proteinePer100g,
                indiceGlicemico = indiceGlicemico,
            )
        }
    }

    fun removeFavorite(favorite: FavoriteShortcutEntity) {
        viewModelScope.launch { favoritesRepository.removeFavorite(favorite) }
    }
}
