package it.unical.geosentinel_mobile.refuge.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.RefugesApi
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.apiCall
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

// Limiti del server per /refuges/nearby, come sentieri e POI
const val MAX_REFUGES_RADIUS_METERS = 50_000.0
const val MAX_NEARBY_REFUGES = 50

// Rifugi (M5.1). Richiedono il login (/refuges/**): per l'ospite l'app non li chiede. Niente cache: solo rete.
class RefugeRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val refugesApi by lazy { RefugesApi(apiBaseUrl, client) }

    /** I rifugi entro [radiusMeters] da [center], dal più vicino (l'ordine lo dà il server). */
    suspend fun nearby(center: GeoPoint, radiusMeters: Double = MAX_REFUGES_RADIUS_METERS): Result<List<RefugeResponse>> =
        apiCall {
            refugesApi.findRefugesNearby(
                lon = center.lon,
                lat = center.lat,
                radius = radiusMeters.coerceIn(1.0, MAX_REFUGES_RADIUS_METERS),
                limit = MAX_NEARBY_REFUGES,
            )
        }

    suspend fun get(id: String): Result<RefugeResponse> = apiCall { refugesApi.getRefuge(id) }

    /**
     * Posti liberi per [nights] notti a partire da [from], in ordine. Il server conta le notti in `[from, to)` e rifiuta
     * un `from` nel passato o più di 14 notti (422).
     */
    suspend fun availability(id: String, from: LocalDate, nights: Int = AVAILABILITY_NIGHTS): Result<List<NightAvailability>> =
        apiCall { refugesApi.getRefugeAvailability(id, from = from, to = from.plus(DatePeriod(days = nights))) }
            .mapCatching { response ->
                // chiavi "2026-10-05" (ISO): una data illeggibile è un errore, non una notte inventata
                response.availableBedsPerNight
                    .map { (date, beds) -> NightAvailability(LocalDate.parse(date), beds) }
                    .sortedBy { it.date }
            }
}
