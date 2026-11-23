// Accesso alle API dell'SOS dal lato dell'escursionista: lancio, allerta attiva, aggiornamento della posizione e
// annullamento. Le chiamate sono semplici: la logica di outbox, retry e stato sta in SosController.
package it.unical.geosentinel_mobile.sos.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.SOSAlertsApi
import it.unical.geosentinel_mobile.api.model.GeoPointDto
import it.unical.geosentinel_mobile.api.model.RaiseSosRequest
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.api.model.UpdateSosLocationRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.apiCall

/**
 * Repository delle rotte SOS dell'escursionista. Non gestisce code né tentativi: outbox, retry e stato sono compito di
 * `SosController`. Il client HTTP autenticato arriva come funzione e si crea alla prima chiamata.
 */
class SosRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {
    private val client by lazy(httpClient)
    private val sosApi by lazy { SOSAlertsApi(apiBaseUrl, client) }

    /** Allerta attiva dell'utente; il server risponde 404 se non ce n'è nessuna. */
    suspend fun active(): Result<SosAlertResponse> = apiCall { sosApi.getMyActiveSos() }

    /**
     * Lancia l'SOS. Il server risponde 201 la prima volta e 200 se riceve di nuovo la stessa [idempotencyKey] o se
     * l'utente ha già un'allerta attiva: in tutti i casi è un successo e restituisce l'allerta.
     */
    suspend fun raise(idempotencyKey: String, payload: SosPayload): Result<SosAlertResponse> = apiCall {
        sosApi.raiseSos(
            RaiseSosRequest(location = GeoPointDto(lat = payload.lat, lon = payload.lon), medicalNotes = payload.medicalNotes),
            idempotencyKey = idempotencyKey,
        )
    }

    /**
     * Invia la nuova posizione [at] dell'allerta attiva [alertId] e restituisce l'allerta aggiornata. Il server
     * risponde 404 se l'allerta non è dell'utente e 422 se è già chiusa.
     */
    suspend fun updateLocation(alertId: String, at: GeoPoint): Result<SosAlertResponse> = apiCall {
        sosApi.updateSosLocation(alertId, UpdateSosLocationRequest(newLocation = GeoPointDto(lat = at.lat, lon = at.lon)))
    }

    /** Annulla l'allerta [alertId]. */
    suspend fun cancel(alertId: String): Result<Unit> = apiCall { sosApi.cancelSos(alertId) }
}
