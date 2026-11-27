// Accesso alle API riservate al personale: stato di pericoli e sentieri, creazione di POI, posti letto dei rifugi,
// prezzo dei rifugi, nuove squadre di soccorso e azzeramento della verifica in due passaggi di un utente.
package it.unical.geosentinel_mobile.staff.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.AdministrationApi
import it.unical.geosentinel_mobile.api.HazardReportsApi
import it.unical.geosentinel_mobile.api.PointsOfInterestApi
import it.unical.geosentinel_mobile.api.TrailsApi
import it.unical.geosentinel_mobile.api.model.AdminMfaResetRequest
import it.unical.geosentinel_mobile.api.model.CreatePoiRequest
import it.unical.geosentinel_mobile.api.model.GeoPointDto
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.TrailDetailResponse
import it.unical.geosentinel_mobile.api.model.UpdateHazardStatusRequest
import it.unical.geosentinel_mobile.api.model.UpdateTrailStatusRequest
import it.unical.geosentinel_mobile.api.RefugesApi
import it.unical.geosentinel_mobile.api.RescueTeamsApi
import it.unical.geosentinel_mobile.api.model.CreateRescueTeamRequest
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.api.model.RescueTeamResponse
import it.unical.geosentinel_mobile.api.model.UpdateCapacityRequest
import it.unical.geosentinel_mobile.api.model.UpdateRefugePriceRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.apiCall

/**
 * Repository delle azioni del personale (gestori del parco e amministratori). Le regole, come le transizioni di stato
 * ammesse e i permessi, le decide il server: qui non ci sono controlli. Nessuna di queste richieste ha una
 * `Idempotency-Key`, quindi su un errore di rete non si ritenta in automatico: decide l'utente se riprovare.
 */
class StaffRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val hazardsApi by lazy { HazardReportsApi(apiBaseUrl, client) }
    private val trailsApi by lazy { TrailsApi(apiBaseUrl, client) }
    private val poisApi by lazy { PointsOfInterestApi(apiBaseUrl, client) }
    private val refugesApi by lazy { RefugesApi(apiBaseUrl, client) }
    private val teamsApi by lazy { RescueTeamsApi(apiBaseUrl, client) }
    private val adminApi by lazy { AdministrationApi(apiBaseUrl, client) }

    /** Cambia lo stato della segnalazione di pericolo [hazardId] (per esempio la verifica o il rifiuto). */
    suspend fun changeHazardStatus(hazardId: String, status: UpdateHazardStatusRequest.Status): Result<HazardResponse> =
        apiCall { hazardsApi.changeHazardStatus(hazardId, UpdateHazardStatusRequest(status = status)) }

    /** Cambia lo stato del sentiero [trailId] e restituisce il dettaglio aggiornato. */
    suspend fun changeTrailStatus(trailId: String, status: UpdateTrailStatusRequest.Status): Result<TrailDetailResponse> =
        apiCall { trailsApi.changeTrailStatus(trailId, UpdateTrailStatusRequest(status = status)) }

    /** Crea un punto di interesse in [location], facoltativamente legato al sentiero [trailId]. Il nome si invia senza spazi ai bordi. */
    suspend fun createPoi(name: String, type: CreatePoiRequest.Type, location: GeoPoint, trailId: String?): Result<PoiResponse> =
        apiCall {
            poisApi.createPoi(
                CreatePoiRequest(
                    location = GeoPointDto(lat = location.lat, lon = location.lon),
                    name = name.trim(),
                    type = type,
                    trailId = trailId,
                ),
            )
        }

    /** Imposta i posti letto del rifugio [refugeId]. */
    suspend fun updateRefugeCapacity(refugeId: String, capacity: Int): Result<RefugeResponse> =
        apiCall { refugesApi.updateRefugeCapacity(refugeId, UpdateCapacityRequest(capacity = capacity)) }

    /** Imposta il prezzo a persona per notte del rifugio [refugeId] in centesimi; null lo toglie. */
    suspend fun updateRefugePrice(refugeId: String, pricePerNightCents: Int?): Result<RefugeResponse> =
        apiCall { refugesApi.updateRefugePrice(refugeId, UpdateRefugePriceRequest(pricePerNightCents = pricePerNightCents)) }

    /** Crea una nuova squadra di soccorso con nome, nominativo radio, numero di contatto e posizione della base. */
    suspend fun createRescueTeam(
        name: String,
        callSign: String,
        contactNumber: String,
        baseLocation: GeoPoint
    ): Result<RescueTeamResponse> = apiCall {
        teamsApi.createRescueTeam(
            CreateRescueTeamRequest(
                baseLocation = GeoPointDto(lat = baseLocation.lat, lon = baseLocation.lon),
                callSign = callSign,
                contactNumber = contactNumber,
                name = name,
            ),
        )
    }

    /**
     * Azzera la verifica in due passaggi dell'utente con [email]. È riservata agli amministratori, da usare dopo aver
     * verificato l'identità della persona fuori dall'app. Il server chiude tutte le sessioni dell'utente e gli invia
     * un'email. Risponde 404 se l'email è sconosciuta o l'utente non ha la verifica attiva, 422 `business-rule` se
     * l'amministratore prova ad azzerare la propria.
     */
    suspend fun resetUserMfa(email: String): Result<Unit> = apiCall { adminApi.resetUserMfa(AdminMfaResetRequest(email.trim())) }
}
