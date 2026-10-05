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
import it.unical.geosentinel_mobile.core.network.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock
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
        return sending.withLock { send(key, payload) }
    }

    /** Rimanda le prenotazioni rimaste in coda di [ownerId] (all'avvio da loggati e aprendo "Le mie prenotazioni"). */
    suspend fun resendPending(ownerId: String): List<BookingOutcome> = sending.withLock {
        outbox.pending(ActionKind.BOOKING, ownerId).map { action ->
            outbox.incrementAttempts(action.idempotencyKey)
            send(action.idempotencyKey, json.decodeFromString(BookingPayload.serializer(), action.payloadJson))
        }
    }

    /** Le prenotazioni di [ownerId] ancora in coda, per mostrarle come "in invio". */
    fun observePending(ownerId: String): Flow<List<PendingBooking>> =
        outbox.observePending(ActionKind.BOOKING, ownerId).map { actions ->
            actions.map { PendingBooking(it.idempotencyKey, json.decodeFromString(BookingPayload.serializer(), it.payloadJson)) }
        }

    private suspend fun send(key: String, payload: BookingPayload): BookingOutcome {
        val request = CreateBookingRequest(checkIn = payload.checkIn, checkOut = payload.checkOut, guests = payload.guests, refugeId = payload.refugeId)
        val result = apiCall { bookingsApi.createBooking(idempotencyKey = key, createBookingRequest = request) }
        val failure = result.exceptionOrNull()
        return when {
            failure == null -> {
                outbox.delete(key)
                BookingOutcome.Booked(result.getOrThrow())
            }
            failure.isTransient() -> BookingOutcome.Queued
            else -> {
                outbox.delete(key)
                BookingOutcome.Rejected(failure)
            }
        }
    }

    /** Le prenotazioni dell'utente, dalla più lontana nel futuro (ordine del server: check-in decrescente). */
    suspend fun mine(page: Int, size: Int = MY_BOOKINGS_PAGE_SIZE): Result<PagedModelBookingResponse> =
        apiCall { bookingsApi.findMyBookings(page = page, size = size) }

    suspend fun cancel(bookingId: String): Result<Unit> = apiCall { bookingsApi.cancelBooking(bookingId) }
}

// Senza risposta definitiva la chiave resta: rete, 429, 409 resource-busy, 5xx (il client ha già ritentato quelli che
// il contratto permette) e 401 (sessione da rinnovare: dopo il login dello stesso utente la chiave vale ancora).
// Ogni altro 4xx è definitivo e ripeterlo uguale non serve.
private fun Throwable.isTransient(): Boolean = when (val error = (this as? ApiException)?.error) {
    ApiError.Network, is ApiError.RateLimited, is ApiError.Server, is ApiError.Unauthorized -> true
    is ApiError.Conflict -> error.slug == "resource-busy"
    else -> false
}
