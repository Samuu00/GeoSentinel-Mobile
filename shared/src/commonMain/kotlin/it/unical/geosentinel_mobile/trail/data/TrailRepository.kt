package it.unical.geosentinel_mobile.trail.data

import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart
import it.unical.geosentinel_mobile.api.PointsOfInterestApi
import it.unical.geosentinel_mobile.api.TrailsApi
import it.unical.geosentinel_mobile.api.model.GeoJsonLineString
import it.unical.geosentinel_mobile.api.model.GeoPointDto
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.TrailDetailResponse
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.toPositions
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.Conditional
import it.unical.geosentinel_mobile.core.network.apiCall
import it.unical.geosentinel_mobile.core.network.conditionalGet
import it.unical.geosentinel_mobile.trail.data.local.CatalogDao
import it.unical.geosentinel_mobile.trail.data.local.CatalogEtagEntity
import it.unical.geosentinel_mobile.trail.data.local.PoiEntity
import it.unical.geosentinel_mobile.trail.data.local.TrailDetailEntity
import it.unical.geosentinel_mobile.trail.data.local.TrailSummaryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/** Un dato della cache con l'ultima volta che il server l'ha confermato ("aggiornato alle"). */
data class Cached<T>(val value: T, val updatedAt: Instant)

/**
 * Sentieri e POI: GET pubbliche, l'ospite le vede. Il client è quello condiviso (con il bearer se c'è una sessione:
 * il rate limit conta per utente).
 *
 * Cache (M3.3): la UI legge sempre da Room (`observe*`), la rete aggiorna (`refresh*`). Dettaglio e POI di un
 * sentiero rimandano l'ETag in `If-None-Match`: un 304 aggiorna solo la data. La bbox no: l'area cambia a ogni
 * movimento della mappa, la stessa URL non torna quasi mai e l'ETag non servirebbe.
 */
class TrailRepository(
    httpClient: () -> HttpClient,
    apiBaseUrl: String,
    private val dao: CatalogDao,
    private val now: () -> Instant = { Clock.System.now() },
    // GeoJSON da codificare e decodificare: fuori dal main thread (§4). Nei test il dispatcher del test (tempo virtuale)
    private val converting: CoroutineDispatcher = Dispatchers.Default,
) {

    private val client by lazy(httpClient)
    private val baseUrl = apiBaseUrl.trimEnd('/')
    private val trailsApi by lazy { TrailsApi(apiBaseUrl, client) }
    private val poisApi by lazy { PointsOfInterestApi(apiBaseUrl, client) }

    // ---- mappa ----

    // Le conversioni (GeoJSON da decodificare) girano su Default, non nel contesto di chi raccoglie (Main, §4)
    fun observeInBounds(bounds: GeoBounds): Flow<List<TrailSummaryResponse>> =
        dao.observeSummariesInBox(bounds.minLon, bounds.minLat, bounds.maxLon, bounds.maxLat)
            .map { rows -> rows.mapNotNull { it.toResponse() } }
            .flowOn(converting)

    /**
     * I sentieri che attraversano [area] (fino a [MAX_BBOX_RESULTS], con il percorso semplificato, ADR-0020) dal server
     * alla cache. Restituisce quanti ne ha dati il server: con [MAX_BBOX_RESULTS] l'area non è completa.
     */
    // bbox sempre esplicito: il client generato (openapi-generator 7.14) gli dà per errore il default "createdAt,ASC"
    suspend fun refreshInBounds(area: GeoBounds): Result<Int> = guarded {
        apiCall { trailsApi.findTrailsInBoundingBox(bbox = area.toQuery()) }.map { trails ->
            val at = now()
            val rows = withContext(converting) { trails.mapNotNull { it.toEntity(at) } }
            // Con la risposta completa si tolgono dalla cache anche quelli che il server non ha più (cancellati o
            // spostati); con 200 risultati no, la risposta è solo una parte
            if (trails.size < MAX_BBOX_RESULTS) {
                dao.replaceSummariesInBox(area.minLon, area.minLat, area.maxLon, area.maxLat, rows)
            } else {
                dao.upsertSummaries(rows)
            }
            trails.size
        }
    }

    // "Sentieri vicini": solo rete, è una ricerca del momento. Raggio in metri, il server lo accetta fino a 50 km.
    suspend fun nearby(at: GeoPoint, radiusMeters: Double = NEARBY_RADIUS_METERS): Result<List<TrailSummaryResponse>> =
        apiCall {
            trailsApi.findTrailsNearby(lon = at.lon, lat = at.lat, radius = radiusMeters.coerceIn(1.0, MAX_RADIUS_METERS), limit = MAX_NEARBY_RESULTS)
        }

    fun observePoisInBounds(bounds: GeoBounds): Flow<List<PoiResponse>> =
        dao.observePoisInBox(bounds.minLon, bounds.minLat, bounds.maxLon, bounds.maxLat)
            .map { rows -> rows.map { it.toResponse() } }
            .flowOn(converting)

    /**
     * I POI di [area] nella cache: il server li dà per raggio, con il cerchio che contiene tutto il riquadro (fino a
     * 50 km). Con una risposta completa (meno di 50, raggio non tagliato) quelli nel riquadro che il server non ha più
     * spariscono anche qui. Restituisce true se la risposta era completa.
     */
    suspend fun refreshPoisIn(area: GeoBounds): Result<Boolean> = guarded {
        val radius = area.radiusMeters
        apiCall {
            poisApi.findPoisNearby(lon = area.center.lon, lat = area.center.lat, radius = radius.coerceIn(1.0, MAX_RADIUS_METERS), limit = MAX_NEARBY_RESULTS)
        }.map { pois ->
            val at = now()
            val rows = pois.map { it.toEntity(at) }
            val complete = pois.size < MAX_NEARBY_RESULTS && radius <= MAX_RADIUS_METERS
            if (complete) dao.replacePoisInBox(area.minLon, area.minLat, area.maxLon, area.maxLat, rows) else dao.upsertPois(rows)
            complete
        }
    }

    // ---- dettaglio ----

    fun observeDetail(id: String): Flow<Cached<TrailDetailResponse>?> =
        dao.observeTrailDetail(id).map { row -> row?.toResponse()?.let { Cached(it, row.updatedAt) } }.flowOn(converting)

    /**
     * Il dettaglio restituito dal server dopo un cambio di stato (M8.1): si salva come una risposta nuova, e la mappa
     * prende subito lo stato nuovo. L'ETag salvato è quello vecchio: al prossimo giro il server risponde 200, non 304.
     */
    suspend fun saveDetail(detail: TrailDetailResponse): Result<Unit> = guarded {
        dao.upsertTrailDetail(withContext(converting) { detail.toEntity(now()) })
        dao.updateSummaryStatus(detail.id, detail.status.value)
        Result.success(Unit)
    }

    /** Un POI appena creato (M8.1): compare subito sulla mappa e nel dettaglio del suo sentiero. */
    suspend fun savePoi(poi: PoiResponse): Result<Unit> = guarded {
        dao.upsertPois(listOf(poi.toEntity(now())))
        Result.success(Unit)
    }

    suspend fun refreshDetail(id: String): Result<Unit> = guarded {
        val key = "trail/$id"
        // Senza la copia in cache l'ETag non si manda: un 304 lascerebbe il dettaglio vuoto. Riga ed ETag si scrivono
        // in due passi: se il processo muore in mezzo, al massimo il giro dopo arriva un 200 invece di un 304.
        val etag = if (dao.getTrailDetail(id) != null) dao.etagOf(key) else null
        val result = client.conditionalGet<TrailDetailResponse>("$baseUrl/api/v1/trails/${id.encodeURLPathPart()}", etag)
        // 404: il sentiero non c'è più sul server, sparisce anche la copia salvata (la schermata mostra l'errore)
        if ((result.exceptionOrNull() as? ApiException)?.error is ApiError.NotFound) {
            dao.deleteTrailDetail(id)
            dao.deleteEtag(key)
        }
        result.map { response ->
            when (response) {
                is Conditional.Modified -> {
                    dao.upsertTrailDetail(withContext(converting) { response.body.toEntity(now()) })
                    response.etag?.let { dao.saveEtag(CatalogEtagEntity(key, it)) }
                    Unit
                }
                Conditional.NotModified -> dao.updateTrailDetailTimestamp(id, now())
            }
        }
    }

    fun observePoisOf(trailId: String): Flow<List<PoiResponse>> =
        dao.observePoiByTrail(trailId).map { rows -> rows.map { it.toResponse() } }.flowOn(converting)

    suspend fun refreshPoisOf(trailId: String): Result<Unit> = guarded {
        val key = "pois/$trailId"
        client.conditionalGet<List<PoiResponse>>("$baseUrl/api/v1/pois/trail/${trailId.encodeURLPathPart()}", dao.etagOf(key)).map { result ->
            if (result is Conditional.Modified) {
                val at = now()
                dao.replacePoisOfTrail(trailId, result.body.map { it.toEntity(at) })
                result.etag?.let { dao.saveEtag(CatalogEtagEntity(key, it)) }
            }
        }
    }

    /**
     * La cache è un aiuto, non deve far cadere la schermata: un errore del database (disco pieno, file rovinato) o una
     * risposta fuori contratto diventano un fallimento come gli altri. La cancellazione si propaga.
     */
    private suspend fun <T> guarded(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---- conversioni tra le risposte dell'API e le righe della cache ----

    private fun TrailSummaryResponse.toEntity(at: Instant): TrailSummaryEntity? {
        val box = GeoBounds.around(path.toPositions()) ?: return null
        return TrailSummaryEntity(
            id = id,
            name = name,
            difficulty = difficulty.value,
            lengthMeters = lengthM,
            status = status.value,
            simplifiedGeoJson = json.encodeToString(GeoJsonLineString.serializer(), path),
            minLon = box.minLon,
            minLat = box.minLat,
            maxLon = box.maxLon,
            maxLat = box.maxLat,
            updatedAt = at,
        )
    }

    private fun TrailSummaryEntity.toResponse(): TrailSummaryResponse? {
        val path = decodePath(simplifiedGeoJson) ?: return null
        return TrailSummaryResponse(
            difficulty = enumOf(difficulty, TrailSummaryResponse.Difficulty.unknown_default_open_api),
            id = id,
            lengthM = lengthMeters,
            name = name,
            path = path,
            status = enumOf(status, TrailSummaryResponse.Status.unknown_default_open_api),
        )
    }

    private fun TrailDetailResponse.toEntity(at: Instant) = TrailDetailEntity(
        id = id,
        name = name,
        difficulty = difficulty.value,
        lengthMeters = lengthM,
        status = status.value,
        fullGeoJson = json.encodeToString(GeoJsonLineString.serializer(), path),
        description = description,
        updatedAt = at,
    )

    private fun TrailDetailEntity.toResponse(): TrailDetailResponse? {
        val path = decodePath(fullGeoJson) ?: return null
        return TrailDetailResponse(
            difficulty = enumOf(difficulty, TrailDetailResponse.Difficulty.unknown_default_open_api),
            id = id,
            lengthM = lengthMeters,
            name = name,
            path = path,
            status = enumOf(status, TrailDetailResponse.Status.unknown_default_open_api),
            description = description,
        )
    }

    private fun PoiResponse.toEntity(at: Instant) =
        PoiEntity(id = id, trailId = trailId, name = name, type = type.value, lat = location.lat, lon = location.lon, updatedAt = at)

    private fun PoiEntity.toResponse() = PoiResponse(
        id = id,
        location = GeoPointDto(lat = lat, lon = lon),
        name = name,
        type = enumOf(type, PoiResponse.Type.unknown_default_open_api),
        trailId = trailId,
    )

    // Una riga illeggibile (es. scritta da una versione futura) si salta: la rete la riscrive
    private fun decodePath(text: String): GeoJsonLineString? =
        runCatching { json.decodeFromString(GeoJsonLineString.serializer(), text) }.getOrNull()

    companion object {
        // Limiti del backend (TrailService, TrailController, PoiController): oltre, il server taglia o risponde 400
        const val MAX_BBOX_RESULTS = 200
        const val MAX_NEARBY_RESULTS = 50
        const val MAX_RADIUS_METERS = 50_000.0
        const val NEARBY_RADIUS_METERS = 10_000.0

        private val json = Json { ignoreUnknownKeys = true }
    }
}

// Negli enum generati il nome del caso coincide con il valore; uno sconosciuto (versione futura) va nel caso di riserva
private inline fun <reified E : Enum<E>> enumOf(name: String, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback
