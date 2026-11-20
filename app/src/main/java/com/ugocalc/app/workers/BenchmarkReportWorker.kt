package com.ugocalc.app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import androidx.hilt.work.HiltWorker

@HiltWorker
class BenchmarkReportWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val benchmarkRepository: BenchmarkRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = coroutineScope {
        try {
            val handler = BenchmarkPromotionHandler(BenchmarkRepositoryEvaluator(benchmarkRepository), com.ugocalc.app.workers.SettingsRepositoryAdapter(settingsRepository))
            val promoted = handler.handlePromotion()
            if (promoted) Log.i("BenchmarkReportWorker", "V2 promoted to visible mode")
            Result.success()
        } catch (e: Exception) {
            Log.e("BenchmarkReportWorker", "Error evaluating benchmark promotion", e)
            Result.retry()
        }
    }
}
