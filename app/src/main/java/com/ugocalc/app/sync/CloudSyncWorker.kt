package com.ugocalc.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ugocalc.app.data.local.db.dao.CloudSyncQueueDao
import com.ugocalc.app.data.remote.aws.CloudSyncClient
import com.ugocalc.app.data.remote.aws.SyncResult
import com.ugocalc.app.data.remote.aws.normalizeAwsBaseUrl
import com.ugocalc.app.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Named

internal enum class SyncWorkerOutcome {
    SUCCESS,
    RETRY,
}

internal class CloudSyncRunner(
    private val cloudSyncQueueDao: CloudSyncQueueDao,
    private val cloudSyncClient: CloudSyncClient,
    private val userResolver: CloudSyncUserResolver,
    private val awsBaseUrl: String,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val logDebug: (String) -> Unit = {},
    private val logWarn: (String) -> Unit = {},
    private val logError: (String) -> Unit = {},
) {
    suspend fun run(runAttemptCount: Int = 0): SyncWorkerOutcome {
        if (normalizeAwsBaseUrl(awsBaseUrl) == null) {
            return SyncWorkerOutcome.SUCCESS
        }

        // Rimuovi eventuali record legacy non autenticati.
        runCatching { cloudSyncQueueDao.deleteByUserId(CloudSyncManager.DEFAULT_USER_ID) }

        val userId = try {
            userResolver.resolveAuthenticatedUserId()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (userId.isNullOrBlank()) {
            // Utente non autenticato: non contattare il backend e non riaccodare retry.
            return SyncWorkerOutcome.SUCCESS
        }

        if (runAttemptCount >= maxAttempts) {
            logWarn("Max WorkManager attempts ($maxAttempts) reached — stopping background retries")
            return SyncWorkerOutcome.SUCCESS
        }

        val pending = cloudSyncQueueDao.getPending(limit = 50)
        if (pending.isEmpty()) return SyncWorkerOutcome.SUCCESS

        var hasRetryable = false

        for (event in pending) {
            val now = clockMillis()
            if (event.userId == CloudSyncManager.DEFAULT_USER_ID || event.userId.isBlank()) {
                cloudSyncQueueDao.markSynced(event.eventId, now)
                continue
            }

            val endpoint = endpointFor(event.eventType)
            val result = try {
                cloudSyncClient.sendEvent(
                    endpoint = endpoint,
                    eventId = event.eventId,
                    userId = event.userId,
                    payloadJson = event.payloadJson,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncResult.RetryableError(e.message ?: "unexpected error")
            }

            when (result) {
                is SyncResult.Success, is SyncResult.Duplicate -> {
                    cloudSyncQueueDao.markSynced(event.eventId, now)
                    logDebug("Synced ${event.eventId} (${event.eventType})")
                }
                is SyncResult.RetryableError -> {
                    val nextAttempt = event.attemptCount + 1
                    if (nextAttempt >= maxAttempts) {
                        cloudSyncQueueDao.markSynced(event.eventId, now)
                        logWarn("Max sync attempts ($maxAttempts) reached for ${event.eventId}: ${result.message} — discarding to preserve battery")
                    } else {
                        cloudSyncQueueDao.update(
                            event.copy(
                                attemptCount = nextAttempt,
                                lastAttemptEpochMillis = now,
                            ),
                        )
                        hasRetryable = true
                        logWarn("Retryable error for ${event.eventId} (attempt $nextAttempt/$maxAttempts): ${result.message}")
                    }
                }
                is SyncResult.FatalError -> {
                    cloudSyncQueueDao.markSynced(event.eventId, now)
                    logError("Fatal error for ${event.eventId}: ${result.message} — discarding")
                }
            }
        }

        pruneOldSynced()
        return if (hasRetryable) SyncWorkerOutcome.RETRY else SyncWorkerOutcome.SUCCESS
    }

    private suspend fun pruneOldSynced() {
        val thirtyDaysAgo = clockMillis() - TimeUnit.DAYS.toMillis(30)
        runCatching { cloudSyncQueueDao.pruneOldSynced(thirtyDaysAgo) }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5

        fun endpointFor(eventType: String): String = when (eventType) {
            CloudSyncEventType.BENCHMARK -> "benchmark"
            CloudSyncEventType.INFERENCE -> "inference"
            CloudSyncEventType.CALIBRATION -> "calibration"
            else -> eventType
        }
    }
}

/**
 * Processa la coda CloudSync: per ogni evento pendente invia al backend con retry limitato via
 * WorkManager (backoff esponenziale configurato in CloudSyncManager).
 *
 * - Se il backend AWS è disabilitato o l'utente non è autenticato → esce subito con `Result.success()`.
 * - Successo/Duplicate → evento marcato come sincronizzato (idempotente lato server).
 * - RetryableError → ritenta fino a [CloudSyncRunner.DEFAULT_MAX_ATTEMPTS], poi scarta per non
 *   consumare batteria/rete in loop se il backend AWS è spento.
 * - FatalError → evento scartato (richiesta non valida, non ha senso ritentare).
 */
@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cloudSyncQueueDao: CloudSyncQueueDao,
    private val cloudSyncClient: CloudSyncClient,
    private val authRepository: AuthRepository,
    @Named("aws_base_url") private val awsBaseUrl: String,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val runner = CloudSyncRunner(
            cloudSyncQueueDao = cloudSyncQueueDao,
            cloudSyncClient = cloudSyncClient,
            userResolver = CloudSyncUserResolver {
                val cachedLoggedIn = withTimeoutOrNull(300L) {
                    runCatching { authRepository.isLoggedInFlow.first() }.getOrNull()
                }
                if (cachedLoggedIn == false) return@CloudSyncUserResolver null

                val cognitoUser = withTimeoutOrNull(1_000L) {
                    runCatching { authRepository.getCurrentUser() }.getOrNull()
                }
                if (cognitoUser != null && cognitoUser.userId.isNotBlank()) {
                    return@CloudSyncUserResolver cognitoUser.userId
                }

                withTimeoutOrNull(300L) {
                    runCatching { authRepository.currentUserFlow.first() }.getOrNull()?.userId
                }
            },
            awsBaseUrl = awsBaseUrl,
            logDebug = { msg -> Log.d("CloudSyncWorker", msg) },
            logWarn = { msg -> Log.w("CloudSyncWorker", msg) },
            logError = { msg -> Log.e("CloudSyncWorker", msg) },
        )

        return when (runner.run(runAttemptCount = runAttemptCount)) {
            SyncWorkerOutcome.SUCCESS -> Result.success()
            SyncWorkerOutcome.RETRY -> Result.retry()
        }
    }
}
