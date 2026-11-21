package com.ugocalc.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ugocalc.app.data.local.db.dao.AnalysisHistoryDao
import com.ugocalc.app.data.local.db.dao.BenchmarkMealDao
import com.ugocalc.app.data.local.db.dao.BiasCorrectionDao
import com.ugocalc.app.data.local.db.dao.CloudSyncQueueDao
import com.ugocalc.app.data.local.db.dao.CostTransactionDao
import com.ugocalc.app.data.local.db.dao.FavoriteShortcutDao
import com.ugocalc.app.data.local.db.dao.NormalizedFoodEntryDao
import com.ugocalc.app.data.local.db.dao.OfficialTableDao
import com.ugocalc.app.data.local.db.dao.PhysicalDataDao
import com.ugocalc.app.data.local.db.dao.RecipeDao
import com.ugocalc.app.data.local.db.dao.ScaleCalibrationDao
import com.ugocalc.app.data.local.db.dao.SearchFrequencyDao
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.local.db.entity.BenchmarkMealEntity
import com.ugocalc.app.data.local.db.entity.BiasCorrectionEntity
import com.ugocalc.app.data.local.db.entity.CloudSyncQueueEntity
import com.ugocalc.app.data.local.db.entity.CostTransactionEntity
import com.ugocalc.app.data.local.db.entity.FavoriteShortcutEntity
import com.ugocalc.app.data.local.db.entity.NormalizedFoodEntryEntity
import com.ugocalc.app.data.local.db.entity.OfficialTableEntity
import com.ugocalc.app.data.local.db.entity.PhysicalDataEntity
import com.ugocalc.app.data.local.db.entity.RecipeEntity
import com.ugocalc.app.data.local.db.entity.RecipeIngredientEntity
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.data.local.db.entity.SearchFrequencyEntity
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity

@Database(
    entities = [
        OfficialTableEntity::class,
        NormalizedFoodEntryEntity::class,
        AnalysisHistoryEntity::class,
        FavoriteShortcutEntity::class,
        SearchFrequencyEntity::class,
        V2AnalysisRecordEntity::class,
        BiasCorrectionEntity::class,
        BenchmarkMealEntity::class,
        ScaleCalibrationEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        PhysicalDataEntity::class,
        CloudSyncQueueEntity::class,
        CostTransactionEntity::class,
    ],
    version = 26,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun officialTableDao(): OfficialTableDao
    abstract fun normalizedFoodEntryDao(): NormalizedFoodEntryDao
    abstract fun analysisHistoryDao(): AnalysisHistoryDao
    abstract fun favoriteShortcutDao(): FavoriteShortcutDao
    abstract fun searchFrequencyDao(): SearchFrequencyDao
    abstract fun v2AnalysisRecordDao(): V2AnalysisRecordDao
    abstract fun biasCorrectionDao(): BiasCorrectionDao
    abstract fun benchmarkMealDao(): BenchmarkMealDao
    abstract fun scaleCalibrationDao(): ScaleCalibrationDao
    abstract fun recipeDao(): RecipeDao
    abstract fun physicalDataDao(): PhysicalDataDao
    abstract fun cloudSyncQueueDao(): CloudSyncQueueDao
    abstract fun costTransactionDao(): CostTransactionDao

    companion object {
        const val DATABASE_NAME = "ugocalc.db"
    }
}
