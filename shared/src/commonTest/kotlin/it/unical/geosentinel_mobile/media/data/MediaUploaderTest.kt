// Test di MediaUploader, che carica le foto in tre passi: richiesta di upload all'API, invio del file direttamente
// allo storage (compatibile S3) con un form firmato, conferma all'API. Il file contiene anche FakeMedia e le funzioni
// di supporto riusate dai test di segnalazione dei pericoli e del profilo.
package it.unical.geosentinel_mobile.media.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.model.InitUploadRequest
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.HttpClientFactory
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Indirizzo dello storage finto a cui viene inviato il file. */
const val STORAGE_URL = "http://storage.test/geosentinel-media"
/** Istante fisso usato come "adesso" dall'orologio dei test. */
val TEST_NOW: Instant = Instant.parse("2026-10-05T10:00:00Z")

/** Orologio che restituisce sempre l'istante [now], per rendere deterministici i controlli sulle scadenze. */
fun fixedClock(now: Instant) = object : Clock {
    override fun now() = now
}

/**
 * API dei media e storage finti, che registrano tutto quello che ricevono. Le rotte dell'API si aggiungono a quelle
 * extra di [FakeBackend], mantenendo quelle già installate; lo storage è un motore HTTP separato, senza bearer.
 */
class FakeMedia(backend: FakeBackend) {
    /** Corpi delle richieste POST /media/upload-requests ricevute. */
    val uploadRequests = mutableListOf<String>()
    /** Richieste arrivate allo storage. */
    val storageRequests = mutableListOf<HttpRequestData>()
    /** Corpi grezzi delle richieste arrivate allo storage. */
    val storageBodies = mutableListOf<ByteArray>()
    /** Numero di conferme ricevute dall'API. */
    var confirms = 0
    /** Scadenza del form firmato restituita dalla richiesta di upload. */
    var expiresAt: Instant = Instant.parse("2026-10-05T10:10:00Z")
    /** Stato HTTP con cui lo storage risponde, in funzione del numero del tentativo (a partire da 1). */
    var storageStatus: (attempt: Int) -> HttpStatusCode = { HttpStatusCode.NoContent }
    /** Se vero, le richieste allo storage falliscono con un errore di rete. */
    var storageOffline = false
    /** Numero di conferme iniziali che falliscono per un errore di rete. */
    var confirmOfflineTimes = 0
    /** Stato della foto restituito dalla conferma (per esempio READY o QUARANTINED). */
    var mediaStatus = "READY"
    /** Id delle foto cancellate con DELETE /media/{id}. */
    val deleted = mutableListOf<String>()
    /** Stato HTTP della risposta alla cancellazione; da 300 in su si risponde con un ProblemDetail "not-found". */
    var deleteStatus = HttpStatusCode.NoContent

    // Installa le rotte dei media sul backend finto; le richieste che non riguardano i media passano alle rotte già presenti.
    init {
        val previous = backend.extraRoutes
        backend.extraRoutes = { request, owner ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post && path == "/api/v1/media/upload-requests" -> {
                    uploadRequests += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    val id = "m${uploadRequests.size}"
                    respond(
                        """{"mediaId":"$id","uploadUrl":"$STORAGE_URL","expiresAt":"$expiresAt",""" +
                            """"formFields":{"key":"media/$id","policy":"cG9saWN5","x-amz-signature":"sig","Content-Type":"image/jpeg"}}""",
                        HttpStatusCode.Created,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                request.method == HttpMethod.Post && path.startsWith("/api/v1/media/") && path.endsWith("/confirm") -> {
                    confirms++
                    if (confirms <= confirmOfflineTimes) throw IOException("rete assente")
                    val id = path.removePrefix("/api/v1/media/").removeSuffix("/confirm")
                    respond(
                        """{"id":"$id","contentType":"image/jpeg","createdAt":"$TEST_NOW","originalFilename":"photo.jpg",""" +
                            """"sizeBytes":4,"status":"$mediaStatus","entityType":"HAZARD_REPORT","entityId":"h9"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                request.method == HttpMethod.Delete && path.startsWith("/api/v1/media/") -> {
                    deleted += path.removePrefix("/api/v1/media/")
                    if (deleteStatus.value < 300) {
                        respond("", deleteStatus)
                    } else {
                        with(backend) { problem(deleteStatus, "not-found") }
                    }
                }
                else -> previous?.invoke(this, request, owner)
            }
        }
    }

    /**
     * Crea un MediaUploader che usa il client autenticato [client] per l'API e uno storage finto per i file. Il motore
     * dello storage usa il dispatcher del test, così anche le sue richieste seguono il tempo virtuale.
     */
    fun TestScope.uploader(client: HttpClient, clock: Clock = fixedClock(TEST_NOW)): MediaUploader {
        val storageEngine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                if (storageOffline) throw IOException("rete assente")
                storageRequests += request
                storageBodies += request.body.toByteArray()
                respond("", storageStatus(storageRequests.size))
            }
        })
        val storage = HttpClientFactory.createStorageClient(storageEngine)
        return MediaUploader({ client }, { storage }, TEST_BASE_URL, clock)
    }
}

/** Verifica la sequenza di caricamento delle foto e la gestione di scadenze, rifiuti ed errori di rete. */
class MediaUploaderTest {

    private val backend = FakeBackend()
    private val media = FakeMedia(backend)
    /** Il più piccolo file JPEG riconoscibile: solo i marcatori di inizio e fine immagine. */
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    /** Entità a cui la foto viene allegata: una segnalazione di pericolo. */
    private val target = MediaTarget(InitUploadRequest.EntityType.HAZARD_REPORT, "h9")

    /** Le rotte dei media richiedono l'accesso: crea una sessione autenticata e restituisce un uploader sul suo client. */
    private suspend fun TestScope.loggedInUploader(): MediaUploader {
        val (session, client) = sessionWith(backend)
        session.restore()
        assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        return with(media) { uploader(client) }
    }

    /** Carica [bytes] come foto della segnalazione di prova. */
    private suspend fun TestScope.upload(bytes: ByteArray = jpeg): Result<MediaResponse> = loggedInUploader().upload(bytes, target)

    /**
     * Caricamento completo: la richiesta di upload descrive tipo, dimensione ed entità; il file va allo storage senza
     * bearer (il token non deve uscire verso un altro host); la conferma va all'API con il bearer e la foto risulta READY.
     */
    @Test
    fun `richiesta di upload poi storage senza bearer poi conferma e la foto e READY`() = runTest {
        val result = upload()

        assertEquals(MediaResponse.Status.READY, result.getOrThrow().status)
        val request = media.uploadRequests.single()
        assertTrue("\"contentType\":\"image/jpeg\"" in request, request)
        assertTrue("\"sizeBytes\":4" in request, request)
        assertTrue("\"entityType\":\"HAZARD_REPORT\"" in request && "\"entityId\":\"h9\"" in request, request)
        val stored = media.storageRequests.single()
        assertEquals(STORAGE_URL, stored.url.toString())
        assertNull(stored.headers[HttpHeaders.Authorization], "il bearer non deve arrivare allo storage")
        assertEquals(1, media.confirms)
        assertTrue(backend.requests.any { it.startsWith("POST /api/v1/media/m1/confirm Bearer") }, backend.requests.toString())
    }

    /**
     * Il corpo inviato allo storage è multipart/form-data con un Content-Length esplicito (lo storage rifiuta il
     * trasferimento chunked), contiene prima i campi firmati e solo per ultimo il file, come richiede il protocollo
     * di upload con form firmato.
     */
    @Test
    fun `lo storage riceve i formFields poi il file per ultimo con Content-Length`() = runTest {
        upload().getOrThrow()

        val stored = media.storageRequests.single()
        val raw = media.storageBodies.single()
        assertEquals(raw.size.toLong(), stored.body.contentLength, "senza Content-Length lo storage rifiuta il chunked")
        assertTrue(stored.body.contentType.toString().startsWith("multipart/form-data"), stored.body.contentType.toString())
        val body = raw.decodeToString()
        val file = body.indexOf("name=\"file\"")
        val fields = listOf("key", "policy", "x-amz-signature", "Content-Type").map { body.indexOf("name=\"$it\"") }
        assertTrue(file > 0 && fields.all { it in 0 until file }, body)
        assertTrue("filename=\"photo.jpg\"" in body, body)
        // Dopo la parte del file non ci sono altre parti: il file è l'ultima.
        assertEquals(1, Regex("Content-Disposition").findAll(body.substring(body.lastIndexOf("Content-Disposition", file))).count(), body)
    }

    /** Una foto messa in quarantena dal server è un esito valido del caricamento, non un errore. */
    @Test
    fun `una foto QUARANTINED e un esito non un errore`() = runTest {
        media.mediaStatus = "QUARANTINED"

        assertEquals(MediaResponse.Status.QUARANTINED, upload().getOrThrow().status)
    }

    /**
     * Se il form firmato è già scaduto e lo storage risponde 403, il caricamento riparte una sola volta dalla richiesta
     * di upload e la seconda volta riesce.
     */
    @Test
    fun `dopo expiresAt lo storage rifiuta e si riparte una volta dalla richiesta di upload`() = runTest {
        media.expiresAt = Instant.parse("2026-10-05T09:59:00Z")
        media.storageStatus = { attempt -> if (attempt == 1) HttpStatusCode.Forbidden else HttpStatusCode.NoContent }

        val result = upload()

        assertTrue(result.isSuccess, result.toString())
        assertEquals(2, media.uploadRequests.size)
        assertEquals(2, media.storageRequests.size)
        assertTrue(backend.requests.any { it.startsWith("POST /api/v1/media/m2/confirm") }, backend.requests.toString())
    }

    /** Un 403 dello storage prima della scadenza è definitivo: non si ripete e non si chiede la conferma. */
    @Test
    fun `un rifiuto dello storage prima della scadenza non si ripete e non si conferma`() = runTest {
        media.storageStatus = { HttpStatusCode.Forbidden }

        val error = (upload().exceptionOrNull() as ApiException).error

        assertTrue(error is ApiError.BusinessRule, error.toString())
        assertEquals(1, media.uploadRequests.size)
        assertEquals(0, media.confirms)
    }

    /** Senza rete verso lo storage l'errore è Network e la conferma non parte. */
    @Test
    fun `senza rete verso lo storage l'errore e Network e non si conferma`() = runTest {
        media.storageOffline = true

        assertEquals(ApiError.Network, (upload().exceptionOrNull() as ApiException).error)
        assertEquals(0, media.confirms)
    }

    /**
     * Se la conferma si perde per un errore di rete viene ripetuta senza ricaricare il file, perché il file è già
     * nello storage.
     */
    @Test
    fun `la conferma persa per la rete si ripete senza ricaricare la foto`() = runTest {
        media.confirmOfflineTimes = 1

        assertEquals(MediaResponse.Status.READY, upload().getOrThrow().status)
        assertEquals(2, media.confirms)
        assertEquals(1, media.storageRequests.size)
    }

    /** Una foto più grande del massimo consentito viene rifiutata dal client prima di qualsiasi richiesta al server. */
    @Test
    fun `una foto oltre 10 MB non parte`() = runTest {
        val result = upload(ByteArray(MAX_UPLOAD_BYTES + 1))

        assertEquals(ApiError.BusinessRule("media-payload-too-large", null), (result.exceptionOrNull() as ApiException).error)
        assertTrue(media.uploadRequests.isEmpty())
    }
}
