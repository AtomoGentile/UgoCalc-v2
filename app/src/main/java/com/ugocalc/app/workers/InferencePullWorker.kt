package com.ugocalc.app.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ugocalc.app.data.repository.AuthRepository
import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first


/**
 * Scarica i record INFERENCE dell'utente dal backend, fa upsert locale (idempotente per clientId),
 * poi ricalcola tutti i fattori di correzione BIAS dal dataset aggiornato.
 * Schedulato al login e disponibile per trigger manuali.
 */
@HiltWorker
class InferencePullWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val telemetryRepository: V2TelemetryRepository,
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        authRepository.getCurrentUser() ?: return Result.success()
        return try {
            val inserted = telemetryRepository.pullAndMerge()
            Log.d("InferencePullWorker", "Merged $inserted new inference records")
            val consentGiven = settingsRepository.settings.first().biasDataConsentGiven
            biasCorrectionRepository.recalculateAll(consentGiven)
            Result.success()
        } catch (e: Exception) {
            Log.w("InferencePullWorker", "Pull failed, will retry: ${e.message}")
            Result.retry()
        }
    }
}
