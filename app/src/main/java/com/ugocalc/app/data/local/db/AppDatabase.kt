package com.ugocalc.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ugocalc.app.data.local.db.dao.AnalysisHistoryDao
import com.ugocalc.app.data.local.db.dao.FavoriteShortcutDao
import com.ugocalc.app.data.local.db.dao.NormalizedFoodEntryDao
import com.ugocalc.app.data.local.db.dao.OfficialTableDao
import com.ugocalc.app.data.local.db.dao.SearchFrequencyDao
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.local.db.entity.FavoriteShortcutEntity
import com.ugocalc.app.data.local.db.entity.NormalizedFoodEntryEntity
import com.ugocalc.app.data.local.db.entity.OfficialTableEntity
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
    ],
    version = 5,
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

    companion object {
        const val DATABASE_NAME = "ugocalc.db"
    }
}
