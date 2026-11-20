package com.ugocalc.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ugocalc.app.data.local.db.dao.CloudSyncQueueDao
import com.ugocalc.app.data.local.db.entity.CloudSyncQueueEntity
import com.ugocalc.app.data.remote.aws.normalizeAwsBaseUrl
import com.ugocalc.app.data.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

interface CloudSyncManager {
    suspend fun enqueue(eventType: String, payloadJson: String)

    /**
     * Svuota eventuali record locali orfani non associati a un utente autenticato
     * e cancella eventuali job WorkManager pendenti.
     */
    suspend fun clearLocalQueue() {}

    companion object {
        const val DEFAULT_USER_ID = "default-user-single-mode"
    }
}

internal fun interface CloudSyncUserResolver {
    suspend fun resolveAuthenticatedUserId(): String?
}

internal interface CloudSyncWorkScheduler {
    fun scheduleSync()
    fun cancelSync()
}

/**
 * Logica pura Local-First per CloudSyncManager:
 * - Se il backend AWS è disabilitato (`awsBaseUrl` vuoto/non valido) oppure l'utente non è
 *   autenticato con Cognito, NON scrive nella `cloud_sync_queue` e NON schedula WorkManager,
 *   evitando crescita infinita del DB locale e retry inutili in background.
 * - Ripulisce una volta eventuali record legacy [CloudSyncManager.DEFAULT_USER_ID].
 */
internal class LocalFirstCloudSyncCoordinator(
    private val cloudSyncQueueDao: CloudSyncQueueDao,
    private val userResolver: CloudSyncUserResolver,
    private val workScheduler: CloudSyncWorkScheduler,
    private val awsBaseUrl: String,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val cleanedLegacyQueue = AtomicBoolean(false)

    suspend fun enqueue(eventType: String, payloadJson: String): Boolean {
        if (normalizeAwsBaseUrl(awsBaseUrl) == null) {
            cleanupLegacyQueueOnce()
            workScheduler.cancelSync()
            return false
        }

        val userId = try {
            userResolver.resolveAuthenticatedUserId()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        if (userId.isNullOrBlank() || userId == CloudSyncManager.DEFAULT_USER_ID) {
            cleanupLegacyQueueOnce()
            workScheduler.cancelSync()
            return false
        }

        val event = CloudSyncQueueEntity(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            userId = userId,
            payloadJson = payloadJson,
            createdAtEpochMillis = clockMillis(),
        )
        cloudSyncQueueDao.enqueue(event)
        workScheduler.scheduleSync()
        return true
    }

    suspend fun clearLocalQueue() {
        cloudSyncQueueDao.deleteByUserId(CloudSyncManager.DEFAULT_USER_ID)
        workScheduler.cancelSync()
    }

    private suspend fun cleanupLegacyQueueOnce() {
        if (cleanedLegacyQueue.compareAndSet(false, true)) {
            runCatching {
                cloudSyncQueueDao.deleteByUserId(CloudSyncManager.DEFAULT_USER_ID)
            }
        }
    }
}

/**
 * Punto di accesso per accodare eventi da sincronizzare con il backend AWS (opzionale, Local-First).
 *
 * Ogni evento riceve un UUID generato client-side: la Lambda destinataria applica una condizione
 * di scrittura condizionale su questo UUID in DynamoDB, rendendo l'invio idempotente anche in caso
 * di retry ravvicinati o duplicati quasi simultanei.
 *
 * Se l'utente usa l'app in modalità locale senza login Cognito (o con backend AWS spento/disabilitato),
 * gli eventi non vengono accodati in `cloud_sync_queue` e WorkManager non viene avviato.
 */
@Singleton
class RealCloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudSyncQueueDao: CloudSyncQueueDao,
    private val workManager: WorkManager,
    private val authRepository: AuthRepository,
    @Named("aws_base_url") private val awsBaseUrl: String,
) : CloudSyncManager {
    companion object {
        private const val WORK_NAME = "cloud_sync"
        private const val AUTH_FAST_CHECK_MS = 300L
        private const val AUTH_COGNITO_TIMEOUT_MS = 1_000L
    }

    private val coordinator = LocalFirstCloudSyncCoordinator(
        cloudSyncQueueDao = cloudSyncQueueDao,
        userResolver = CloudSyncUserResolver {
            // Controllo rapido su DataStore: se non loggato, evita qualsiasi attesa su Amplify.
            val cachedLoggedIn = withTimeoutOrNull(AUTH_FAST_CHECK_MS) {
                runCatching { authRepository.isLoggedInFlow.first() }.getOrNull()
            }
            if (cachedLoggedIn == false) {
                return@CloudSyncUserResolver null
            }

            val cognitoUser = withTimeoutOrNull(AUTH_COGNITO_TIMEOUT_MS) {
                runCatching { authRepository.getCurrentUser() }.getOrNull()
            }
            if (cognitoUser != null && cognitoUser.userId.isNotBlank()) {
                return@CloudSyncUserResolver cognitoUser.userId
            }

            withTimeoutOrNull(AUTH_FAST_CHECK_MS) {
                runCatching { authRepository.currentUserFlow.first() }.getOrNull()?.userId
            }
        },
        workScheduler = object : CloudSyncWorkScheduler {
            override fun scheduleSync() {
                runCatching {
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

            override fun cancelSync() {
                runCatching { workManager.cancelUniqueWork(WORK_NAME) }
            }
        },
        awsBaseUrl = awsBaseUrl,
    )

    override suspend fun enqueue(eventType: String, payloadJson: String) {
        coordinator.enqueue(eventType, payloadJson)
    }

    override suspend fun clearLocalQueue() {
        coordinator.clearLocalQueue()
    }
}
