package com.ugocalc.app.sync

import com.ugocalc.app.data.local.db.dao.CloudSyncQueueDao
import com.ugocalc.app.data.local.db.entity.CloudSyncQueueEntity
import com.ugocalc.app.data.remote.aws.CloudSyncClient
import com.ugocalc.app.data.remote.aws.SyncResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCloudSyncQueueDao : CloudSyncQueueDao {
    val rows = mutableListOf<CloudSyncQueueEntity>()

    override suspend fun enqueue(event: CloudSyncQueueEntity) {
        if (rows.none { it.eventId == event.eventId }) {
            rows.add(event)
        }
    }

    override suspend fun getPending(limit: Int): List<CloudSyncQueueEntity> =
        rows.filter { it.syncedAtEpochMillis == null }
            .sortedBy { it.createdAtEpochMillis }
            .take(limit)

    override suspend fun update(event: CloudSyncQueueEntity) {
        val idx = rows.indexOfFirst { it.eventId == event.eventId }
        if (idx >= 0) rows[idx] = event
    }

    override suspend fun markSynced(eventId: String, syncedAt: Long) {
        val idx = rows.indexOfFirst { it.eventId == eventId }
        if (idx >= 0) rows[idx] = rows[idx].copy(syncedAtEpochMillis = syncedAt)
    }

    override suspend fun pruneOldSynced(beforeEpochMillis: Long) {
        rows.removeAll { it.syncedAtEpochMillis != null && it.syncedAtEpochMillis < beforeEpochMillis }
    }

    override suspend fun deleteByUserId(userId: String) {
        rows.removeAll { it.userId == userId }
    }
}

private class RecordingScheduler : CloudSyncWorkScheduler {
    var scheduleCount = 0
    var cancelCount = 0

    override fun scheduleSync() {
        scheduleCount++
    }

    override fun cancelSync() {
        cancelCount++
    }
}

private class FakeCloudSyncClient(
    private val sendHandler: suspend (String, String, String, String) -> SyncResult = { _, _, _, _ -> SyncResult.Success },
) : CloudSyncClient {
    val sentEvents = mutableListOf<String>()

    override suspend fun fetchInferenceRecords(): List<String> = emptyList()

    override suspend fun fetchCalibrationRecords(): List<String> = emptyList()

    override suspend fun sendEvent(
        endpoint: String,
        eventId: String,
        userId: String,
        payloadJson: String,
    ): SyncResult {
        sentEvents.add("$endpoint:$eventId:$userId")
        return sendHandler(endpoint, eventId, userId, payloadJson)
    }
}

class CloudSyncLocalFirstTest {

    private val validBaseUrl = "https://example.execute-api.us-east-1.amazonaws.com/prod"

    @Test
    fun `unauthenticated user does not enqueue events and cleans legacy default-user rows`() = runBlocking {
        val dao = FakeCloudSyncQueueDao()
        dao.enqueue(
            CloudSyncQueueEntity(
                eventId = "legacy-1",
                eventType = CloudSyncEventType.INFERENCE,
                userId = CloudSyncManager.DEFAULT_USER_ID,
                payloadJson = "{}",
                createdAtEpochMillis = 100L,
            ),
        )
        val scheduler = RecordingScheduler()
        val coordinator = LocalFirstCloudSyncCoordinator(
            cloudSyncQueueDao = dao,
            userResolver = { null },
            workScheduler = scheduler,
            awsBaseUrl = validBaseUrl,
        )

        val enqueued = coordinator.enqueue(CloudSyncEventType.INFERENCE, """{"ok":true}""")

        assertFalse(enqueued)
        assertTrue(dao.rows.isEmpty())
        assertEquals(0, scheduler.scheduleCount)
        assertEquals(1, scheduler.cancelCount)
    }

    @Test
    fun `disabled awsBaseUrl skips enqueue even when user is authenticated`() = runBlocking {
        val dao = FakeCloudSyncQueueDao()
        val scheduler = RecordingScheduler()
        val coordinator = LocalFirstCloudSyncCoordinator(
            cloudSyncQueueDao = dao,
            userResolver = { "cognito-sub-123" },
            workScheduler = scheduler,
            awsBaseUrl = "",
        )

        val enqueued = coordinator.enqueue(CloudSyncEventType.BENCHMARK, """{"ok":true}""")

        assertFalse(enqueued)
        assertTrue(dao.rows.isEmpty())
        assertEquals(0, scheduler.scheduleCount)
        assertEquals(1, scheduler.cancelCount)
    }

    @Test
    fun `authenticated user with configured backend enqueues and schedules work`() = runBlocking {
        val dao = FakeCloudSyncQueueDao()
        val scheduler = RecordingScheduler()
        val coordinator = LocalFirstCloudSyncCoordinator(
            cloudSyncQueueDao = dao,
            userResolver = { "cognito-sub-123" },
            workScheduler = scheduler,
            awsBaseUrl = validBaseUrl,
            clockMillis = { 1234L },
        )

        val enqueued = coordinator.enqueue(CloudSyncEventType.INFERENCE, """{"id":1}""")

        assertTrue(enqueued)
        assertEquals(1, dao.rows.size)
        assertEquals("cognito-sub-123", dao.rows.first().userId)
        assertEquals(1234L, dao.rows.first().createdAtEpochMillis)
        assertEquals(1, scheduler.scheduleCount)
    }

    @Test
    fun `resolver exception degrades silently without queuing or crashing`() = runBlocking {
        val dao = FakeCloudSyncQueueDao()
        val scheduler = RecordingScheduler()
        val coordinator = LocalFirstCloudSyncCoordinator(
            cloudSyncQueueDao = dao,
            userResolver = { error("Amplify not configured") },
            workScheduler = scheduler,
            awsBaseUrl = validBaseUrl,
        )

        val enqueued = coordinator.enqueue(CloudSyncEventType.CALIBRATION, "{}")

        assertFalse(enqueued)
        assertTrue(dao.rows.isEmpty())
        assertEquals(0, scheduler.scheduleCount)
    }

    @Test
    fun `worker stops retrying after max attempts when backend is unreachable`() = runBlocking {
        val dao = FakeCloudSyncQueueDao()
        dao.enqueue(
            CloudSyncQueueEntity(
                eventId = "ev-1",
                eventType = CloudSyncEventType.INFERENCE,
                userId = "cognito-sub-123",
                payloadJson = "{}",
                createdAtEpochMillis = 1000L,
            ),
        )
        val client = FakeCloudSyncClient { _, _, _, _ -> SyncResult.RetryableError("network error") }
        val runner = CloudSyncRunner(
            cloudSyncQueueDao = dao,
            cloudSyncClient = client,
            userResolver = { "cognito-sub-123" },
            awsBaseUrl = validBaseUrl,
            maxAttempts = 3,
            clockMillis = { 5000L },
        )

        assertEquals(SyncWorkerOutcome.RETRY, runner.run(runAttemptCount = 0))
        assertEquals(1, dao.rows.first().attemptCount)
        assertEquals(SyncWorkerOutcome.RETRY, runner.run(runAttemptCount = 1))
        assertEquals(2, dao.rows.first().attemptCount)
        // Third attempt reaches maxAttempts=3 -> discards to prevent infinite battery drain
        assertEquals(SyncWorkerOutcome.SUCCESS, runner.run(runAttemptCount = 2))
        assertNotNull(dao.rows.first().syncedAtEpochMillis)
    }

    @Test
    fun `worker exits cleanly without network calls when unauthenticated`() = runBlocking {
        val dao = FakeCloudSyncQueueDao()
        dao.enqueue(
            CloudSyncQueueEntity(
                eventId = "ev-2",
                eventType = CloudSyncEventType.BENCHMARK,
                userId = "cognito-sub-123",
                payloadJson = "{}",
                createdAtEpochMillis = 1000L,
            ),
        )
        val client = FakeCloudSyncClient()
        val runner = CloudSyncRunner(
            cloudSyncQueueDao = dao,
            cloudSyncClient = client,
            userResolver = { null },
            awsBaseUrl = validBaseUrl,
        )

        val outcome = runner.run()

        assertEquals(SyncWorkerOutcome.SUCCESS, outcome)
        assertTrue(client.sentEvents.isEmpty())
    }

    @Test
    fun `worker handles unexpected sendEvent exception without crashing`() = runBlocking {
        val dao = FakeCloudSyncQueueDao()
        dao.enqueue(
            CloudSyncQueueEntity(
                eventId = "ev-3",
                eventType = CloudSyncEventType.INFERENCE,
                userId = "cognito-sub-123",
                payloadJson = "{}",
                createdAtEpochMillis = 1000L,
            ),
        )
        val client = FakeCloudSyncClient { _, _, _, _ -> throw IllegalStateException("boom") }
        val runner = CloudSyncRunner(
            cloudSyncQueueDao = dao,
            cloudSyncClient = client,
            userResolver = { "cognito-sub-123" },
            awsBaseUrl = validBaseUrl,
            maxAttempts = 2,
        )

        assertEquals(SyncWorkerOutcome.RETRY, runner.run(runAttemptCount = 0))
        assertEquals(SyncWorkerOutcome.SUCCESS, runner.run(runAttemptCount = 1))
    }
}
