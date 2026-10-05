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

// Il primo sentiero del seed del backend (R__local_seed.sql), con le coordinate vere della Sila in [lon, lat]
const val SEED_TRAIL_ID = "10000000-0000-0000-0000-000000000001"
const val SEED_TRAIL_PATH = """{"type":"LineString","coordinates":[[16.4852,39.2934],[16.4910,39.2980],[16.5020,39.3050],[16.5150,39.3100]]}"""

fun trailJson(id: String = SEED_TRAIL_ID, status: String = "OPEN", difficulty: String = "MODERATE", name: String = "Sentiero delle Vette Silane") =
    """{"id":"$id","name":"$name","difficulty":"$difficulty","status":"$status","lengthM":2830.4,"path":$SEED_TRAIL_PATH}"""

fun trailDetailJson(id: String = SEED_TRAIL_ID, status: String = "CLOSED") =
    """{"id":"$id","name":"Sentiero delle Vette Silane","description":"Traversata panoramica.","difficulty":"MODERATE","status":"$status","lengthM":2830.4,"path":$SEED_TRAIL_PATH}"""

fun mediaJson(id: String, status: String) =
    """{"id":"$id","contentType":"image/jpeg","createdAt":"2026-10-01T10:00:00Z","originalFilename":"$id.jpg","sizeBytes":1000,"status":"$status","entityType":"TRAIL","entityId":"$SEED_TRAIL_ID"}"""

/**
 * Le rotte del catalogo sul backend finto di SessionTestSupport (stesso client, con Auth e AuthScope): sentieri e
 * POI pubblici, pericoli e media solo con un bearer valido, come sul server.
 */
class FakeCatalog(private val backend: FakeBackend) {
    var trails: List<String> = listOf(trailJson())
    var detailStatus = HttpStatusCode.OK
    var pois = listOf("""{"id":"p1","trailId":"$SEED_TRAIL_ID","name":"Fonte del Faggio","type":"WATER_SOURCE","location":{"lat":39.298,"lon":16.491}}""")
    var hazards = listOf("""{"id":"h1","trailId":"$SEED_TRAIL_ID","type":"FALLEN_TREE","severity":"HIGH","status":"VERIFIED","location":{"lat":39.3,"lon":16.5},"createdAt":"2026-10-04T09:00:00Z"}""")
    var media = listOf(mediaJson("m1", "READY"), mediaJson("m2", "QUARANTINED"), mediaJson("m3", "READY"))
    var offline = false
    // una richiesta della bbox ancora in volo (per i test di annullamento)
    var bboxDelayMillis = 0L
    var downloadUrlFails = false
    // L'ETag cambia quando cambia il body, come con ShallowEtagHeaderFilter: si simula con un numero di versione
    var detailVersion = 1
    var poisVersion = 1
    var detailStatusValue = "CLOSED"
    // tipo e query string di ogni richiesta ("bbox", "nearby", "pois-nearby", "pois", "detail", "hazards", "media", "download-url")
    val calls = mutableListOf<Pair<String, String>>()
    private var urlCounter = 0

    fun count(kind: String) = calls.count { it.first == kind }
    fun queries(kind: String) = calls.filter { it.first == kind }.map { it.second }

    fun install() {
        backend.extraRoutes = { request, owner -> handle(request, owner) }
    }

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData, owner: String?): HttpResponseData? {
        if (request.method != HttpMethod.Get) return null
        val path = request.url.encodedPath
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

    private fun list(items: List<String>) = "[${items.joinToString(",")}]"

    // If-None-Match uguale all'ETag attuale: 304 senza body; altrimenti 200 con l'ETag
    private fun MockRequestHandleScope.conditional(request: HttpRequestData, etag: String, body: () -> String): HttpResponseData {
        if (request.headers[HttpHeaders.IfNoneMatch] == etag) {
            calls += "304" to request.url.encodedPath
            return respond("", HttpStatusCode.NotModified, headersOf(HttpHeaders.ETag, etag))
        }
        return respond(body(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(etag)))
    }

    private fun MockRequestHandleScope.ok(json: String) = respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
}
