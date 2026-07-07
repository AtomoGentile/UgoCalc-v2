package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.RecipeDao
import com.ugocalc.app.data.local.db.entity.RecipeEntity
import com.ugocalc.app.data.local.db.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Ingrediente di una ricetta: nome da cercare nelle tabelle per il matching, quota del totale. */
data class Recipe(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
)

data class RecipeIngredient(
    val id: Long = 0,
    val name: String,
    val percentage: Double,
)

/**
 * Repository per la gestione delle ricette di composti (piano V2 §13 punto 6). Le ricette sono
 * definite dall'utente tramite UI e servono a ripartire il peso totale di un alimento composto
 * (es. lasagne) tra i singoli ingredienti per il calcolo nutrizionale.
 */
@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
) {

    fun observeAll(): Flow<List<Recipe>> = recipeDao.getAll().map { list ->
        list.map { entity ->
            val ingredients = recipeDao.getIngredientsForRecipe(entity.id).map { toDomain(it) }
            toDomain(entity, ingredients)
        }
    }

    suspend fun findByName(name: String): Recipe? {
        val entity = recipeDao.findByName(name) ?: return null
        val ingredients = recipeDao.getIngredientsForRecipe(entity.id).map { toDomain(it) }
        return toDomain(entity, ingredients)
    }

    suspend fun addRecipe(recipe: Recipe): Long {
        val recipeId = recipeDao.insertRecipe(
            RecipeEntity(id = 0L, name = recipe.name, description = recipe.description)
        )
        val ingredients = recipe.ingredients.map {
            RecipeIngredientEntity(recipeId = recipeId, name = it.name, percentage = it.percentage)
        }
        recipeDao.insertIngredients(ingredients)
        return recipeId
    }

    suspend fun deleteRecipe(id: Long) {
        recipeDao.deleteById(id)
    }

    private fun toDomain(entity: RecipeEntity, ingredients: List<RecipeIngredient> = emptyList()) = Recipe(
        id = entity.id,
        name = entity.name,
        description = entity.description,
        ingredients = ingredients,
    )

    private fun toDomain(entity: RecipeIngredientEntity) = RecipeIngredient(
        id = entity.id,
        name = entity.name,
        percentage = entity.percentage,
    )
}
