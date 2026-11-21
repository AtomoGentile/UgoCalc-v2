package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.FavoriteShortcutDao
import com.ugocalc.app.data.local.db.entity.FavoriteShortcutEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Scorciatoie preferite in Homepage, gestite da Impostazioni > Preferiti (§2.1, §2.2). */
@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoriteShortcutDao,
) {
    fun observeFavorites(): Flow<List<FavoriteShortcutEntity>> = dao.observeAll()

    suspend fun addFavorite(historyEntryId: Long?, label: String, sortOrder: Int) =
        dao.insert(FavoriteShortcutEntity(historyEntryId = historyEntryId, label = label, sortOrder = sortOrder))

    /**
     * Preferito "da database" (§ richiesta utente), non legato a una voce di cronologia. Il peso è
     * facoltativo — l'utente decide se specificarlo o lasciare solo i valori per 100g.
     */
    suspend fun addComboFavorite(
        label: String,
        grams: Double?,
        choPer100g: Double,
        grassiPer100g: Double,
        sortOrder: Int,
        proteinePer100g: Double? = null,
        indiceGlicemico: Int? = null,
    ) =
        dao.insert(
            FavoriteShortcutEntity(
                historyEntryId = null,
                label = label,
                sortOrder = sortOrder,
                comboGrams = grams,
                comboChoPer100g = choPer100g,
                comboGrassiPer100g = grassiPer100g,
                comboProteinePer100g = proteinePer100g,
                comboIndiceGlicemico = indiceGlicemico,
            )
        )

    suspend fun removeFavorite(favorite: FavoriteShortcutEntity) = dao.delete(favorite)

    suspend fun reorder(favorites: List<FavoriteShortcutEntity>) {
        favorites.forEachIndexed { index, favorite -> dao.update(favorite.copy(sortOrder = index)) }
    }
}
