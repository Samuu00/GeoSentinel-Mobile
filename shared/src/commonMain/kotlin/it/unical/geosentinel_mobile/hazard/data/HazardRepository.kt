package it.unical.geosentinel_mobile.hazard.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.HazardReportsApi
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.core.network.apiCall

/** Pericoli. Richiedono il login: per l'ospite l'app non li chiede nemmeno (il server risponderebbe 401). */
class HazardRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val hazardsApi by lazy { HazardReportsApi(apiBaseUrl, client) }

    // Quali sono "attivi" (verificati da un gestore e non risolti) lo decide il server
    suspend fun activeOn(trailId: String): Result<List<HazardResponse>> = apiCall { hazardsApi.findActiveHazardsByTrail(trailId) }
}
