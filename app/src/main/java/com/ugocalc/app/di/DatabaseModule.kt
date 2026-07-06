package com.ugocalc.app.di

import android.content.Context
import androidx.room.Room
import com.ugocalc.app.data.local.db.AppDatabase
import com.ugocalc.app.data.local.db.MIGRATION_1_2
import com.ugocalc.app.data.local.db.MIGRATION_2_3
import com.ugocalc.app.data.local.db.MIGRATION_3_4
import com.ugocalc.app.data.local.db.MIGRATION_4_5
import com.ugocalc.app.data.local.db.MIGRATION_5_6
import com.ugocalc.app.data.local.db.dao.AnalysisHistoryDao
import com.ugocalc.app.data.local.db.dao.BiasCorrectionDao
import com.ugocalc.app.data.local.db.dao.FavoriteShortcutDao
import com.ugocalc.app.data.local.db.dao.NormalizedFoodEntryDao
import com.ugocalc.app.data.local.db.dao.OfficialTableDao
import com.ugocalc.app.data.local.db.dao.SearchFrequencyDao
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideOfficialTableDao(db: AppDatabase): OfficialTableDao = db.officialTableDao()

    @Provides
    fun provideNormalizedFoodEntryDao(db: AppDatabase): NormalizedFoodEntryDao = db.normalizedFoodEntryDao()

    @Provides
    fun provideAnalysisHistoryDao(db: AppDatabase): AnalysisHistoryDao = db.analysisHistoryDao()

    @Provides
    fun provideFavoriteShortcutDao(db: AppDatabase): FavoriteShortcutDao = db.favoriteShortcutDao()

    @Provides
    fun provideSearchFrequencyDao(db: AppDatabase): SearchFrequencyDao = db.searchFrequencyDao()

    @Provides
    fun provideV2AnalysisRecordDao(db: AppDatabase): V2AnalysisRecordDao = db.v2AnalysisRecordDao()

    @Provides
    fun provideBiasCorrectionDao(db: AppDatabase): BiasCorrectionDao = db.biasCorrectionDao()
}
