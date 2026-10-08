package it.unical.geosentinel_mobile.core.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import it.unical.geosentinel_mobile.core.session.SessionEnd
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

// Salvato come testo (il nome): un valore nuovo non cambia lo schema e non serve una migrazione
enum class ActionKind {
    BOOKING,
    SOS,
    // prenotazione in coda rifiutata dopo (o scaduta): resta finché l'utente non l'ha letta in "Le mie prenotazioni"
    BOOKING_REJECTED,
    // SOS annullato prima della risposta: si verifica sul server (e si chiude l'allerta) anche dopo un riavvio.
    // Nessun dato personale nel payload
    SOS_CANCEL,
}

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

    // Logout o account cancellato: le azioni di quell'utente con i dati personali (posizione, note mediche, date) non
    // restano sul telefono
    @Query("DELETE FROM pending_actions WHERE ownerId = :ownerId AND kind IN (:kinds)")
    suspend fun deleteOwner(ownerId: String, kinds: List<ActionKind>)

    // Un'azione cambia tipo (es. prenotazione in coda rifiutata): stessa chiave, payload nuovo
    @Query("UPDATE pending_actions SET kind = :kind, payloadJson = :payloadJson WHERE idempotencyKey = :key")
    suspend fun replace(key: String, kind: ActionKind, payloadJson: String)
}

/**
 * Pulizia a fine sessione. Logout e account cancellato: l'utente lascia il telefono, le sue azioni con dati personali
 * (posizione e note mediche di un SOS, date delle prenotazioni) non restano. Al logout resta solo la verifica di un SOS
 * annullato (nessun dato personale), che riparte al suo login. Sessione scaduta: nulla, rientrando l'SOS deve ripartire.
 */
suspend fun OutboxDao.clearAfterSession(ownerId: String, reason: SessionEnd) = when (reason) {
    SessionEnd.LOGOUT -> deleteOwner(ownerId, ActionKind.entries - ActionKind.SOS_CANCEL)
    SessionEnd.ACCOUNT_DELETED -> deleteOwner(ownerId, ActionKind.entries)
    SessionEnd.EXPIRED -> Unit
}
