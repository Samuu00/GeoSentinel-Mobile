// Accesso a sentieri e punti di interesse: unisce le API del backend e la cache Room. La UI osserva sempre la cache,
// mentre le funzioni refresh aggiornano la cache dalla rete; alcune ricerche del momento (sentieri vicini, percorso
// da A a B) passano invece solo dalla rete.
package it.unical.geosentinel_mobile.trail.data

import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart
import it.unical.geosentinel_mobile.api.PointsOfInterestApi
import it.unical.geosentinel_mobile.api.TrailsApi
import it.unical.geosentinel_mobile.api.model.GeoJsonLineString
import it.unical.geosentinel_mobile.api.model.GeoPointDto
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.RouteResponse
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

/** Un dato letto dalla cache insieme all'ultima volta che il server lo ha confermato, mostrato come "aggiornato alle". */
data class Cached<T>(val value: T, val updatedAt: Instant)

/**
 * Repository di sentieri e POI. Le rotte sono GET pubbliche, visibili anche all'ospite. Si usa il client HTTP
 * condiviso, che aggiunge il token se c'è una sessione: il rate limit del server viene così conteggiato per utente.
 *
 * Strategia di cache: la UI legge sempre da Room (funzioni `observe*`) e la rete aggiorna la cache (funzioni
 * `refresh*`). Le richieste del dettaglio e dei POI di un sentiero rimandano l'ETag salvato in `If-None-Match`: con un
 * 304 si aggiorna solo l'orario. Per la ricerca per riquadro l'ETag non si usa: l'area cambia a ogni movimento della
 * mappa, la stessa URL non si ripete quasi mai e l'ETag non servirebbe.
 *
 * @param now orologio iniettabile per i test.
 * @param converting dispatcher su cui si codifica e decodifica il GeoJSON, fuori dal main thread; nei test si passa
 * quello del test, che usa il tempo virtuale.
 */
class TrailRepository(
    httpClient: () -> HttpClient,
    apiBaseUrl: String,
    private val dao: CatalogDao,
    private val now: () -> Instant = { Clock.System.now() },
    private val converting: CoroutineDispatcher = Dispatchers.Default,
) {

    private val client by lazy(httpClient)

    /** URL base senza la barra finale, per costruire a mano gli URL delle richieste condizionali. */
    private val baseUrl = apiBaseUrl.trimEnd('/')
    private val trailsApi by lazy { TrailsApi(apiBaseUrl, client) }
    private val poisApi by lazy { PointsOfInterestApi(apiBaseUrl, client) }

    // Sentieri e POI per la mappa.

    /**
     * Osserva i sentieri della cache il cui riquadro tocca [bounds]. La decodifica del GeoJSON gira sul dispatcher
     * [converting] grazie a flowOn, non nel contesto di chi raccoglie il flow (il main thread).
     */
    fun observeInBounds(bounds: GeoBounds): Flow<List<TrailSummaryResponse>> =
        dao.observeSummariesInBox(bounds.minLon, bounds.minLat, bounds.maxLon, bounds.maxLat)
            .map { rows -> rows.mapNotNull { it.toResponse() } }
            .flowOn(converting)

    /**
     * Scarica dal server i sentieri che attraversano [area] (al massimo [MAX_BBOX_RESULTS], con il percorso
     * semplificato) e li salva in cache. Restituisce quanti sentieri ha dato il server: se sono [MAX_BBOX_RESULTS] la
     * risposta potrebbe essere incompleta.
     *
     * Il parametro bbox va sempre passato in modo esplicito, perché il client generato gli assegna per errore il valore
     * predefinito "createdAt,ASC".
     */
    suspend fun refreshInBounds(area: GeoBounds): Result<Int> = guarded {
        apiCall { trailsApi.findTrailsInBoundingBox(bbox = area.toQuery()) }.map { trails ->
            val at = now()
            val rows = withContext(converting) { trails.mapNotNull { it.toEntity(at) } }
            // Con una risposta completa si tolgono dalla cache anche i sentieri che il server non ha più (cancellati o
            // spostati); con MAX_BBOX_RESULTS risultati invece no, perché la risposta è solo una parte.
            if (trails.size < MAX_BBOX_RESULTS) {
                dao.replaceSummariesInBox(area.minLon, area.minLat, area.maxLon, area.maxLat, rows)
            } else {
                dao.upsertSummaries(rows)
            }
            trails.size
        }
    }

    /**
     * Riduce la cache ad [area], cioè ai 50 km attorno all'utente: sentieri e POI fuori vengono cancellati. Un sentiero
     * che tocca l'area resta per intero; i dettagli dei sentieri aperti restano (si rileggono quando si aprono).
     */
    suspend fun pruneOutside(area: GeoBounds): Result<Unit> = guarded {
        dao.deleteOutsideBox(area.minLon, area.minLat, area.maxLon, area.maxLat)
        Result.success(Unit)
    }

    /**
     * Cerca i sentieri vicini a [at] per la lista "Sentieri vicini". Passa solo dalla rete perché è una ricerca del
     * momento. [radiusMeters] è in metri e viene limitato ai 50 km accettati dal server.
     */
    suspend fun nearby(at: GeoPoint, radiusMeters: Double = NEARBY_RADIUS_METERS): Result<List<TrailSummaryResponse>> =
        apiCall {
            trailsApi.findTrailsNearby(lon = at.lon, lat = at.lat, radius = radiusMeters.coerceIn(1.0, MAX_RADIUS_METERS), limit = MAX_NEARBY_RESULTS)
        }

    /**
     * Calcola un percorso a piedi da [from] a [to]. Il backend usa GraphHopper sui sentieri e sulle strade di
     * OpenStreetMap ed evita i sentieri chiusi. Passa solo dalla rete, come "Sentieri vicini". Il server risponde 422
     * `no-route` se un punto è fuori dall'area coperta o non esiste un percorso, e 503 `routing-unavailable` se il
     * motore non è disponibile: in quel caso non si ritenta in automatico, decide l'utente.
     */
    suspend fun route(from: GeoPoint, to: GeoPoint): Result<RouteResponse> =
        apiCall { trailsApi.findRoute(fromLon = from.lon, fromLat = from.lat, toLon = to.lon, toLat = to.lat) }

    /** Osserva i POI della cache che cadono in [bounds]. */
    fun observePoisInBounds(bounds: GeoBounds): Flow<List<PoiResponse>> =
        dao.observePoisInBox(bounds.minLon, bounds.minLat, bounds.maxLon, bounds.maxLat)
            .map { rows -> rows.map { it.toResponse() } }
            .flowOn(converting)

    /**
     * Scarica i POI di [area] e li salva in cache. Il server li cerca per raggio, quindi si chiede il cerchio che
     * contiene tutto il riquadro (al massimo 50 km). Con una risposta completa (meno di [MAX_POI_RESULTS] risultati e
     * raggio non ridotto) i POI del riquadro che il server non ha più spariscono anche dalla cache. Restituisce true se
     * la risposta era completa.
     */
    suspend fun refreshPoisIn(area: GeoBounds): Result<Boolean> = guarded {
        val radius = area.radiusMeters
        apiCall {
            poisApi.findPoisNearby(lon = area.center.lon, lat = area.center.lat, radius = radius.coerceIn(1.0, MAX_RADIUS_METERS), limit = MAX_POI_RESULTS)
        }.map { pois ->
            val at = now()
            val rows = pois.map { it.toEntity(at) }
            val complete = pois.size < MAX_POI_RESULTS && radius <= MAX_RADIUS_METERS
            if (complete) dao.replacePoisInBox(area.minLon, area.minLat, area.maxLon, area.maxLat, rows) else dao.upsertPois(rows)
            complete
        }
    }

    // Dettaglio di un sentiero.

    /** Osserva il dettaglio del sentiero [id] con l'orario dell'ultima conferma; null se non è in cache. */
    fun observeDetail(id: String): Flow<Cached<TrailDetailResponse>?> =
        dao.observeTrailDetail(id).map { row -> row?.toResponse()?.let { Cached(it, row.updatedAt) } }.flowOn(converting)

    /**
     * Salva il dettaglio restituito dal server dopo un cambio di stato fatto dal gestore, come se fosse una risposta
     * nuova, e aggiorna lo stato anche nel riassunto così la mappa lo mostra subito. L'ETag salvato resta quello
     * vecchio: alla prossima richiesta il server risponde 200 e non 304, quindi la cache si riallinea.
     */
    suspend fun saveDetail(detail: TrailDetailResponse): Result<Unit> = guarded {
        dao.upsertTrailDetail(withContext(converting) { detail.toEntity(now()) })
        dao.updateSummaryStatus(detail.id, detail.status.value)
        Result.success(Unit)
    }

    /** Salva un POI appena creato, così compare subito sulla mappa e nel dettaglio del suo sentiero. */
    suspend fun savePoi(poi: PoiResponse): Result<Unit> = guarded {
        dao.upsertPois(listOf(poi.toEntity(now())))
        Result.success(Unit)
    }

    /**
     * Aggiorna dalla rete il dettaglio del sentiero [id] con una GET condizionale. Con 200 salva il nuovo dettaglio e
     * l'ETag, con 304 aggiorna solo l'orario, con 404 cancella la copia salvata.
     */
    suspend fun refreshDetail(id: String): Result<Unit> = guarded {
        val key = "trail/$id"
        // Se il dettaglio non è in cache l'ETag non si invia: un 304 lascerebbe la schermata senza dati. Riga ed ETag
        // si scrivono in due passi separati: se il processo si chiude in mezzo, al massimo la volta dopo arriva un 200
        // invece di un 304.
        val etag = if (dao.getTrailDetail(id) != null) dao.etagOf(key) else null
        val result = client.conditionalGet<TrailDetailResponse>("$baseUrl/api/v1/trails/${id.encodeURLPathPart()}", etag)
        // Con un 404 il sentiero non esiste più sul server: sparisce anche la copia salvata e la schermata mostra
        // l'errore.
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

    /** Osserva i POI del sentiero [trailId] dalla cache. */
    fun observePoisOf(trailId: String): Flow<List<PoiResponse>> =
        dao.observePoiByTrail(trailId).map { rows -> rows.map { it.toResponse() } }.flowOn(converting)

    /**
     * Aggiorna dalla rete i POI del sentiero [trailId] con una GET condizionale: con 200 sostituisce quelli in cache e
     * salva l'ETag, con 304 non cambia nulla.
     */
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
     * Esegue [block] trasformando qualunque eccezione in un Result fallito. La cache è un aiuto e non deve far cadere la
     * schermata: un errore del database (disco pieno, file corrotto) o una risposta fuori contratto diventano un
     * fallimento come gli altri. La cancellazione della coroutine invece si propaga.
     */
    private suspend fun <T> guarded(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Conversioni tra le risposte dell'API e le righe della cache.

    /** Converte un riassunto in riga della cache, calcolando il riquadro del percorso; null se il percorso è vuoto. */
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

    /** Converte una riga della cache in riassunto; null se il GeoJSON salvato non si legge. */
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

    /** Converte un dettaglio in riga della cache. */
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

    /** Converte una riga della cache in dettaglio; null se il GeoJSON salvato non si legge. */
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

    /** Converte un POI in riga della cache. */
    private fun PoiResponse.toEntity(at: Instant) =
        PoiEntity(id = id, trailId = trailId, name = name, type = type.value, lat = location.lat, lon = location.lon, updatedAt = at)

    /** Converte una riga della cache in POI. */
    private fun PoiEntity.toResponse() = PoiResponse(
        id = id,
        location = GeoPointDto(lat = lat, lon = lon),
        name = name,
        type = enumOf(type, PoiResponse.Type.unknown_default_open_api),
        trailId = trailId,
    )

    /**
     * Decodifica il GeoJSON di un percorso salvato. Una riga illeggibile (per esempio scritta da una versione futura
     * dell'app) restituisce null e viene saltata: la rete la riscriverà.
     */
    private fun decodePath(text: String): GeoJsonLineString? =
        runCatching { json.decodeFromString(GeoJsonLineString.serializer(), text) }.getOrNull()

    /**
     * Limiti allineati a quelli del backend (TrailService, TrailController, PoiController): oltre questi valori il
     * server riduce il risultato o risponde 400.
     */
    companion object {
        /** Sentieri massimi per un riquadro: bastano per tutti i sentieri della Calabria (circa 350) in una richiesta. */
        const val MAX_BBOX_RESULTS = 500

        /** POI massimi per richiesta; sulla mappa quelli che si sovrappongono vengono nascosti da MapLibre. */
        const val MAX_POI_RESULTS = 300

        /** Risultati massimi della ricerca "Sentieri vicini". */
        const val MAX_NEARBY_RESULTS = 50

        /** Raggio massimo accettato dal server per le ricerche per vicinanza, in metri. */
        const val MAX_RADIUS_METERS = 50_000.0

        /** Raggio predefinito di "Sentieri vicini", in metri. */
        const val NEARBY_RADIUS_METERS = 10_000.0

        /** Parser JSON che ignora i campi sconosciuti, così le righe scritte da versioni diverse restano leggibili. */
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Converte una stringa salvata nel caso dell'enum con lo stesso nome (negli enum generati il nome coincide con il
 * valore). Un valore sconosciuto, per esempio arrivato da una versione futura del server, diventa [fallback].
 */
private inline fun <reified E : Enum<E>> enumOf(name: String, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback
