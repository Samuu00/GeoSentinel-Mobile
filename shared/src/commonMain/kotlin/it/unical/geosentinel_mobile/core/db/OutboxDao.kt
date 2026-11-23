// Outbox: la coda persistente delle azioni dell'utente che devono arrivare al server.
// In montagna la rete è spesso assente: una prenotazione o un SOS vengono prima salvati qui, con la loro
// `Idempotency-Key`, e poi inviati. Se l'invio fallisce per un motivo temporaneo l'azione resta in coda e viene
// ripetuta con la stessa chiave, così il server non la esegue due volte.
package it.unical.geosentinel_mobile.core.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import it.unical.geosentinel_mobile.core.session.SessionEnd
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Il tipo di un'azione in coda. Nel database è salvato come testo (il nome del valore): aggiungere un valore
 * non cambia lo schema e non richiede una migrazione.
 */
enum class ActionKind {
    /** Prenotazione di un rifugio da inviare. */
    BOOKING,
    /** Allerta SOS da inviare. */
    SOS,
    // Prenotazione in coda che il server ha poi rifiutato (o che è scaduta): resta finché l'utente non ha
    // visto l'esito in "Le mie prenotazioni".
    BOOKING_REJECTED,
    // SOS annullato prima che arrivasse la risposta del server: l'annullamento va verificato sul server (e
    // l'allerta chiusa) anche dopo un riavvio dell'app. Il payload non contiene dati personali.
    SOS_CANCEL,
}

/**
 * Un'azione dell'utente in attesa di una risposta definitiva dal server.
 *
 * La `Idempotency-Key` viene salvata qui prima di inviare la richiesta e cancellata solo con un 2xx o con un 4xx
 * che non si ripete: in questo modo sopravvive anche alla chiusura dell'app.
 *
 * @property ownerId l'utente che ha compiuto l'azione. Il server lega la chiave all'utente, quindi un'azione si
 *   rimanda solo per lui: con un altro account creerebbe, per esempio, una prenotazione a nome della persona sbagliata.
 * @property payloadJson il corpo della richiesta serializzato in JSON.
 * @property attempts quante volte si è già provato a inviarla.
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
    // Il payload contiene date e rifugio della prenotazione, oppure posizione e note mediche dell'SOS:
    // non deve mai comparire nei log, quindi il toString mostra solo tipo e tentativi.
    override fun toString() = "PendingActionEntity(kind=$kind, attempts=$attempts)"
}

/** Operazioni sulla tabella `pending_actions`, usate dai repository di prenotazioni e SOS. */
@Dao
interface OutboxDao {
    /**
     * Inserisce una nuova azione. La chiave è un UUID appena generato, quindi un conflitto sarebbe un bug: per
     * questo non si usa una strategia di sostituzione e si preferisce un errore alla sovrascrittura di un'azione
     * ancora in corso.
     */
    @Insert
    suspend fun insert(action: PendingActionEntity)

    /** Le azioni di un tipo per un utente, dalla più vecchia alla più recente. */
    @Query("SELECT * FROM pending_actions WHERE kind = :kind AND ownerId = :ownerId ORDER BY createdAt ASC")
    suspend fun pending(kind: ActionKind, ownerId: String): List<PendingActionEntity>

    /** Come [pending], ma come flusso che si aggiorna a ogni modifica della tabella. */
    @Query("SELECT * FROM pending_actions WHERE kind = :kind AND ownerId = :ownerId ORDER BY createdAt ASC")
    fun observePending(kind: ActionKind, ownerId: String): Flow<List<PendingActionEntity>>

    /** Incrementa il numero di tentativi dell'azione con chiave [key]. */
    @Query("UPDATE pending_actions SET attempts = attempts + 1 WHERE idempotencyKey = :key")
    suspend fun incrementAttempts(key: String)

    /** Elimina l'azione dopo una risposta definitiva del server. */
    @Query("DELETE FROM pending_actions WHERE idempotencyKey = :key")
    suspend fun delete(key: String)

    /**
     * Elimina le azioni di [ownerId] dei tipi indicati. Si usa al logout o alla cancellazione dell'account, perché
     * le azioni di quell'utente contengono dati personali (posizione, note mediche, date) che non devono restare
     * sul telefono.
     */
    @Query("DELETE FROM pending_actions WHERE ownerId = :ownerId AND kind IN (:kinds)")
    suspend fun deleteOwner(ownerId: String, kinds: List<ActionKind>)

    /**
     * Cambia tipo e payload di un'azione mantenendo la stessa chiave, per esempio quando una prenotazione in coda
     * viene rifiutata e diventa [ActionKind.BOOKING_REJECTED].
     */
    @Query("UPDATE pending_actions SET kind = :kind, payloadJson = :payloadJson WHERE idempotencyKey = :key")
    suspend fun replace(key: String, kind: ActionKind, payloadJson: String)
}

/**
 * Pulisce l'outbox alla fine della sessione di [ownerId], in base al motivo.
 *
 * - Logout: l'utente lascia il telefono, quindi si eliminano le sue azioni con dati personali (posizione e note
 *   mediche di un SOS, date delle prenotazioni). Resta solo la verifica di un SOS annullato, che non contiene dati
 *   personali e riparte al successivo login dello stesso utente.
 * - Account cancellato: si elimina tutto.
 * - Sessione scaduta: non si elimina nulla, perché dopo il nuovo login l'SOS in coda deve ripartire.
 */
suspend fun OutboxDao.clearAfterSession(ownerId: String, reason: SessionEnd) = when (reason) {
    SessionEnd.LOGOUT -> deleteOwner(ownerId, ActionKind.entries - ActionKind.SOS_CANCEL)
    SessionEnd.ACCOUNT_DELETED -> deleteOwner(ownerId, ActionKind.entries)
    SessionEnd.EXPIRED -> Unit
}
