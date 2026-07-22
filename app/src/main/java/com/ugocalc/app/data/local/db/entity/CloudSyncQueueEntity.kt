package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Coda offline-first per CloudSyncManager: ogni evento da inviare al backend viene prima
 * persisto qui (con UUID idempotente), poi trasmesso da CloudSyncWorker. La Lambda lato server
 * applica una condizione di scrittura condizionale sull'eventId per garantire l'idempotenza
 * anche in caso di retry aggressivi o scritture quasi simultanee.
 *
 * userId è prefissato "simone" per ora (app single-user), ma è parte della chiave DynamoDB
 * (PK: userId, SK: eventId) per non dover riscrivere lo schema quando si aggiungeranno utenti.
 */
@Entity(tableName = "cloud_sync_queue")
data class CloudSyncQueueEntity(
    @PrimaryKey val eventId: String,
    val eventType: String,
    val userId: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val lastAttemptEpochMillis: Long? = null,
    val syncedAtEpochMillis: Long? = null,
)
