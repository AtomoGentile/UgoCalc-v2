package com.ugocalc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ugocalc.app.data.local.db.entity.CostTransactionEntity
import kotlinx.coroutines.flow.Flow

/** Accesso al registro delle transazioni di costo per API e modelli usati (M2-M7). */
@Dao
interface CostTransactionDao {
    @Insert
    suspend fun insert(transaction: CostTransactionEntity): Long

    @Query("SELECT * FROM cost_transactions ORDER BY transactionAtEpochMillis DESC")
    fun getAllFlow(): Flow<List<CostTransactionEntity>>

    @Query("SELECT * FROM cost_transactions ORDER BY transactionAtEpochMillis DESC")
    suspend fun getAll(): List<CostTransactionEntity>

    @Query("SELECT SUM(costUsd) FROM cost_transactions")
    suspend fun getTotalCostUsd(): Double?

    @Query("SELECT SUM(costUsd) FROM cost_transactions WHERE stage = :stage")
    suspend fun getTotalCostByStage(stage: String): Double?

    @Query("SELECT SUM(costUsd) FROM cost_transactions WHERE modelId = :modelId")
    suspend fun getTotalCostByModel(modelId: String): Double?

    @Query("SELECT SUM(costUsd) FROM cost_transactions WHERE transactionAtEpochMillis >= :startMillis AND transactionAtEpochMillis < :endMillis")
    suspend fun getTotalCostInRange(startMillis: Long, endMillis: Long): Double?

    @Query("SELECT * FROM cost_transactions WHERE transactionAtEpochMillis >= :startMillis ORDER BY transactionAtEpochMillis DESC LIMIT :limit")
    suspend fun getRecentTransactions(startMillis: Long, limit: Int = 50): List<CostTransactionEntity>

    @Query("DELETE FROM cost_transactions")
    suspend fun deleteAll()
}
