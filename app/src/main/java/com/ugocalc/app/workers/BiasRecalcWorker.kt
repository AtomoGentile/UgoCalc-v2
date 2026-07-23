package com.ugocalc.app.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Ricalcola tutti i fattori di correzione BIAS dai record con ground truth presenti in locale. Usato come trigger periodico o post-pull. */
@HiltWorker
class BiasRecalcWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val consentGiven = settingsRepository.settings.first().biasDataConsentGiven
            biasCorrectionRepository.recalculateAll(consentGiven)
            Log.d("BiasRecalcWorker", "Bias recalculation complete")
            Result.success()
        } catch (e: Exception) {
            Log.w("BiasRecalcWorker", "Recalc failed, will retry: ${e.message}")
            Result.retry()
        }
    }
}
