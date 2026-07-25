package com.ugocalc.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ugocalc.app.data.repository.AuthRepository
import com.ugocalc.app.data.local.db.dao.CloudSyncQueueDao
import com.ugocalc.app.data.local.db.entity.CloudSyncQueueEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface CloudSyncManager {
    suspend fun enqueue(eventType: String, payloadJson: String)

    companion object {
        const val DEFAULT_USER_ID = "default-user-single-mode"
    }
}

/**
 * Punto di accesso per accodare eventi da sincronizzare con il backend AWS (offline-first).
 *
 * Ogni evento riceve un UUID generato client-side: la Lambda destinataria applica una condizione
 * di scrittura condizionale su questo UUID in DynamoDB, rendendo l'invio idempotente anche in caso
 * di retry ravvicinati o duplicati quasi simultanei.
 *
 * La struttura userId/eventId rispecchia il PK/SK DynamoDB previsto (Prompt 8): aggiungere un
 * secondo utente in futuro non richiederà modifiche allo schema né alla coda locale.
 */
@Singleton
class RealCloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudSyncQueueDao: CloudSyncQueueDao,
    private val workManager: WorkManager,
    private val authRepository: AuthRepository,
) : CloudSyncManager {
    companion object {
        private const val WORK_NAME = "cloud_sync"
    }

    override suspend fun enqueue(eventType: String, payloadJson: String) {
        val userId = authRepository.getCurrentUser()?.userId ?: CloudSyncManager.DEFAULT_USER_ID
        val event = CloudSyncQueueEntity(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            userId = userId,
            payloadJson = payloadJson,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        cloudSyncQueueDao.enqueue(event)

        // Trigger immediato del worker (§8: "push immediato se online, altrimenti accoda").
        // WorkManager gestisce autonomamente la coda e i retry in base alla connettività.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
