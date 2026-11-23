// Accesso alle API dei rifugi: rifugi vicini a un punto, dettaglio di un rifugio e posti liberi per notte.
// Tutte le rotte richiedono il login, quindi l'app non le chiama per l'ospite.
package it.unical.geosentinel_mobile.refuge.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.RefugesApi
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.apiCall
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

// Limiti imposti dal server alla ricerca dei rifugi vicini, gli stessi di sentieri e POI: raggio massimo in metri e
// numero massimo di risultati.
const val MAX_REFUGES_RADIUS_METERS = 50_000.0
const val MAX_NEARBY_REFUGES = 50

/**
 * Repository dei rifugi. Non ha una cache locale: ogni lettura va in rete. Il client HTTP autenticato arriva come
 * funzione e si crea alla prima chiamata.
 */
class RefugeRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val refugesApi by lazy { RefugesApi(apiBaseUrl, client) }

    /**
     * Rifugi entro [radiusMeters] da [center], ordinati dal più vicino (l'ordine lo dà il server). Il raggio si porta
     * dentro i limiti accettati dal server.
     */
    suspend fun nearby(center: GeoPoint, radiusMeters: Double = MAX_REFUGES_RADIUS_METERS): Result<List<RefugeResponse>> =
        apiCall {
            refugesApi.findRefugesNearby(
                lon = center.lon,
                lat = center.lat,
                radius = radiusMeters.coerceIn(1.0, MAX_REFUGES_RADIUS_METERS),
                limit = MAX_NEARBY_REFUGES,
            )
        }

    /** Dettaglio del rifugio [id]. */
    suspend fun get(id: String): Result<RefugeResponse> = apiCall { refugesApi.getRefuge(id) }

    /**
     * Posti liberi per [nights] notti a partire da [from], ordinati per data. Il server conta le notti da `from`
     * compreso a `to` escluso e risponde 422 se `from` è nel passato o se le notti sono più di 14.
     */
    suspend fun availability(id: String, from: LocalDate, nights: Int = AVAILABILITY_NIGHTS): Result<List<NightAvailability>> =
        apiCall { refugesApi.getRefugeAvailability(id, from = from, to = from.plus(DatePeriod(days = nights))) }
            .mapCatching { response ->
                // Le chiavi della mappa sono date ISO. Una data illeggibile fa fallire tutto il risultato invece di
                // produrre una notte inventata.
                response.availableBedsPerNight
                    .map { (date, beds) -> NightAvailability(LocalDate.parse(date), beds) }
                    .sortedBy { it.date }
            }
}
