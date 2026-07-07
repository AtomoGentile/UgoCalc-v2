package com.ugocalc.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ugocalc.domain.inference.benchmark.BenchmarkMealEntity

@Dao
interface BenchmarkMealDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meal: BenchmarkMealEntity)

    @Query("SELECT * FROM benchmark_meals WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): List<BenchmarkMealEntity>

    @Query("SELECT * FROM benchmark_meals")
    suspend fun getAll(): List<BenchmarkMealEntity>
}
