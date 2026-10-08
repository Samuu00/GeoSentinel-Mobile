package it.unical.geosentinel_mobile.refuge.data

import it.unical.geosentinel_mobile.api.model.BookingResponse
import it.unical.geosentinel_mobile.core.network.ApiError
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

/** Una prenotazione in coda rifiutata dopo, o scaduta ([error] null): l'utente l'aveva vista "in invio". */
data class RejectedBooking(val key: String, val payload: BookingPayload, val error: ApiError?) {
    override fun toString() = "RejectedBooking(guests=${payload.guests}, expired=${error == null})"
}

/**
 * Come si salva un rifiuto nell'outbox (`BOOKING_REJECTED`): la prenotazione e il motivo come status e slug del
 * ProblemDetail (status null = scaduta in coda). Solo lo slug: titolo e dettaglio del server non si salvano.
 */
@Serializable
internal data class RejectionRecord(val booking: BookingPayload, val status: Int? = null, val slug: String? = null) {
    fun error(): ApiError? = when (status) {
        null -> null
        400 -> ApiError.Validation(emptyMap(), null)
        403 -> ApiError.Forbidden(null)
        404 -> ApiError.NotFound(null)
        409 -> ApiError.Conflict(slug ?: "conflict", null)
        else -> ApiError.BusinessRule(slug ?: "business-rule", null)
    }

    companion object {
        fun of(booking: BookingPayload, error: ApiError?): RejectionRecord = when (error) {
            null -> RejectionRecord(booking)
            is ApiError.Validation -> RejectionRecord(booking, 400)
            is ApiError.Forbidden -> RejectionRecord(booking, 403)
            is ApiError.NotFound -> RejectionRecord(booking, 404)
            is ApiError.Conflict -> RejectionRecord(booking, 409, error.slug)
            is ApiError.BusinessRule -> RejectionRecord(booking, 422, error.slug)
            // gli altri non arrivano qui (si ritentano): un motivo generico
            else -> RejectionRecord(booking, 422)
        }
    }
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
