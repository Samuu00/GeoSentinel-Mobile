// Accesso alle segnalazioni di pericolo tramite le API del backend: pericoli attivi di un sentiero, segnalazioni
// dell'utente, coda da verificare per il gestore, pericoli vicini e invio di una nuova segnalazione.
package it.unical.geosentinel_mobile.hazard.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.HazardReportsApi
import it.unical.geosentinel_mobile.api.model.CreateHazardRequest
import it.unical.geosentinel_mobile.api.model.GeoPointDto
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.PagedModelHazardResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.apiCall

/** Elementi per pagina in "Le mie segnalazioni", uguale al default del backend: una pagina riempie lo schermo e oltre. */
const val MY_HAZARDS_PAGE_SIZE = 20

/** Raggio predefinito della ricerca "Pericoli vicini", in metri (5 km dalla posizione). */
const val NEARBY_HAZARDS_RADIUS_METERS = 5_000.0

/** Raggio massimo accettato dal server per la ricerca dei pericoli vicini, in metri (50 km). */
const val MAX_NEARBY_HAZARDS_RADIUS_METERS = 50_000.0

/** Numero massimo di pericoli restituiti dal server per una ricerca per vicinanza. */
const val MAX_NEARBY_HAZARDS = 50

/**
 * Repository delle segnalazioni di pericolo. Tutte le rotte richiedono il login: per l'utente ospite l'app non le
 * chiama nemmeno, perché il server risponderebbe 401. Nessuna risposta viene messa in cache: lo stato di una
 * segnalazione cambia quando un gestore la verifica, e un dato vecchio mostrato come attuale sarebbe fuorviante.
 */
class HazardRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val hazardsApi by lazy { HazardReportsApi(apiBaseUrl, client) }

    /** Restituisce i pericoli attivi del sentiero [trailId]; quali siano attivi (verificati e non risolti) lo decide il server. */
    suspend fun activeOn(trailId: String): Result<List<HazardResponse>> = apiCall { hazardsApi.findActiveHazardsByTrail(trailId) }

    /**
     * Restituisce una pagina delle segnalazioni fatte dall'utente, in qualunque stato, dalla più recente (l'ordine lo
     * fissa il server). [page] parte da 0.
     */
    suspend fun mine(page: Int, size: Int = MY_HAZARDS_PAGE_SIZE): Result<PagedModelHazardResponse> =
        apiCall { hazardsApi.findMyHazards(page = page, size = size) }

    /**
     * Restituisce una pagina delle segnalazioni ancora da verificare in tutto il parco, dalla più recente. È riservata
     * al gestore del parco: per gli altri ruoli il server risponde con un errore.
     */
    suspend fun pending(page: Int, size: Int): Result<PagedModelHazardResponse> =
        apiCall { hazardsApi.findPendingHazards(page = page, size = size) }

    /**
     * Restituisce i pericoli attivi (verificati e non risolti) segnalati da tutti gli utenti entro [radiusMeters] metri
     * da [center], dal più vicino. Il raggio viene limitato all'intervallo accettato dal server.
     */
    suspend fun nearby(center: GeoPoint, radiusMeters: Double = NEARBY_HAZARDS_RADIUS_METERS): Result<List<HazardResponse>> =
        apiCall {
            hazardsApi.findHazardsNearby(
                lon = center.lon,
                lat = center.lat,
                radius = radiusMeters.coerceIn(1.0, MAX_NEARBY_HAZARDS_RADIUS_METERS),
                limit = MAX_NEARBY_HAZARDS,
            )
        }

    /**
     * Invia una nuova segnalazione nella posizione [location], eventualmente legata al sentiero [trailId]. La
     * segnalazione nasce nello stato `SUBMITTED` e compare tra i pericoli del sentiero solo dopo la verifica di un
     * gestore. Una descrizione vuota o di soli spazi viene inviata come assente. Il POST non ha una `Idempotency-Key`,
     * quindi su un errore di rete non si ritenta in automatico (si rischierebbe un doppione): decide l'utente.
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
