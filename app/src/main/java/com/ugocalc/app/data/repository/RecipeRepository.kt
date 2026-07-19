package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.RecipeDao
import com.ugocalc.app.data.local.db.entity.RecipeEntity
import com.ugocalc.app.data.local.db.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
) {
    val allRecipes: Flow<List<Recipe>> = recipeDao.getAll().map { list ->
        list.map { toDomain(it) }
    }

    suspend fun getRecipeById(id: Long): Recipe? {
        val entity = recipeDao.getById(id) ?: return null
        val ingredients = recipeDao.getIngredientsForRecipe(entity.id).map { toDomain(it) }
        return toDomain(entity, ingredients)
    }

    /**
     * Fuzzy lookup: try exact match first, then a case-insensitive substring search via DAO.
     */
    suspend fun findByNameFuzzy(name: String): Recipe? {
        val exact = recipeDao.findByName(name)
        if (exact != null) {
            val ingredients = recipeDao.getIngredientsForRecipe(exact.id).map { toDomain(it) }
            return toDomain(exact, ingredients)
        }
        val fuzzy = recipeDao.findByNameFuzzy(name) ?: return null
        val ingredients = recipeDao.getIngredientsForRecipe(fuzzy.id).map { toDomain(it) }
        return toDomain(fuzzy, ingredients)
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

data class Recipe(
    val id: Long,
    val name: String,
    val description: String,
    val ingredients: List<RecipeIngredient>
)

data class RecipeIngredient(
    val id: Long,
    val name: String,
    val percentage: Double
)
