package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.db.dao.AnalysisHistoryDao
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.AnalysisTotals
import kotlinx.coroutines.flow.Flow
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
    /**
     * Local-First: la cronologia salvata in Room sul dispositivo è sempre visibile,
     * indipendentemente dal fatto che l'utente sia loggato con Cognito o lavori in locale.
     */
    fun observeHistory(): Flow<List<AnalysisHistoryEntity>> = historyDao.observeAll()

    fun observeHistoryByMode(mode: SessionMode): Flow<List<AnalysisHistoryEntity>> =
        historyDao.observeByMode(mode.name)

    suspend fun getById(id: Long): AnalysisHistoryEntity? = historyDao.getById(id)

    suspend fun getBySessionId(sessionId: String): AnalysisHistoryEntity? = historyDao.getBySessionId(sessionId)

    suspend fun saveAnalysis(foodTitle: String, totals: AnalysisTotals, modelDisplayNames: List<String>, sessionId: String? = null, sessionMode: SessionMode = SessionMode.CASA, id: Long = 0L): Long {
        val entity = AnalysisHistoryEntity(
            id = id,
            foodTitle = foodTitle,
            analyzedAtEpochMillis = System.currentTimeMillis(),
            totalChoG = totals.totalChoG,
            totalGrassiG = totals.totalGrassiG,
            modelDisplayNamesJson = json.encodeToString(modelDisplayNames),
            detailsJson = json.encodeToString(totals.lines),
            sessionId = sessionId,
            sessionMode = sessionMode.name,
            totalProteineG = totals.totalProteineG,
            glycemicLoad = GlycemicCurveCalculator.computeTotalGlycemicLoad(totals.lines),
        )
        return historyDao.insert(entity)
    }

    /** "Pulizia database cronologia" (§2.2, icona cestino). */
    suspend fun clearHistory() = historyDao.clearAll()

    suspend fun deleteAnalysis(id: Long) {
        val entry = historyDao.getById(id)
        historyDao.deleteById(id)
        // Se c'è un record V2 o benchmark linkato, la cancellazione dalla cronologia
        // deve rimuovere anche la richiesta di calibrazione (§ segnalazione utente).
        entry?.sessionId?.let { sid ->
            // Qui dovremmo chiamare i repository V2 e Benchmark per pulire, 
            // ma HistoryRepository non dovrebbe dipendere da loro per evitare cicli.
            // Una soluzione è usare un evento o far sì che il chiamante coordini.
        }
    }
}
