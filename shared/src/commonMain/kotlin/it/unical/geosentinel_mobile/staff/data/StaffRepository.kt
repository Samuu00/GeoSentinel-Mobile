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
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.apiCall

/**
 * Azioni del gestore del parco (M8.1). Le regole (transizioni, permessi) le decide il server: qui niente controlli.
 * Nessuna di queste richieste ha una `Idempotency-Key`: su un errore di rete non si ritenta da soli, decide l'utente.
 */
class StaffRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val hazardsApi by lazy { HazardReportsApi(apiBaseUrl, client) }
    private val trailsApi by lazy { TrailsApi(apiBaseUrl, client) }
    private val poisApi by lazy { PointsOfInterestApi(apiBaseUrl, client) }
    private val refugesApi by lazy { RefugesApi(apiBaseUrl, client) }
    private val teamsApi by lazy { RescueTeamsApi(apiBaseUrl, client) }
    private val adminApi by lazy { AdministrationApi(apiBaseUrl, client) }

    suspend fun changeHazardStatus(hazardId: String, status: UpdateHazardStatusRequest.Status): Result<HazardResponse> =
        apiCall { hazardsApi.changeHazardStatus(hazardId, UpdateHazardStatusRequest(status = status)) }

    suspend fun changeTrailStatus(trailId: String, status: UpdateTrailStatusRequest.Status): Result<TrailDetailResponse> =
        apiCall { trailsApi.changeTrailStatus(trailId, UpdateTrailStatusRequest(status = status)) }

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

    suspend fun updateRefugeCapacity(refugeId: String, capacity: Int): Result<RefugeResponse> =
        apiCall { refugesApi.updateRefugeCapacity(refugeId, UpdateCapacityRequest(capacity = capacity)) }

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

    // Solo ADMIN, dopo aver verificato l'identità fuori dall'app (M10.3). Il server chiude tutte le sessioni
    // dell'utente e gli manda un'email. 404: email sconosciuta o senza 2FA; 422 business-rule: la propria.
    suspend fun resetUserMfa(email: String): Result<Unit> = apiCall { adminApi.resetUserMfa(AdminMfaResetRequest(email.trim())) }
}
