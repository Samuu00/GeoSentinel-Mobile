package it.unical.geosentinel_mobile.refuge.data

import it.unical.geosentinel_mobile.api.model.BookingResponse
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * La richiesta salvata nell'outbox, per rimandarla uguale dopo un riavvio (stessa chiave e stesso body: un body diverso
 * con la stessa chiave è 422 `idempotency-key-reused`). Date in ISO ("2026-10-10"). Niente note: il backend non le ha.
 */
@Serializable
data class BookingPayload(val refugeId: String, val checkIn: LocalDate, val checkOut: LocalDate, val guests: Int)

/** Una prenotazione in coda ("in invio"): salvata, senza ancora una risposta definitiva. */
data class PendingBooking(val key: String, val payload: BookingPayload) {
    override fun toString() = "PendingBooking(guests=${payload.guests})"
}

/** Com'è andato un invio. */
sealed interface BookingOutcome {
    // 201 o 200 (un retry con la stessa chiave): la prenotazione c'è
    data class Booked(val booking: BookingResponse) : BookingOutcome {
        override fun toString() = "Booked"
    }
    // nessuna risposta definitiva (rete, 429, 409 resource-busy, 5xx, sessione da rinnovare): resta in coda
    data object Queued : BookingOutcome
    // rifiutata (409 capacity-exceeded, 422, 404...): tolta dalla coda, si mostra il motivo
    data class Rejected(val cause: Throwable) : BookingOutcome
}
