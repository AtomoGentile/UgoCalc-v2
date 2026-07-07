package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.ugocalc.app.data.local.db.entity.RecipeEntity
import com.ugocalc.app.data.local.db.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes")
    fun getAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): RecipeEntity?

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun getIngredientsForRecipe(recipeId: Long): List<RecipeIngredientEntity>

    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert
    suspend fun insertIngredients(ingredients: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recipes")
    suspend fun deleteAll()
}
