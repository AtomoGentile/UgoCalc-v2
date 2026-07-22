package com.ugocalc.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ugocalc.app.data.local.db.dao.CloudSyncQueueDao
import com.ugocalc.app.data.local.db.entity.CloudSyncQueueEntity
import com.ugocalc.app.data.remote.aws.CloudSyncClient
import com.ugocalc.app.data.remote.aws.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Processa la coda CloudSync: per ogni evento pendente invia al backend con retry via WorkManager
 * (backoff esponenziale configurato in CloudSyncManager).
 *
 * - Successo/Duplicate → evento marcato come sincronizzato (idempotente lato server).
 * - RetryableError → Result.retry() lascia a WorkManager il backoff.
 * - FatalError → evento scartato (richiesta non valida, non ha senso ritentare).
 *
 * Il worker prune automaticamente i record già sincronizzati più vecchi di 30 giorni per
 * mantenere la tabella locale compatta.
 */
@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cloudSyncQueueDao: CloudSyncQueueDao,
    private val cloudSyncClient: CloudSyncClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pending = cloudSyncQueueDao.getPending(limit = 50)
        if (pending.isEmpty()) return Result.success()

        var hasRetryable = false

        for (event in pending) {
            val endpoint = endpointFor(event.eventType)
            val result = cloudSyncClient.sendEvent(
                endpoint = endpoint,
                eventId = event.eventId,
                userId = event.userId,
                payloadJson = event.payloadJson,
            )

            val now = System.currentTimeMillis()
            when (result) {
                is SyncResult.Success, is SyncResult.Duplicate -> {
                    cloudSyncQueueDao.markSynced(event.eventId, now)
                    Log.d("CloudSyncWorker", "Synced ${event.eventId} (${event.eventType})")
                }
                is SyncResult.RetryableError -> {
                    cloudSyncQueueDao.update(
                        event.copy(
                            attemptCount = event.attemptCount + 1,
                            lastAttemptEpochMillis = now,
                        ),
                    )
                    hasRetryable = true
                    Log.w("CloudSyncWorker", "Retryable error for ${event.eventId}: ${result.message}")
                }
                is SyncResult.FatalError -> {
                    cloudSyncQueueDao.markSynced(event.eventId, now)
                    Log.e("CloudSyncWorker", "Fatal error for ${event.eventId}: ${result.message} — discarding")
                }
            }
        }

        pruneOldSynced()
        return if (hasRetryable) Result.retry() else Result.success()
    }

    private fun endpointFor(eventType: String): String = when (eventType) {
        CloudSyncEventType.BENCHMARK -> "benchmark"
        CloudSyncEventType.INFERENCE -> "inference"
        else -> eventType
    }

    private suspend fun pruneOldSynced() {
        val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        cloudSyncQueueDao.pruneOldSynced(thirtyDaysAgo)
    }
}
