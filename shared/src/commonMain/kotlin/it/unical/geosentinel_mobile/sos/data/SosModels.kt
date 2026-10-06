package it.unical.geosentinel_mobile.sos.data

import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.network.ApiError
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Il limite del server (RaiseSosRequest.medicalNotes): il campo non lascia scrivere oltre
const val MEDICAL_NOTES_MAX_LENGTH = 2000

/** Quello che si salva nell'outbox per rimandare l'SOS uguale (stessa Idempotency-Key). */
@Serializable
data class SosPayload(val lat: Double, val lon: Double, val medicalNotes: String?) {
    // posizione e note mediche: mai nei log
    override fun toString() = "SosPayload(…)"
}

sealed interface SosState {
    data object Idle : SosState

    /** Nell'outbox, senza ancora una risposta: si ritenta finché non arriva. [since] = quando è partito, anche prima di un riavvio. */
    data class Sending(val attempts: Int, val since: Instant) : SosState

    /** Il soccorso ha l'allerta (`ACTIVE` o `DISPATCHED`, in `alert.status`). */
    data class Raised(val alert: SosAlertResponse) : SosState

    /** Risposta definitiva negativa (un 4xx che non si ritenta): la richiesta NON è arrivata, si propone il 112. */
    data class Failed(val error: ApiError) : SosState
}
