package com.ugocalc.app.di

import android.content.Context
import androidx.room.Room
import com.ugocalc.app.data.local.db.AppDatabase
import com.ugocalc.app.data.local.db.MIGRATION_1_2
import com.ugocalc.app.data.local.db.MIGRATION_2_3
import com.ugocalc.app.data.local.db.MIGRATION_3_4
import com.ugocalc.app.data.local.db.MIGRATION_4_5
import com.ugocalc.app.data.local.db.MIGRATION_5_6
import com.ugocalc.app.data.local.db.MIGRATION_6_7
import com.ugocalc.app.data.local.db.MIGRATION_7_8
import com.ugocalc.app.data.local.db.MIGRATION_8_9
import com.ugocalc.app.data.local.db.MIGRATION_9_10
import com.ugocalc.app.data.local.db.MIGRATION_14_15
import com.ugocalc.app.data.local.db.MIGRATION_16_17
import com.ugocalc.app.data.local.db.MIGRATION_17_18
import com.ugocalc.app.data.local.db.MIGRATION_18_19
import com.ugocalc.app.data.local.db.MIGRATION_19_20
import com.ugocalc.app.data.local.db.MIGRATION_20_21
import com.ugocalc.app.data.local.db.MIGRATION_21_22
import com.ugocalc.app.data.local.db.MIGRATION_22_23
import com.ugocalc.app.data.local.db.MIGRATION_23_24
import com.ugocalc.app.data.local.db.MIGRATION_24_25
import com.ugocalc.app.data.local.db.MIGRATION_25_26
import com.ugocalc.app.data.local.db.dao.AnalysisHistoryDao
import com.ugocalc.app.data.local.db.dao.CloudSyncQueueDao
import com.ugocalc.app.data.local.db.dao.BenchmarkMealDao
import com.ugocalc.app.data.local.db.dao.BiasCorrectionDao
import com.ugocalc.app.data.local.db.dao.CostTransactionDao
import com.ugocalc.app.data.local.db.dao.FavoriteShortcutDao
import com.ugocalc.app.data.local.db.dao.NormalizedFoodEntryDao
import com.ugocalc.app.data.local.db.dao.OfficialTableDao
import com.ugocalc.app.data.local.db.dao.PhysicalDataDao
import com.ugocalc.app.data.local.db.dao.RecipeDao
import com.ugocalc.app.data.local.db.dao.ScaleCalibrationDao
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
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_14_15, MIGRATION_16_17, MIGRATION_17_18,
                MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
                MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26
            )
            .fallbackToDestructiveMigration()
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

    @Provides
    fun provideBenchmarkMealDao(db: AppDatabase): BenchmarkMealDao = db.benchmarkMealDao()

    @Provides
    fun provideScaleCalibrationDao(db: AppDatabase): ScaleCalibrationDao = db.scaleCalibrationDao()

    @Provides
    fun provideRecipeDao(db: AppDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun providePhysicalDataDao(db: AppDatabase): PhysicalDataDao = db.physicalDataDao()

    @Provides
    fun provideCloudSyncQueueDao(db: AppDatabase): CloudSyncQueueDao = db.cloudSyncQueueDao()

    @Provides
    fun provideCostTransactionDao(db: AppDatabase): CostTransactionDao = db.costTransactionDao()
}
