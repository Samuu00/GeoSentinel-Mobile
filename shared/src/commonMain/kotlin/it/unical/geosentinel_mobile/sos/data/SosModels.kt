// Modelli dell'SOS dell'escursionista: il contenuto salvato nell'outbox per poter rimandare la richiesta e gli stati
// in cui può trovarsi un SOS, osservati dalla schermata SOS e dal resto dell'app.
package it.unical.geosentinel_mobile.sos.data

import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.network.ApiError
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Lunghezza massima delle note mediche accettata dal server; il campo di testo non permette di superarla. */
const val MEDICAL_NOTES_MAX_LENGTH = 2000

/**
 * Contenuto della richiesta di SOS salvato nell'outbox, per poterla rimandare identica con la stessa
 * `Idempotency-Key` anche dopo un riavvio dell'app.
 */
@Serializable
data class SosPayload(val lat: Double, val lon: Double, val medicalNotes: String?) {
    // toString scritto a mano: posizione e note mediche non devono mai finire nei log.
    override fun toString() = "SosPayload(…)"
}

/** Stato dell'SOS dell'utente, pubblicato da `SosController`. */
sealed interface SosState {
    /** Nessun SOS in corso. */
    data object Idle : SosState

    /**
     * SOS salvato nell'outbox e senza ancora una risposta: si ritenta finché non arriva. [attempts] conta i tentativi;
     * [since] è il momento della prima richiesta, conservato anche dopo un riavvio dell'app.
     */
    data class Sending(val attempts: Int, val since: Instant) : SosState

    /** Il soccorso ha ricevuto l'allerta, che è `ACTIVE` o `DISPATCHED` (lo dice `alert.status`). */
    data class Raised(val alert: SosAlertResponse) : SosState

    /** Risposta definitiva negativa (un 4xx che non si ritenta): l'allerta non è stata creata e si propone di chiamare il 112. */
    data class Failed(val error: ApiError) : SosState
}
