// Modelli delle prenotazioni dei rifugi usati dall'outbox: la richiesta salvata per poterla rimandare, le
// prenotazioni in coda o rifiutate da mostrare all'utente e l'esito di un singolo invio.
package it.unical.geosentinel_mobile.refuge.data

import it.unical.geosentinel_mobile.api.model.BookingResponse
import it.unical.geosentinel_mobile.core.network.ApiError
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Corpo della richiesta di prenotazione salvato nell'outbox, serializzato in JSON con le date in formato ISO
 * (AAAA-MM-GG).
 *
 * Si salva per poter rimandare la richiesta identica anche dopo un riavvio dell'app: il server riconosce un
 * duplicato dalla `Idempotency-Key`, ma se con la stessa chiave arriva un corpo diverso risponde 422
 * `idempotency-key-reused`.
 */
@Serializable
data class BookingPayload(val refugeId: String, val checkIn: LocalDate, val checkOut: LocalDate, val guests: Int)

/**
 * Prenotazione in coda, mostrata come "in invio": è salvata ma non ha ancora una risposta definitiva dal server.
 * [key] è la sua `Idempotency-Key`. Il `toString` non stampa date né rifugio.
 */
data class PendingBooking(val key: String, val payload: BookingPayload) {
    override fun toString() = "PendingBooking(guests=${payload.guests})"
}

/**
 * Prenotazione che l'utente aveva visto "in invio" e che poi il server ha rifiutato, oppure che è scaduta in coda
 * ([error] null). Resta visibile finché l'utente non la chiude, così sa che non è andata a buon fine.
 */
data class RejectedBooking(val key: String, val payload: BookingPayload, val error: ApiError?) {
    override fun toString() = "RejectedBooking(guests=${payload.guests}, expired=${error == null})"
}

/**
 * Forma in cui un rifiuto si salva nell'outbox (azione `BOOKING_REJECTED`): la prenotazione più il motivo, ridotto
 * allo status HTTP e allo slug dell'errore. Uno [status] null indica una prenotazione scaduta in coda. Titolo e
 * dettaglio del server non si salvano: il messaggio per l'utente si ricostruisce dallo slug.
 */
@Serializable
internal data class RejectionRecord(val booking: BookingPayload, val status: Int? = null, val slug: String? = null) {
    /** Ricostruisce l'[ApiError] dal motivo salvato; null se la prenotazione era scaduta in coda. */
    fun error(): ApiError? = when (status) {
        null -> null
        400 -> ApiError.Validation(emptyMap(), null)
        403 -> ApiError.Forbidden(null)
        404 -> ApiError.NotFound(null)
        409 -> ApiError.Conflict(slug ?: "conflict", null)
        else -> ApiError.BusinessRule(slug ?: "business-rule", null)
    }

    companion object {
        /** Crea il record di un rifiuto a partire dall'errore ricevuto, o di una scadenza se [error] è null. */
        fun of(booking: BookingPayload, error: ApiError?): RejectionRecord = when (error) {
            null -> RejectionRecord(booking)
            is ApiError.Validation -> RejectionRecord(booking, 400)
            is ApiError.Forbidden -> RejectionRecord(booking, 403)
            is ApiError.NotFound -> RejectionRecord(booking, 404)
            is ApiError.Conflict -> RejectionRecord(booking, 409, error.slug)
            is ApiError.BusinessRule -> RejectionRecord(booking, 422, error.slug)
            // Gli altri errori (rete, 429, 5xx) si ritentano e non dovrebbero arrivare qui: si salva un motivo
            // generico per sicurezza.
            else -> RejectionRecord(booking, 422)
        }
    }
}

/** Esito di un tentativo di invio di una prenotazione, restituito da [BookingRepository]. */
sealed interface BookingOutcome {
    /** Prenotazione creata: 201, oppure 200 se era un nuovo tentativo con la stessa chiave già accettato. */
    data class Booked(val booking: BookingResponse) : BookingOutcome {
        override fun toString() = "Booked"
    }
    /** Nessuna risposta definitiva (rete assente, 429, 409 `resource-busy`, 5xx, sessione da rinnovare): resta in coda. */
    data object Queued : BookingOutcome
    /** Rifiutata in modo definitivo (per esempio 409 `capacity-exceeded`, 422, 404): esce dalla coda e si mostra il motivo. */
    data class Rejected(val cause: Throwable) : BookingOutcome
}
