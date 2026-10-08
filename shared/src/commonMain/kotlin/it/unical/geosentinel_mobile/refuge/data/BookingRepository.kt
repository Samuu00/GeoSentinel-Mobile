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

const val MY_BOOKINGS_PAGE_SIZE = 20

/**
 * Prenotazioni (M5.2) con l'outbox: la `Idempotency-Key` nasce al tocco su "Prenota", si salva PRIMA della richiesta e
 * si cancella solo con una risposta definitiva. Così una prenotazione partita senza risposta (rete, app chiusa) si
 * rimanda uguale con [resendPending] e il server non la duplica (201 la prima volta, 200 ai retry).
 */
@OptIn(ExperimentalUuidApi::class)
class BookingRepository(
    httpClient: () -> HttpClient,
    apiBaseUrl: String,
    private val outbox: OutboxDao,
    private val clock: Clock = Clock.System,
    private val newKey: () -> String = { Uuid.random().toString() },
    // l'utente loggato adesso: un'azione parte solo per il suo proprietario (null nei test: nessun controllo)
    private val currentOwner: (suspend () -> String?)? = null,
) {
    private val client by lazy(httpClient)
    private val bookingsApi by lazy { BookingsApi(apiBaseUrl, client) }
    private val json = Json { ignoreUnknownKeys = true }
    // un invio alla volta: un retry all'avvio e un tocco su "Prenota" non mandano mai insieme la stessa chiave
    private val sending = Mutex()

    /** Una prenotazione nuova di [ownerId] (l'utente loggato). */
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
        // dal form il rifiuto si mostra subito nel form stesso: la riga si toglie
        return sending.withLock { send(key, ownerId, payload).also { if (it is BookingOutcome.Rejected) outbox.delete(key) } }
    }

    /**
     * Rimanda le prenotazioni rimaste in coda di [ownerId] (all'avvio da loggati e aprendo "Le mie prenotazioni").
     * Al primo errore di rete si ferma: le altre fallirebbero uguale, e ognuna può tenere il lock decine di secondi.
     * Quelle più vecchie di [MaxQueueAge] non partono più: l'utente non se le aspetta, e si dice che sono scadute.
     * Rifiutate o scadute restano nell'outbox come `BOOKING_REJECTED` (anche dopo un riavvio) finché l'utente non le ha
     * lette in "Le mie prenotazioni": le aveva viste "in invio" e deve sapere che non ci sono.
     */
    suspend fun resendPending(ownerId: String): List<BookingOutcome> = sending.withLock {
        val outcomes = mutableListOf<BookingOutcome>()
        val oldest = clock.now() - MaxQueueAge
        for (action in outbox.pending(ActionKind.BOOKING, ownerId)) {
            val payload = decode(action)
            if (payload == null) {
                // riga illeggibile (formato cambiato in un aggiornamento): non si può mandare, si toglie senza crash
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

    private suspend fun markRejected(key: String, payload: BookingPayload, error: ApiError?) =
        outbox.replace(key, ActionKind.BOOKING_REJECTED, json.encodeToString(RejectionRecord.serializer(), RejectionRecord.of(payload, error)))

    /** Le prenotazioni di [ownerId] rifiutate dopo la coda (o scadute), finché non le chiude. */
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

    /** L'utente ha letto il rifiuto: non si mostra più. */
    suspend fun dismissRejected(key: String) = outbox.delete(key)

    /** Le prenotazioni di [ownerId] ancora in coda, per mostrarle come "in invio" (quelle illeggibili si saltano). */
    fun observePending(ownerId: String): Flow<List<PendingBooking>> =
        outbox.observePending(ActionKind.BOOKING, ownerId).map { actions ->
            actions.mapNotNull { action -> decode(action)?.let { PendingBooking(action.idempotencyKey, it) } }
        }

    private fun decode(action: PendingActionEntity): BookingPayload? = try {
        json.decodeFromString(BookingPayload.serializer(), action.payloadJson)
    } catch (_: SerializationException) {
        null
    }

    private suspend fun send(key: String, ownerId: String, payload: BookingPayload): BookingOutcome {
        // Il bearer è quello dell'utente loggato adesso: se nel frattempo è entrato un altro, la prenotazione resta
        // in coda per il suo proprietario (con un altro account la chiave creerebbe una prenotazione a nome suo)
        val owner = currentOwner
        if (owner != null && owner() != ownerId) return BookingOutcome.Queued
        val request = CreateBookingRequest(checkIn = payload.checkIn, checkOut = payload.checkOut, guests = payload.guests, refugeId = payload.refugeId)
        val result = try {
            apiCall { bookingsApi.createBooking(idempotencyKey = key, createBookingRequest = request) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: ContractViolationException) {
            // 2xx fuori contratto (un bug): come un errore del server, resta in coda e si riprova
            return BookingOutcome.Queued
        }
        val failure = result.exceptionOrNull()
        return when {
            failure == null -> {
                outbox.delete(key)
                BookingOutcome.Booked(result.getOrThrow())
            }
            (failure as? ApiException)?.error?.isTransient() == true -> BookingOutcome.Queued
            // la riga la toglie (dal form) o la segna come rifiutata (dalla coda) chi ha chiamato
            else -> BookingOutcome.Rejected(failure)
        }
    }

    private companion object {
        val MaxQueueAge = 24.hours
    }

    /** Le prenotazioni dell'utente, dalla più lontana nel futuro (ordine del server: check-in decrescente). */
    suspend fun mine(page: Int, size: Int = MY_BOOKINGS_PAGE_SIZE): Result<PagedModelBookingResponse> =
        apiCall { bookingsApi.findMyBookings(page = page, size = size) }

    suspend fun cancel(bookingId: String): Result<Unit> = apiCall { bookingsApi.cancelBooking(bookingId) }
}
