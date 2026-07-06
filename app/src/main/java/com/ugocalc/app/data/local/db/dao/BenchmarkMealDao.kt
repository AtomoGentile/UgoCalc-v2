package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ugocalc.app.data.local.db.entity.BenchmarkMealEntity

/** Benchmark pesato V1 vs V2 (§10, §13 punto 5): accesso a `benchmark_meals`. */
@Dao
interface BenchmarkMealDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meal: BenchmarkMealEntity)

    @Update
    suspend fun update(meal: BenchmarkMealEntity)

    @Query("SELECT * FROM benchmark_meals WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): BenchmarkMealEntity?

    @Query("SELECT * FROM benchmark_meals ORDER BY capturedAtEpochMillis DESC")
    suspend fun getAll(): List<BenchmarkMealEntity>

    /** Pasti ancora senza peso vero: da mostrare per la registrazione manuale (§13 punto 5). */
    @Query("SELECT * FROM benchmark_meals WHERE groundTruthPesiJson IS NULL ORDER BY capturedAtEpochMillis DESC")
    suspend fun getAllWithoutGroundTruth(): List<BenchmarkMealEntity>

    /** Pasti con peso vero registrato: la base della dashboard di confronto V1/V2. */
    @Query("SELECT * FROM benchmark_meals WHERE groundTruthPesiJson IS NOT NULL ORDER BY capturedAtEpochMillis DESC")
    suspend fun getAllWithGroundTruth(): List<BenchmarkMealEntity>
}
