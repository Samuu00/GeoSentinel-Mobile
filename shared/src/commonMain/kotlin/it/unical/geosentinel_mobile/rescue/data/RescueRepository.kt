package it.unical.geosentinel_mobile.rescue.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.RescueTeamsApi
import it.unical.geosentinel_mobile.api.SOSAlertsApi
import it.unical.geosentinel_mobile.api.model.DispatchTeamRequest
import it.unical.geosentinel_mobile.api.model.PagedModelSosAlertResponse
import it.unical.geosentinel_mobile.api.model.RescueTeamResponse
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.apiCall

// Limiti del backend: /sos/nearby fino a 100 km e 50 allerte, /rescue-teams/available-nearby fino a 150 km e 30 squadre.
// Le squadre si cercano fino al massimo: in zone remote la più vicina può essere lontana, e arrivano già dalla più vicina
const val MAX_ALERTS_RADIUS_METERS = 100_000.0
const val MAX_NEARBY_ALERTS = 50
const val TEAMS_RADIUS_METERS = 150_000.0
const val MAX_NEARBY_TEAMS = 30
const val ALERTS_PAGE_SIZE = 20

/** Le rotte del soccorso (M7.2), solo `RESCUE_OPERATOR` e `ADMIN`: il server risponde 403 a tutti gli altri. */
class RescueRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {
    private val client by lazy(httpClient)
    private val sosApi by lazy { SOSAlertsApi(apiBaseUrl, client) }
    private val teamsApi by lazy { RescueTeamsApi(apiBaseUrl, client) }

    /** Allerte `ACTIVE` e `DISPATCHED`, dalla più vecchia (il default del server: chi aspetta da più tempo in cima). */
    suspend fun active(page: Int, size: Int = ALERTS_PAGE_SIZE): Result<PagedModelSosAlertResponse> =
        apiCall { sosApi.findActiveSosAlerts(page = page, size = size) }

    suspend fun alert(id: String): Result<SosAlertResponse> = apiCall { sosApi.getSosAlert(id) }

    /** Allerte aperte nel cerchio, dalla più vicina: il livello della mappa. */
    suspend fun nearby(center: GeoPoint, radiusMeters: Double): Result<List<SosAlertResponse>> = apiCall {
        sosApi.findSosAlertsNearby(
            lon = center.lon,
            lat = center.lat,
            radius = radiusMeters.coerceIn(1.0, MAX_ALERTS_RADIUS_METERS),
            limit = MAX_NEARBY_ALERTS,
        )
    }

    /** Squadre `AVAILABLE` attorno all'allerta, dalla più vicina. */
    suspend fun availableTeams(near: GeoPoint): Result<List<RescueTeamResponse>> = apiCall {
        teamsApi.findAvailableRescueTeamsNearby(lon = near.lon, lat = near.lat, radius = TEAMS_RADIUS_METERS, limit = MAX_NEARBY_TEAMS)
    }

    /** 409 `alert-already-taken` se un altro operatore è arrivato prima; 422 se la squadra non è più libera. */
    suspend fun dispatch(alertId: String, teamId: String): Result<SosAlertResponse> =
        apiCall { sosApi.dispatchRescueTeam(alertId, DispatchTeamRequest(teamId = teamId)) }

    /** Chiude un'allerta `ACTIVE` o `DISPATCHED` (libera la squadra); 422 se è già chiusa. */
    suspend fun resolve(alertId: String): Result<SosAlertResponse> = apiCall { sosApi.resolveSosAlert(alertId) }
}
