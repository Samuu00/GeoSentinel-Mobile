// Prenotazioni dei rifugi con l'outbox: ogni prenotazione si salva nel database locale prima di partire, così non
// si perde se manca la rete o l'app si chiude, e si rimanda identica più tardi. Contiene anche la lettura delle
// prenotazioni dell'utente e la loro cancellazione.
package it.unical.geosentinel_mobile.refuge.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.BookingsApi
import it.unical.geosentinel_mobile.api.model.CreateBookingRequest
import it.unical.geosentinel_mobile.api.model.PagedModelBookingResponse
import it.unical.geosentinel_mobile.core.db.ActionKind
import it.unical.geosentinel_mobile.core.db.OutboxDao
import it.unical.geosentinel_mobile.core.db.PendingActionEntity
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.ContractViolationException
import it.unical.geosentinel_mobile.core.network.apiCall
import it.unical.geosentinel_mobile.core.network.isTransient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Numero di prenotazioni per pagina in "Le mie prenotazioni". */
const val MY_BOOKINGS_PAGE_SIZE = 20

/**
 * Repository delle prenotazioni basato sull'outbox.
 *
 * La `Idempotency-Key` nasce al tocco su "Prenota", si salva nell'outbox prima della richiesta e si cancella solo
 * quando arriva una risposta definitiva. Così una prenotazione rimasta senza risposta (rete assente, app chiusa) si
 * rimanda identica con [resendPending] e il server non la duplica: risponde 201 la prima volta e 200 ai tentativi
 * successivi con la stessa chiave.
 *
 * [clock] e [newKey] sono parametri per poterli sostituire nei test. [currentOwner] restituisce l'id dell'utente
 * loggato in questo momento: un'azione parte solo se appartiene a lui (nei test può essere null, e il controllo
 * si salta).
 */
@OptIn(ExperimentalUuidApi::class)
class BookingRepository(
    httpClient: () -> HttpClient,
    apiBaseUrl: String,
    private val outbox: OutboxDao,
    private val clock: Clock = Clock.System,
    private val newKey: () -> String = { Uuid.random().toString() },
    private val currentOwner: (suspend () -> String?)? = null,
) {
    private val client by lazy(httpClient)
    private val bookingsApi by lazy { BookingsApi(apiBaseUrl, client) }
    private val json = Json { ignoreUnknownKeys = true }
    // Un solo invio alla volta: così un nuovo tentativo all'avvio e un tocco su "Prenota" non possono mandare la
    // stessa chiave in parallelo.
    private val sending = Mutex()

    /**
     * Crea una nuova prenotazione per [ownerId], l'utente loggato: la salva nell'outbox e la invia subito.
     *
     * @return l'esito dell'invio; se è in coda, la prenotazione resta nell'outbox e ripartirà più tardi.
     */
    suspend fun book(ownerId: String, payload: BookingPayload): BookingOutcome {
        val key = newKey()
        outbox.insert(
            PendingActionEntity(
                idempotencyKey = key,
                kind = ActionKind.BOOKING,
                ownerId = ownerId,
                payloadJson = json.encodeToString(BookingPayload.serializer(), payload),
                createdAt = clock.now(),
            ),
        )
        // Un rifiuto qui si mostra subito nel form, quindi la riga si toglie dall'outbox invece di segnarla come
        // rifiutata.
        return sending.withLock { send(key, ownerId, payload).also { if (it is BookingOutcome.Rejected) outbox.delete(key) } }
    }

    /**
     * Rimanda le prenotazioni di [ownerId] rimaste in coda. Si chiama all'avvio da loggati e all'apertura di "Le mie
     * prenotazioni".
     *
     * Al primo esito "in coda" si ferma: le altre fallirebbero allo stesso modo, e ogni tentativo può tenere il lock
     * per decine di secondi. Le prenotazioni più vecchie di [MaxQueueAge] non partono più, perché l'utente non se le
     * aspetta più, e si segnano come scadute. Quelle rifiutate o scadute restano nell'outbox come
     * `BOOKING_REJECTED`, anche dopo un riavvio, finché l'utente non le chiude in "Le mie prenotazioni": le aveva
     * viste "in invio" e deve sapere che non esistono.
     *
     * @return gli esiti degli invii tentati.
     */
    suspend fun resendPending(ownerId: String): List<BookingOutcome> = sending.withLock {
        val outcomes = mutableListOf<BookingOutcome>()
        val oldest = clock.now() - MaxQueueAge
        for (action in outbox.pending(ActionKind.BOOKING, ownerId)) {
            val payload = decode(action)
            if (payload == null) {
                // Riga illeggibile (per esempio formato cambiato con un aggiornamento dell'app): non si può inviare,
                // quindi si toglie senza far crashare l'app.
                outbox.delete(action.idempotencyKey)
                continue
            }
            if (action.createdAt < oldest) {
                markRejected(action.idempotencyKey, payload, error = null)
                continue
            }
            outbox.incrementAttempts(action.idempotencyKey)
            val outcome = send(action.idempotencyKey, ownerId, payload)
            outcomes += outcome
            if (outcome is BookingOutcome.Rejected) markRejected(action.idempotencyKey, payload, (outcome.cause as? ApiException)?.error)
            if (outcome is BookingOutcome.Queued) break
        }
        outcomes
    }

    /** Trasforma la riga [key] in un rifiuto `BOOKING_REJECTED` con il motivo; [error] null indica una scadenza. */
    private suspend fun markRejected(key: String, payload: BookingPayload, error: ApiError?) =
        outbox.replace(key, ActionKind.BOOKING_REJECTED, json.encodeToString(RejectionRecord.serializer(), RejectionRecord.of(payload, error)))

    /** Prenotazioni di [ownerId] rifiutate dopo essere state in coda, o scadute, finché l'utente non le chiude. Le righe illeggibili si saltano. */
    fun observeRejected(ownerId: String): Flow<List<RejectedBooking>> =
        outbox.observePending(ActionKind.BOOKING_REJECTED, ownerId).map { actions ->
            actions.mapNotNull { action ->
                try {
                    json.decodeFromString(RejectionRecord.serializer(), action.payloadJson)
                        .let { RejectedBooking(action.idempotencyKey, it.booking, it.error()) }
                } catch (_: SerializationException) {
                    null
                }
            }
        }

    /** L'utente ha letto il rifiuto [key]: si toglie dall'outbox e non si mostra più. */
    suspend fun dismissRejected(key: String) = outbox.delete(key)

    /** Prenotazioni di [ownerId] ancora in coda, da mostrare come "in invio". Le righe illeggibili si saltano. */
    fun observePending(ownerId: String): Flow<List<PendingBooking>> =
        outbox.observePending(ActionKind.BOOKING, ownerId).map { actions ->
            actions.mapNotNull { action -> decode(action)?.let { PendingBooking(action.idempotencyKey, it) } }
        }

    /** Legge il corpo della prenotazione dalla riga dell'outbox; null se il JSON non è leggibile. */
    private fun decode(action: PendingActionEntity): BookingPayload? = try {
        json.decodeFromString(BookingPayload.serializer(), action.payloadJson)
    } catch (_: SerializationException) {
        null
    }

    /**
     * Esegue un tentativo di invio della prenotazione [key] e lo classifica in [BookingOutcome]. Se riesce, la riga
     * si toglie dall'outbox; se l'errore è temporaneo resta in coda; negli altri casi è un rifiuto.
     */
    private suspend fun send(key: String, ownerId: String, payload: BookingPayload): BookingOutcome {
        // La richiesta parte con il token dell'utente loggato in questo momento. Se nel frattempo è entrato un altro
        // account, la prenotazione resta in coda per il suo proprietario: altrimenti verrebbe creata a nome
        // dell'utente sbagliato.
        val owner = currentOwner
        if (owner != null && owner() != ownerId) return BookingOutcome.Queued
        val request = CreateBookingRequest(checkIn = payload.checkIn, checkOut = payload.checkOut, guests = payload.guests, refugeId = payload.refugeId)
        val result = try {
            apiCall { bookingsApi.createBooking(idempotencyKey = key, createBookingRequest = request) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: ContractViolationException) {
            // Risposta 2xx che non rispetta il contratto (un bug): si tratta come un errore del server, quindi la
            // prenotazione resta in coda e si riprova.
            return BookingOutcome.Queued
        }
        val failure = result.exceptionOrNull()
        return when {
            failure == null -> {
                outbox.delete(key)
                BookingOutcome.Booked(result.getOrThrow())
            }
            (failure as? ApiException)?.error?.isTransient() == true -> BookingOutcome.Queued
            // Cosa fare della riga lo decide il chiamante: dal form la toglie, dalla coda la segna come rifiutata.
            else -> BookingOutcome.Rejected(failure)
        }
    }

    private companion object {
        /** Età oltre la quale una prenotazione in coda non si invia più e si considera scaduta. */
        val MaxQueueAge = 24.hours
    }

    /** Una pagina delle prenotazioni dell'utente, dalla più lontana nel futuro (il server ordina per check-in decrescente). */
    suspend fun mine(page: Int, size: Int = MY_BOOKINGS_PAGE_SIZE): Result<PagedModelBookingResponse> =
        apiCall { bookingsApi.findMyBookings(page = page, size = size) }

    /** Annulla la prenotazione [bookingId] sul server. */
    suspend fun cancel(bookingId: String): Result<Unit> = apiCall { bookingsApi.cancelBooking(bookingId) }
}
