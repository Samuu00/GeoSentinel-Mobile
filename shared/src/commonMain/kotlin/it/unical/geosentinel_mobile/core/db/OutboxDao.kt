package it.unical.geosentinel_mobile.core.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

enum class ActionKind { BOOKING, SOS }

/**
 * Un'azione dell'utente in attesa di una risposta definitiva (outbox, M5.2/M6). La `Idempotency-Key` si salva qui
 * PRIMA della richiesta e si cancella solo con un 2xx o un 4xx che non si ritenta: sopravvive alla chiusura dell'app.
 * [ownerId]: l'utente che l'ha fatta. Il server lega la chiave all'utente, quindi un'azione si rimanda solo per lui
 * (con un altro account creerebbe una prenotazione a nome suo).
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey val idempotencyKey: String,
    val kind: ActionKind,
    val ownerId: String,
    val payloadJson: String,
    val createdAt: Instant,
    val attempts: Int = 0,
) {
    // il payload ha date e rifugio (o posizione e note mediche per l'SOS): mai nei log
    override fun toString() = "PendingActionEntity(kind=$kind, attempts=$attempts)"
}

@Dao
interface OutboxDao {
    // la chiave è un UUID nuovo: un conflitto è un bug, meglio un errore che sovrascrivere un'azione in corso
    @Insert
    suspend fun insert(action: PendingActionEntity)

    @Query("SELECT * FROM pending_actions WHERE kind = :kind AND ownerId = :ownerId ORDER BY createdAt ASC")
    suspend fun pending(kind: ActionKind, ownerId: String): List<PendingActionEntity>

    @Query("SELECT * FROM pending_actions WHERE kind = :kind AND ownerId = :ownerId ORDER BY createdAt ASC")
    fun observePending(kind: ActionKind, ownerId: String): Flow<List<PendingActionEntity>>

    @Query("UPDATE pending_actions SET attempts = attempts + 1 WHERE idempotencyKey = :key")
    suspend fun incrementAttempts(key: String)

    @Query("DELETE FROM pending_actions WHERE idempotencyKey = :key")
    suspend fun delete(key: String)
}
