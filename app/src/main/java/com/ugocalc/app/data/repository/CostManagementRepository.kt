package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.CostTransactionDao
import com.ugocalc.app.data.local.db.entity.CostTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class CostSummary(
    val totalCostUsd: Double,
    val costByStage: Map<String, Double>,
    val costByModel: Map<String, Double>,
    val transactionCount: Int,
    val recentTransactions: List<CostTransactionEntity>,
)

/** Gestione e tracciamento dei costi per API e modelli (M2-M7). */
@Singleton
class CostManagementRepository @Inject constructor(
    private val dao: CostTransactionDao,
) {
    fun getAllTransactionsFlow(): Flow<List<CostTransactionEntity>> = dao.getAllFlow()

    suspend fun recordCost(
        stage: String,
        modelId: String,
        costUsd: Double,
        description: String,
        sessionId: String? = null,
    ): Long {
        val transaction = CostTransactionEntity(
            transactionAtEpochMillis = System.currentTimeMillis(),
            stage = stage,
            modelId = modelId,
            costUsd = costUsd,
            description = description,
            sessionId = sessionId,
        )
        return dao.insert(transaction)
    }

    suspend fun getTotalCost(): Double = dao.getTotalCostUsd() ?: 0.0

    suspend fun getTotalCostByStage(stage: String): Double = dao.getTotalCostByStage(stage) ?: 0.0

    suspend fun getTotalCostByModel(modelId: String): Double = dao.getTotalCostByModel(modelId) ?: 0.0

    /** Costo totale in un intervallo di tempo (ad es., ultimi 7 giorni, mese corrente, ecc.). */
    suspend fun getTotalCostInRange(startMillis: Long, endMillis: Long): Double =
        dao.getTotalCostInRange(startMillis, endMillis) ?: 0.0

    /** Riepilogo completo dei costi per la dashboard. */
    suspend fun getCostSummary(): CostSummary {
        val all = dao.getAll()
        val totalCost = dao.getTotalCostUsd() ?: 0.0

        val costByStage = all.groupBy { it.stage }
            .mapValues { (_, trans) -> trans.sumOf { it.costUsd } }

        val costByModel = all.groupBy { it.modelId }
            .mapValues { (_, trans) -> trans.sumOf { it.costUsd } }

        val recent = dao.getRecentTransactions(
            startMillis = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000, // ultimi 30 giorni
            limit = 100
        )

        return CostSummary(
            totalCostUsd = totalCost,
            costByStage = costByStage,
            costByModel = costByModel,
            transactionCount = all.size,
            recentTransactions = recent,
        )
    }

    /** Costo del mese corrente. */
    suspend fun getCurrentMonthCost(): Double {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val startOfNextMonth = calendar.timeInMillis

        return getTotalCostInRange(startOfMonth, startOfNextMonth)
    }

    suspend fun deleteAllTransactions() = dao.deleteAll()
}
