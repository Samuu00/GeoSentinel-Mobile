// Accesso alle API del soccorso: allerte SOS aperte (in lista e sulla mappa), squadre libere vicine, invio di una
// squadra e chiusura di un'allerta. Le usano solo gli operatori del soccorso e gli amministratori.
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

// Limiti del server: le allerte vicine si cercano fino a 100 km e al massimo 50 per richiesta, le squadre libere fino
// a 150 km e al massimo 30. Le squadre si cercano sempre con il raggio massimo, perché in zone remote anche la più
// vicina può essere lontana; il server le restituisce già ordinate dalla più vicina.
const val MAX_ALERTS_RADIUS_METERS = 100_000.0
const val MAX_NEARBY_ALERTS = 50
const val TEAMS_RADIUS_METERS = 150_000.0
const val MAX_NEARBY_TEAMS = 30
/** Numero di allerte per pagina nella lista della scheda Allerte. */
const val ALERTS_PAGE_SIZE = 20

/**
 * Repository delle rotte del soccorso, riservate a `RESCUE_OPERATOR` e `ADMIN`: agli altri ruoli il server risponde
 * 403. Il client HTTP autenticato arriva come funzione e si crea alla prima chiamata.
 */
class RescueRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {
    private val client by lazy(httpClient)
    private val sosApi by lazy { SOSAlertsApi(apiBaseUrl, client) }
    private val teamsApi by lazy { RescueTeamsApi(apiBaseUrl, client) }

    /** Una pagina delle allerte `ACTIVE` e `DISPATCHED`, dalla più vecchia: chi aspetta da più tempo sta in cima (ordine del server). */
    suspend fun active(page: Int, size: Int = ALERTS_PAGE_SIZE): Result<PagedModelSosAlertResponse> =
        apiCall { sosApi.findActiveSosAlerts(page = page, size = size) }

    /** Dettaglio dell'allerta [id]. */
    suspend fun alert(id: String): Result<SosAlertResponse> = apiCall { sosApi.getSosAlert(id) }

    /** Allerte aperte nel cerchio di centro [center], dalla più vicina; servono per il livello della mappa. */
    suspend fun nearby(center: GeoPoint, radiusMeters: Double): Result<List<SosAlertResponse>> = apiCall {
        sosApi.findSosAlertsNearby(
            lon = center.lon,
            lat = center.lat,
            radius = radiusMeters.coerceIn(1.0, MAX_ALERTS_RADIUS_METERS),
            limit = MAX_NEARBY_ALERTS,
        )
    }

    /** Squadre libere (`AVAILABLE`) attorno al punto [near] dell'allerta, dalla più vicina. */
    suspend fun availableTeams(near: GeoPoint): Result<List<RescueTeamResponse>> = apiCall {
        teamsApi.findAvailableRescueTeamsNearby(lon = near.lon, lat = near.lat, radius = TEAMS_RADIUS_METERS, limit = MAX_NEARBY_TEAMS)
    }

    /**
     * Invia la squadra [teamId] all'allerta [alertId]. Risponde 409 `alert-already-taken` se un altro operatore l'ha
     * già presa in carico, 422 se la squadra non è più libera.
     */
    suspend fun dispatch(alertId: String, teamId: String): Result<SosAlertResponse> =
        apiCall { sosApi.dispatchRescueTeam(alertId, DispatchTeamRequest(teamId = teamId)) }

    /** Chiude un'allerta `ACTIVE` o `DISPATCHED`, liberando la squadra; risponde 422 se è già chiusa. */
    suspend fun resolve(alertId: String): Result<SosAlertResponse> = apiCall { sosApi.resolveSosAlert(alertId) }
}
