package com.ugocalc.app.data.repository

import com.ugocalc.app.data.backup.ImportValidation
import com.ugocalc.app.data.backup.UgoCalcBackupArchive
import com.ugocalc.app.data.local.db.dao.AnalysisHistoryDao
import com.ugocalc.app.data.local.db.dao.BiasCorrectionDao
import com.ugocalc.app.data.local.db.dao.FavoriteShortcutDao
import com.ugocalc.app.data.local.db.dao.NormalizedFoodEntryDao
import com.ugocalc.app.data.local.db.dao.OfficialTableDao
import com.ugocalc.app.data.local.db.dao.RecipeDao
import com.ugocalc.app.data.local.db.dao.ScaleCalibrationDao
import com.ugocalc.app.data.local.db.dao.SessionModeAiModelPair
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.local.db.entity.BiasCorrectionEntity
import com.ugocalc.app.data.local.db.entity.FavoriteShortcutEntity
import com.ugocalc.app.data.local.db.entity.NormalizationStatus
import com.ugocalc.app.data.local.db.entity.NormalizedFoodEntryEntity
import com.ugocalc.app.data.local.db.entity.OfficialTableEntity
import com.ugocalc.app.data.local.db.entity.RecipeEntity
import com.ugocalc.app.data.local.db.entity.RecipeIngredientEntity
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.domain.model.OfficialFileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAnalysisHistoryDao : AnalysisHistoryDao {
    val rows = mutableListOf<AnalysisHistoryEntity>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<AnalysisHistoryEntity>> = MutableStateFlow(rows)
    override suspend fun getById(id: Long): AnalysisHistoryEntity? = rows.firstOrNull { it.id == id }
    override suspend fun getAllOnce(): List<AnalysisHistoryEntity> = rows.toList()
    override suspend fun insert(entity: AnalysisHistoryEntity): Long {
        val id = nextId++
        rows += entity.copy(id = id)
        return id
    }
    override suspend fun clearAll() { rows.clear() }
}

private class FakeOfficialTableDao : OfficialTableDao {
    val rows = mutableListOf<OfficialTableEntity>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<OfficialTableEntity>> = MutableStateFlow(rows)
    override fun observeCount(): Flow<Int> = MutableStateFlow(rows.size)
    override suspend fun getAllOrderedByPriority(): List<OfficialTableEntity> = rows.sortedBy { it.priorityOrder }
    override suspend fun maxPriorityOrder(): Int? = rows.maxOfOrNull { it.priorityOrder }
    override suspend fun getById(id: Long): OfficialTableEntity? = rows.firstOrNull { it.id == id }
    override suspend fun insert(entity: OfficialTableEntity): Long {
        val id = nextId++
        rows += entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: OfficialTableEntity) {
        rows.indexOfFirst { it.id == entity.id }.takeIf { it >= 0 }?.let { rows[it] = entity }
    }
    override suspend fun delete(entity: OfficialTableEntity) { rows.removeAll { it.id == entity.id } }
    override suspend fun updatePriority(id: Long, priority: Int) {
        rows.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { rows[it] = rows[it].copy(priorityOrder = priority) }
    }
    override suspend fun deleteAll() { rows.clear() }
}

private class FakeNormalizedFoodEntryDao : NormalizedFoodEntryDao {
    val rows = mutableListOf<NormalizedFoodEntryEntity>()
    private var nextId = 1L
    override fun observeForTable(tableId: Long): Flow<List<NormalizedFoodEntryEntity>> = MutableStateFlow(rows.filter { it.tableId == tableId })
    override suspend fun getForTable(tableId: Long): List<NormalizedFoodEntryEntity> = rows.filter { it.tableId == tableId }
    override suspend fun deleteAllForTable(tableId: Long) { rows.removeAll { it.tableId == tableId } }
    override suspend fun insertAll(entities: List<NormalizedFoodEntryEntity>) {
        entities.forEach { rows += it.copy(id = nextId++) }
    }
    override suspend fun insert(entity: NormalizedFoodEntryEntity): Long {
        val id = nextId++
        rows += entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: NormalizedFoodEntryEntity) {
        rows.indexOfFirst { it.id == entity.id }.takeIf { it >= 0 }?.let { rows[it] = entity }
    }
    override suspend fun delete(entity: NormalizedFoodEntryEntity) { rows.removeAll { it.id == entity.id } }
    override suspend fun maxRowOrder(tableId: Long): Int? = rows.filter { it.tableId == tableId }.maxOfOrNull { it.rowOrder }
    override suspend fun getById(id: Long): NormalizedFoodEntryEntity? = rows.firstOrNull { it.id == id }
    override suspend fun searchByName(query: String): List<NormalizedFoodEntryEntity> = rows.filter { it.nome.contains(query) }
}

private class BackupFakeBiasCorrectionDao : BiasCorrectionDao {
    val saved = mutableMapOf<List<String>, BiasCorrectionEntity>()
    override suspend fun get(sessionMode: String, strategy: String, aiModelId: String, foodCategory: String): BiasCorrectionEntity? =
        saved[listOf(sessionMode, strategy, aiModelId, foodCategory)]
    override suspend fun getAll(): List<BiasCorrectionEntity> = saved.values.toList()
    override fun getAllFlow(): Flow<List<BiasCorrectionEntity>> = flowOf(saved.values.toList())
    override suspend fun upsert(entity: BiasCorrectionEntity) {
        saved[listOf(entity.sessionMode, entity.strategy, entity.aiModelId, entity.foodCategory)] = entity
    }
    override suspend fun deleteAll() { saved.clear() }
}

private class BackupFakeV2AnalysisRecordDao : V2AnalysisRecordDao {
    private val rows = mutableMapOf<Long, V2AnalysisRecordEntity>()
    private var nextId = 1L
    override suspend fun insert(record: V2AnalysisRecordEntity): Long {
        val id = nextId++
        rows[id] = record.copy(id = id)
        return id
    }
    override suspend fun update(record: V2AnalysisRecordEntity) { rows[record.id] = record }
    override fun getAllFlow(): Flow<List<V2AnalysisRecordEntity>> = flowOf(rows.values.toList())
    override suspend fun getAll(): List<V2AnalysisRecordEntity> = rows.values.toList()
    override suspend fun getById(id: Long): V2AnalysisRecordEntity? = rows[id]
    override suspend fun getBySessionId(sessionId: String): V2AnalysisRecordEntity? = rows.values.firstOrNull { it.sessionId == sessionId }
    override suspend fun getWithGroundTruth(sessionMode: String, aiModelId: String): List<V2AnalysisRecordEntity> = emptyList()
    override fun getWithGroundTruthAllFlow(): Flow<List<V2AnalysisRecordEntity>> = flowOf(emptyList())
    override suspend fun getWithGroundTruthAll(): List<V2AnalysisRecordEntity> = emptyList()
    override suspend fun getAllWithoutGroundTruth(): List<V2AnalysisRecordEntity> = emptyList()
    override suspend fun getPendingBySessionId(sessionId: String): V2AnalysisRecordEntity? = null
    override suspend fun getSessionModeAiModelPairsWithGroundTruth(): List<SessionModeAiModelPair> = emptyList()
    override suspend fun deleteAll() { rows.clear() }
    override suspend fun deleteById(id: Long) { rows.remove(id) }
}

private class FakeScaleCalibrationDao : ScaleCalibrationDao {
    val rows = mutableListOf<ScaleCalibrationEntity>()
    override fun getAll(sessionMode: String): Flow<List<ScaleCalibrationEntity>> = flowOf(rows)
    override suspend fun getAllOnce(): List<ScaleCalibrationEntity> = rows.toList()
    override suspend fun getByType(type: String, sessionMode: String): List<ScaleCalibrationEntity> = emptyList()
    override suspend fun getById(id: Long): ScaleCalibrationEntity? = rows.firstOrNull { it.id == id }
    override suspend fun insert(calibration: ScaleCalibrationEntity): Long {
        rows.add(calibration)
        return calibration.id
    }
    override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
    override suspend fun deleteAll() { rows.clear() }
}

private class FakeRecipeDao : RecipeDao {
    val recipes = mutableListOf<RecipeEntity>()
    val ingredients = mutableListOf<RecipeIngredientEntity>()
    override fun getAll(sessionMode: String): Flow<List<RecipeEntity>> = flowOf(recipes)
    override suspend fun getAllOnce(): List<RecipeEntity> = recipes.toList()
    override suspend fun getById(id: Long): RecipeEntity? = recipes.firstOrNull { it.id == id }
    override suspend fun findByName(name: String, sessionMode: String): RecipeEntity? = null
    override suspend fun findByNameFuzzy(name: String, sessionMode: String): RecipeEntity? = null
    override suspend fun getIngredientsForRecipe(recipeId: Long): List<RecipeIngredientEntity> = ingredients.filter { it.recipeId == recipeId }
    override suspend fun insertRecipe(recipe: RecipeEntity): Long {
        recipes.add(recipe)
        return recipe.id
    }
    override suspend fun insertIngredients(ingredients: List<RecipeIngredientEntity>) { this.ingredients.addAll(ingredients) }
    override suspend fun deleteById(id: Long) { recipes.removeAll { it.id == id } }
    override suspend fun deleteAll() { recipes.clear() }
}

private class FakeFavoriteShortcutDao : FavoriteShortcutDao {
    val rows = mutableListOf<FavoriteShortcutEntity>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<FavoriteShortcutEntity>> = MutableStateFlow(rows.sortedBy { it.sortOrder })
    override suspend fun getAllOnce(): List<FavoriteShortcutEntity> = rows.sortedBy { it.sortOrder }
    override suspend fun insert(entity: FavoriteShortcutEntity): Long {
        val id = nextId++
        rows += entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: FavoriteShortcutEntity) {
        rows.indexOfFirst { it.id == entity.id }.takeIf { it >= 0 }?.let { rows[it] = entity }
    }
    override suspend fun delete(entity: FavoriteShortcutEntity) { rows.removeAll { it.id == entity.id } }
    override suspend fun deleteAll() { rows.clear() }
}

class BackupRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun repository(
        historyDao: FakeAnalysisHistoryDao = FakeAnalysisHistoryDao(),
        tableDao: FakeOfficialTableDao = FakeOfficialTableDao(),
        entryDao: FakeNormalizedFoodEntryDao = FakeNormalizedFoodEntryDao(),
        biasDao: BackupFakeBiasCorrectionDao = BackupFakeBiasCorrectionDao(),
        favoriteDao: FakeFavoriteShortcutDao = FakeFavoriteShortcutDao(),
        v2Dao: BackupFakeV2AnalysisRecordDao = BackupFakeV2AnalysisRecordDao(),
        scaleDao: FakeScaleCalibrationDao = FakeScaleCalibrationDao(),
        recipeDao: FakeRecipeDao = FakeRecipeDao(),
    ) = BackupRepository(historyDao, tableDao, entryDao, biasDao, favoriteDao, v2Dao, scaleDao, recipeDao, json)

    @Test
    fun `export then apply on a fresh store replicates history, tables, bias corrections and favorites`() = runBlocking {
        val historyDao = FakeAnalysisHistoryDao()
        val tableDao = FakeOfficialTableDao()
        val entryDao = FakeNormalizedFoodEntryDao()
        val biasDao = BackupFakeBiasCorrectionDao()
        val favoriteDao = FakeFavoriteShortcutDao()

        val historyId = historyDao.insert(AnalysisHistoryEntity(foodTitle = "Pasta al pomodoro", analyzedAtEpochMillis = 1000L, totalChoG = 80.0, totalGrassiG = 10.0, modelDisplayNamesJson = "[]", detailsJson = "[]"))
        val tableId = tableDao.insert(OfficialTableEntity(displayName = "CREA", fileType = OfficialFileType.PDF, storedFilePath = "/tmp/x.pdf", uploadedAtEpochMillis = 1L, priorityOrder = 0, rawExtractedText = "testo", normalizationStatus = NormalizationStatus.DONE))
        entryDao.insertAll(listOf(NormalizedFoodEntryEntity(tableId = tableId, nome = "riso", choPer100g = 28.0, grassiPer100g = 0.3, rowOrder = 0)))
        biasDao.upsert(BiasCorrectionEntity("CASA", "DISTESA_SUL_PIATTO", "claude-opus-4.8", "PASTA", 1.2, 5, 1000L))
        favoriteDao.insert(FavoriteShortcutEntity(historyEntryId = historyId, label = "Pasta preferita", sortOrder = 0))
        favoriteDao.insert(FavoriteShortcutEntity(historyEntryId = null, label = "Combo standard", sortOrder = 1, comboGrams = 100.0, comboChoPer100g = 20.0, comboGrassiPer100g = 1.0))

        val exportRepo = repository(historyDao, tableDao, entryDao, biasDao, favoriteDao)
        val archiveJson = exportRepo.exportAll()

        val freshHistoryDao = FakeAnalysisHistoryDao()
        val freshTableDao = FakeOfficialTableDao()
        val freshEntryDao = FakeNormalizedFoodEntryDao()
        val freshBiasDao = BackupFakeBiasCorrectionDao()
        val freshFavoriteDao = FakeFavoriteShortcutDao()
        val importRepo = repository(freshHistoryDao, freshTableDao, freshEntryDao, freshBiasDao, freshFavoriteDao)

        val validation = importRepo.previewImport(archiveJson)
        assertTrue(validation is ImportValidation.Ok)
        val summary = (validation as ImportValidation.Ok).summary
        assertEquals(1, summary.historyCount)
        assertEquals(1, summary.tablesCount)
        assertEquals(1, summary.biasCorrectionsCount)
        assertEquals(2, summary.favoritesCount)

        importRepo.applyImport(archiveJson)

        assertEquals("Pasta al pomodoro", freshHistoryDao.rows.single().foodTitle)
        val newTable = freshTableDao.rows.single()
        assertEquals("", newTable.storedFilePath) // documento originale mai esportato
        assertEquals("riso", freshEntryDao.rows.single().nome)
        assertEquals(newTable.id, freshEntryDao.rows.single().tableId) // rimappato alla nuova tabella
        assertEquals(1.2, freshBiasDao.saved.values.single().factor, 1e-9)
        val linkedFavorite = freshFavoriteDao.rows.single { it.label == "Pasta preferita" }
        assertEquals(freshHistoryDao.rows.single().id, linkedFavorite.historyEntryId) // rimappato alla nuova riga di cronologia
        val comboFavorite = freshFavoriteDao.rows.single { it.label == "Combo standard" }
        assertNull(comboFavorite.historyEntryId)
    }

    @Test
    fun `applyImport replaces existing data instead of merging with it`() = runBlocking {
        val historyDao = FakeAnalysisHistoryDao()
        historyDao.insert(AnalysisHistoryEntity(foodTitle = "Dato locale da sostituire", analyzedAtEpochMillis = 1L, totalChoG = 1.0, totalGrassiG = 1.0, modelDisplayNamesJson = "[]", detailsJson = "[]"))
        val repo = repository(historyDao = historyDao)
        val archiveJson = """{"schemaVersion":5,"exportedAtEpochMillis":1,"history":[{"foodTitle":"Dato importato","analyzedAtEpochMillis":2,"totalChoG":2.0,"totalGrassiG":2.0,"modelDisplayNamesJson":"[]","detailsJson":"[]"}],"tables":[],"biasCorrections":[],"favorites":[],"v2Records":[],"scaleCalibrations":[],"recipes":[]}"""

        repo.applyImport(archiveJson)

        assertEquals(1, historyDao.rows.size)
        assertEquals("Dato importato", historyDao.rows.single().foodTitle)
    }

    @Test
    fun `a schema version newer than supported is rejected without touching the database`() = runBlocking {
        val historyDao = FakeAnalysisHistoryDao()
        historyDao.insert(AnalysisHistoryEntity(foodTitle = "Esistente", analyzedAtEpochMillis = 1L, totalChoG = 1.0, totalGrassiG = 1.0, modelDisplayNamesJson = "[]", detailsJson = "[]"))
        val repo = repository(historyDao = historyDao)
        val futureVersion = UgoCalcBackupArchive.SCHEMA_VERSION + 1
        val archiveJson = """{"schemaVersion":$futureVersion,"exportedAtEpochMillis":1,"history":[],"tables":[],"biasCorrections":[],"favorites":[],"v2Records":[],"scaleCalibrations":[],"recipes":[]}"""

        val validation = repo.previewImport(archiveJson)

        assertTrue("Expected SchemaTooNew, got $validation", validation is ImportValidation.SchemaTooNew)
        assertEquals(futureVersion, (validation as ImportValidation.SchemaTooNew).found)
        assertEquals(1, historyDao.rows.size) // nessuna scrittura: previewImport non tocca mai il database
    }

    @Test
    fun `malformed content is reported as invalid, not thrown to the caller`() {
        val repo = repository()

        val validation = repo.previewImport("questo non è JSON")

        assertTrue(validation is ImportValidation.Invalid)
    }
}
