package com.ugocalc.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ugocalc.app.data.local.db.dao.AnalysisHistoryDao
import com.ugocalc.app.data.local.db.dao.FavoriteShortcutDao
import com.ugocalc.app.data.local.db.dao.NormalizedFoodEntryDao
import com.ugocalc.app.data.local.db.dao.OfficialTableDao
import com.ugocalc.app.data.local.db.dao.SearchFrequencyDao
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.local.db.entity.FavoriteShortcutEntity
import com.ugocalc.app.data.local.db.entity.NormalizedFoodEntryEntity
import com.ugocalc.app.data.local.db.entity.OfficialTableEntity
import com.ugocalc.app.data.local.db.entity.SearchFrequencyEntity

@Database(
    entities = [
        OfficialTableEntity::class,
        NormalizedFoodEntryEntity::class,
        AnalysisHistoryEntity::class,
        FavoriteShortcutEntity::class,
        SearchFrequencyEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun officialTableDao(): OfficialTableDao
    abstract fun normalizedFoodEntryDao(): NormalizedFoodEntryDao
    abstract fun analysisHistoryDao(): AnalysisHistoryDao
    abstract fun favoriteShortcutDao(): FavoriteShortcutDao
    abstract fun searchFrequencyDao(): SearchFrequencyDao

    companion object {
        const val DATABASE_NAME = "ugocalc.db"
    }
}
