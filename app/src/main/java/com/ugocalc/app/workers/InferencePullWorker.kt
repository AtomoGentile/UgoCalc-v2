package com.ugocalc.app.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ugocalc.app.data.remote.aws.normalizeAwsBaseUrl
import com.ugocalc.app.data.repository.AuthRepository
import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Named

/**
 * Scarica opzionalmente i record INFERENCE dell'utente dal backend AWS, fa upsert locale
 * (idempotente per clientId), poi ricalcola tutti i fattori di correzione BIAS dal dataset locale.
 * Degrada silenziosamente in modalità Local-First (senza retry loop in background) se l'utente
 * non è autenticato o se il backend AWS è spento/irraggiungibile.
 */
@HiltWorker
class InferencePullWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val telemetryRepository: V2TelemetryRepository,
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val settingsRepository: SettingsRepository,
    @Named("aws_base_url") private val awsBaseUrl: String,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (normalizeAwsBaseUrl(awsBaseUrl) == null) {
            return Result.success()
        }

        val isLoggedIn = withTimeoutOrNull(300L) {
            runCatching { authRepository.isLoggedInFlow.first() }.getOrNull()
        }
        if (isLoggedIn == false) {
            return Result.success()
        }

        val user = withTimeoutOrNull(1_500L) {
            runCatching { authRepository.getCurrentUser() }.getOrNull()
        } ?: withTimeoutOrNull(300L) {
            runCatching { authRepository.currentUserFlow.first() }.getOrNull()
        } ?: return Result.success()

        if (user.userId.isBlank()) {
            return Result.success()
        }

        try {
            val inserted = telemetryRepository.pullAndMerge()
            Log.d("InferencePullWorker", "Merged $inserted new inference records")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("InferencePullWorker", "Cloud pull unavailable, degrading to local data: ${e.message}")
        }

        try {
            val consentGiven = settingsRepository.settings.first().biasDataConsentGiven
            biasCorrectionRepository.recalculateAll(consentGiven)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("InferencePullWorker", "Local bias recalculation skipped: ${e.message}")
        }

        return Result.success()
    }
}
