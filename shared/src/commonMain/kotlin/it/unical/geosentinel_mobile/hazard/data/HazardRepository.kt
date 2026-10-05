package it.unical.geosentinel_mobile.hazard.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.HazardReportsApi
import it.unical.geosentinel_mobile.api.model.CreateHazardRequest
import it.unical.geosentinel_mobile.api.model.GeoPointDto
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.PagedModelHazardResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.apiCall

// Come il default del backend: una pagina riempie lo schermo e un po' oltre
const val MY_HAZARDS_PAGE_SIZE = 20

/** Pericoli. Richiedono il login: per l'ospite l'app non li chiede nemmeno (il server risponderebbe 401). */
class HazardRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val hazardsApi by lazy { HazardReportsApi(apiBaseUrl, client) }

    // Quali sono "attivi" (verificati da un gestore e non risolti) lo decide il server
    suspend fun activeOn(trailId: String): Result<List<HazardResponse>> = apiCall { hazardsApi.findActiveHazardsByTrail(trailId) }

    /**
     * Le segnalazioni dell'utente (M4.3), in ogni stato, dalla più recente (l'ordine lo fissa il server). [page] parte
     * da 0. Niente cache: lo stato cambia quando un gestore le verifica, e uno vecchio mostrato come attuale inganna.
     */
    suspend fun mine(page: Int, size: Int = MY_HAZARDS_PAGE_SIZE): Result<PagedModelHazardResponse> =
        apiCall { hazardsApi.findMyHazards(page = page, size = size) }

    /**
     * Nuova segnalazione (M4.1). Nasce `SUBMITTED`: tra i pericoli del sentiero compare solo dopo la verifica di un
     * gestore. Il POST non ha una `Idempotency-Key`, quindi su un errore di rete non si ritenta da soli: decide l'utente.
     */
    suspend fun report(
        type: CreateHazardRequest.Type,
        severity: CreateHazardRequest.Severity,
        location: GeoPoint,
        description: String?,
        trailId: String?,
    ): Result<HazardResponse> = apiCall {
        hazardsApi.createHazard(
            CreateHazardRequest(
                location = GeoPointDto(lat = location.lat, lon = location.lon),
                severity = severity,
                type = type,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                trailId = trailId,
            ),
        )
    }
}
