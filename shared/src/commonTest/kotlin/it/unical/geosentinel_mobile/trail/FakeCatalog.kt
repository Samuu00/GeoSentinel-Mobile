// Rotte del catalogo (sentieri, POI, pericoli, media e percorso A-B) simulate sul backend finto dei test comuni,
// con i dati JSON di esempio. Le usano i test del repository dei sentieri e dei ViewModel della mappa.
package it.unical.geosentinel_mobile.trail

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.core.session.FakeBackend
import kotlinx.coroutines.delay
import kotlinx.io.IOException

/** Id del primo sentiero dei dati di prova del backend. */
const val SEED_TRAIL_ID = "10000000-0000-0000-0000-000000000001"
/** Percorso del sentiero di prova in GeoJSON, con coordinate reali della Sila nell'ordine [lon, lat]. */
const val SEED_TRAIL_PATH = """{"type":"LineString","coordinates":[[16.4852,39.2934],[16.4910,39.2980],[16.5020,39.3050],[16.5150,39.3100]]}"""

/** JSON del riassunto di un sentiero come lo restituisce l'API, con i campi principali personalizzabili. */
fun trailJson(id: String = SEED_TRAIL_ID, status: String = "OPEN", difficulty: String = "MODERATE", name: String = "Sentiero delle Vette Silane") =
    """{"id":"$id","name":"$name","difficulty":"$difficulty","status":"$status","lengthM":2830.4,"path":$SEED_TRAIL_PATH}"""

/** JSON del dettaglio di un sentiero, con descrizione e stato personalizzabile. */
fun trailDetailJson(id: String = SEED_TRAIL_ID, status: String = "CLOSED") =
    """{"id":"$id","name":"Sentiero delle Vette Silane","description":"Traversata panoramica.","difficulty":"MODERATE","status":"$status","lengthM":2830.4,"path":$SEED_TRAIL_PATH}"""

/** JSON di un media (foto) allegato al sentiero di prova, con lo stato indicato (per esempio READY o QUARANTINED). */
fun mediaJson(id: String, status: String) =
    """{"id":"$id","contentType":"image/jpeg","createdAt":"2026-10-01T10:00:00Z","originalFilename":"$id.jpg","sizeBytes":1000,"status":"$status","entityType":"TRAIL","entityId":"$SEED_TRAIL_ID"}"""

/**
 * Aggiunge al backend finto di SessionTestSupport le rotte del catalogo, usando lo stesso client con autenticazione.
 * Come sul server vero, sentieri e POI sono pubblici, mentre pericoli e media richiedono un bearer valido. Le
 * proprietà pubbliche permettono ai test di cambiare le risposte e di contare le richieste ricevute.
 */
class FakeCatalog(private val backend: FakeBackend) {
    /** Sentieri restituiti dalle ricerche per area e vicine, come stringhe JSON. */
    var trails: List<String> = listOf(trailJson())
    /** Stato HTTP della risposta al dettaglio di un sentiero; se diverso da 200 si risponde con un errore. */
    var detailStatus = HttpStatusCode.OK
    /** POI restituiti, come stringhe JSON. */
    var pois = listOf("""{"id":"p1","trailId":"$SEED_TRAIL_ID","name":"Fonte del Faggio","type":"WATER_SOURCE","location":{"lat":39.298,"lon":16.491}}""")
    /** Pericoli restituiti per un sentiero, come stringhe JSON. */
    var hazards = listOf("""{"id":"h1","trailId":"$SEED_TRAIL_ID","type":"FALLEN_TREE","severity":"HIGH","status":"VERIFIED","location":{"lat":39.3,"lon":16.5},"createdAt":"2026-10-04T09:00:00Z"}""")
    /** Media allegati al sentiero; uno è in quarantena e non deve essere mostrato. */
    var media = listOf(mediaJson("m1", "READY"), mediaJson("m2", "QUARANTINED"), mediaJson("m3", "READY"))
    /** Se vero, ogni richiesta fallisce con un errore di rete. */
    var offline = false
    /** Se falso, la richiesta del percorso A-B risponde 422 con lo slug "no-route". */
    var routeFound = true
    /** Ritardo della risposta alla ricerca per area, per avere una richiesta ancora in corso nei test di annullamento. */
    var bboxDelayMillis = 0L
    /** Se vero, la richiesta dell'URL di download di una foto fallisce con 503. */
    var downloadUrlFails = false
    /**
     * Versione del dettaglio, usata per calcolare l'ETag. Sul server l'ETag cambia quando cambia il body della risposta;
     * qui lo si simula cambiando il numero di versione.
     */
    var detailVersion = 1
    /** Versione della lista dei POI, usata per calcolare l'ETag come per il dettaglio. */
    var poisVersion = 1
    /** Stato del sentiero scritto nel JSON del dettaglio. */
    var detailStatusValue = "CLOSED"
    /**
     * Registro delle richieste: per ognuna il tipo ("bbox", "nearby", "pois-nearby", "pois", "detail", "hazards",
     * "media", "download-url", "route" o "304") e la query string o il percorso.
     */
    val calls = mutableListOf<Pair<String, String>>()
    /** Contatore usato per generare URL di download sempre diversi, come le firme temporanee dello storage. */
    private var urlCounter = 0

    /** Numero di richieste ricevute del tipo indicato. */
    fun count(kind: String) = calls.count { it.first == kind }
    /** Query string delle richieste ricevute del tipo indicato. */
    fun queries(kind: String) = calls.filter { it.first == kind }.map { it.second }

    /** Registra le rotte del catalogo nel backend finto. */
    fun install() {
        backend.extraRoutes = { request, owner -> handle(request, owner) }
    }

    /**
     * Risponde alle richieste GET del catalogo in base al percorso; restituisce null per le rotte che non gestisce,
     * così il backend finto passa alle sue. [owner] è l'utente del bearer, null se la richiesta è anonima.
     */
    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData, owner: String?): HttpResponseData? {
        if (request.method != HttpMethod.Get) return null
        val path = request.url.encodedPath
        // Registra la richiesta, oppure simula l'assenza di rete se offline è attivo.
        fun record(kind: String) {
            if (offline) throw IOException("rete assente")
            calls += kind to request.url.encodedQuery
        }
        fun unauthorized() = with(backend) { problem(HttpStatusCode.Unauthorized, "unauthorized") }
        return when {
            path == "/api/v1/trails" -> record("bbox").let {
                if (bboxDelayMillis > 0) delay(bboxDelayMillis)
                ok(list(trails))
            }
            path == "/api/v1/trails/nearby" -> record("nearby").let { ok(list(trails)) }
            path == "/api/v1/trails/route" -> record("route").let {
                if (routeFound) {
                    ok("""{"path":$SEED_TRAIL_PATH,"lengthM":4210.0,"trailIds":["$SEED_TRAIL_ID","t2"]}""")
                } else {
                    with(backend) { problem(HttpStatusCode.UnprocessableEntity, "no-route") }
                }
            }
            path == "/api/v1/pois/nearby" -> record("pois-nearby").let { ok(list(pois)) }
            path.startsWith("/api/v1/pois/trail/") -> record("pois").let { conditional(request, "W/\"p$poisVersion\"") { list(pois) } }
            path.startsWith("/api/v1/trails/") -> record("detail").let {
                if (detailStatus == HttpStatusCode.OK) {
                    conditional(request, "W/\"d$detailVersion\"") { trailDetailJson(status = detailStatusValue) }
                } else {
                    with(backend) { problem(detailStatus, "not-found") }
                }
            }
            path.startsWith("/api/v1/hazards/trail/") -> record("hazards").let { if (owner == null) unauthorized() else ok(list(hazards)) }
            path == "/api/v1/media/attached" -> record("media").let { if (owner == null) unauthorized() else ok(list(media)) }
            path.startsWith("/api/v1/media/") && path.endsWith("/download-url") -> record("download-url").let {
                val id = path.removePrefix("/api/v1/media/").removeSuffix("/download-url")
                when {
                    owner == null -> unauthorized()
                    downloadUrlFails -> with(backend) { problem(HttpStatusCode.ServiceUnavailable, "internal-error") }
                    else -> ok("""{"mediaId":"$id","downloadUrl":"http://storage.test/$id?firma=${++urlCounter}"}""")
                }
            }
            else -> null
        }
    }

    /** Unisce una lista di oggetti JSON in un array JSON. */
    private fun list(items: List<String>) = "[${items.joinToString(",")}]"

    /**
     * Simula una GET condizionale: se If-None-Match coincide con l'ETag attuale risponde 304 senza body, altrimenti
     * 200 con il body e l'ETag.
     */
    private fun MockRequestHandleScope.conditional(request: HttpRequestData, etag: String, body: () -> String): HttpResponseData {
        if (request.headers[HttpHeaders.IfNoneMatch] == etag) {
            calls += "304" to request.url.encodedPath
            return respond("", HttpStatusCode.NotModified, headersOf(HttpHeaders.ETag, etag))
        }
        return respond(body(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(etag)))
    }

    /** Risposta 200 con il JSON indicato. */
    private fun MockRequestHandleScope.ok(json: String) = respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
}
