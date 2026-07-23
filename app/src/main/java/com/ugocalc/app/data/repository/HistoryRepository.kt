package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.AnalysisHistoryDao
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.domain.model.AnalysisTotals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: AnalysisHistoryDao,
    private val json: Json,
    private val authRepository: AuthRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHistory(): Flow<List<AnalysisHistoryEntity>> =
        authRepository.currentUserFlow.flatMapLatest { user ->
            if (user != null) historyDao.observeAll() else flowOf(emptyList())
        }

    suspend fun getById(id: Long): AnalysisHistoryEntity? = historyDao.getById(id)

    /** Pulsante "Salva" (§2.4 punto 7): salva l'analisi corrente in Cronologia. */
    suspend fun saveAnalysis(foodTitle: String, totals: AnalysisTotals, modelDisplayNames: List<String>, sessionId: String? = null): Long {
        val entity = AnalysisHistoryEntity(
            foodTitle = foodTitle,
            analyzedAtEpochMillis = System.currentTimeMillis(),
            totalChoG = totals.totalChoG,
            totalGrassiG = totals.totalGrassiG,
            modelDisplayNamesJson = json.encodeToString(modelDisplayNames),
            detailsJson = json.encodeToString(totals.lines),
            sessionId = sessionId,
        )
        return historyDao.insert(entity)
    }

    /** "Pulizia database cronologia" (§2.2, icona cestino). */
    suspend fun clearHistory() = historyDao.clearAll()
}
