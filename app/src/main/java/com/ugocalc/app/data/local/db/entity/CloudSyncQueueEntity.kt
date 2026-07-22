package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Coda offline-first per CloudSyncManager: ogni evento da inviare al backend viene prima
 * persisto qui (con UUID idempotente), poi trasmesso da CloudSyncWorker. La Lambda lato server
 * applica una condizione di scrittura condizionale sull'eventId per garantire l'idempotenza
 * anche in caso di retry aggressivi o scritture quasi simultanee.
 *
 * userId viene risolto da CloudSyncManager via AuthRepository (utente autenticato, oppure
 * DEFAULT_USER_ID per dati preesistenti single-user). PK: userId, SK: eventId in DynamoDB.
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
