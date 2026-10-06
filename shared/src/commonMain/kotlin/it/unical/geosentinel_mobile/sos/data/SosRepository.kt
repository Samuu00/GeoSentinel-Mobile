package it.unical.geosentinel_mobile.sos.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.SOSAlertsApi
import it.unical.geosentinel_mobile.api.model.GeoPointDto
import it.unical.geosentinel_mobile.api.model.RaiseSosRequest
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.api.model.UpdateSosLocationRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.apiCall

/** Le rotte SOS dell'escursionista. La logica (outbox, retry, stato) sta in `SosController`. */
class SosRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {
    private val client by lazy(httpClient)
    private val sosApi by lazy { SOSAlertsApi(apiBaseUrl, client) }

    /** L'allerta attiva dell'utente; 404 se non ce n'è nessuna. */
    suspend fun active(): Result<SosAlertResponse> = apiCall { sosApi.getMyActiveSos() }

    /** 201 la prima volta, 200 con la stessa [idempotencyKey] o con un'allerta già attiva: entrambi un successo. */
    suspend fun raise(idempotencyKey: String, payload: SosPayload): Result<SosAlertResponse> = apiCall {
        sosApi.raiseSos(
            RaiseSosRequest(location = GeoPointDto(lat = payload.lat, lon = payload.lon), medicalNotes = payload.medicalNotes),
            idempotencyKey = idempotencyKey,
        )
    }

    /** M6.2: la posizione nuova di un'allerta attiva. 404 se non è sua, 422 se è già chiusa. Risponde con l'allerta. */
    suspend fun updateLocation(alertId: String, at: GeoPoint): Result<SosAlertResponse> = apiCall {
        sosApi.updateSosLocation(alertId, UpdateSosLocationRequest(newLocation = GeoPointDto(lat = at.lat, lon = at.lon)))
    }

    suspend fun cancel(alertId: String): Result<Unit> = apiCall { sosApi.cancelSos(alertId) }
}
