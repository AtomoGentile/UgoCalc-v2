package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Frequenza di ricerca per alimento (§ "Più ricercate" nella ricerca manuale): una riga per ogni
 * [NormalizedFoodEntryEntity] mai selezionato dalla ricerca. `onDelete = CASCADE` così, quando una
 * tabella viene ri-normalizzata (le righe vecchie sono cancellate e ricreate con nuovi id), la
 * cronologia associata alle righe sparite si pulisce da sola invece di restare orfana.
 */
@Entity(
    tableName = "search_frequency",
    foreignKeys = [
        ForeignKey(
            entity = NormalizedFoodEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class SearchFrequencyEntity(
    @PrimaryKey val entryId: Long,
    val searchCount: Int = 0,
    val lastSearchedAtEpochMillis: Long = 0,
    /** "Possibilità di fissare una voce" (§ richiesta utente): resta in cima indipendentemente dal conteggio. */
    val pinned: Boolean = false,
)
