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

const val STORAGE_URL = "http://storage.test/geosentinel-media"
val TEST_NOW: Instant = Instant.parse("2026-10-05T10:00:00Z")

fun fixedClock(now: Instant) = object : Clock {
    override fun now() = now
}

/** API dei media e storage finti, con quello che ricevono. Si appoggia alle rotte in più di [FakeBackend]. */
class FakeMedia(backend: FakeBackend) {
    val uploadRequests = mutableListOf<String>() // corpi di POST /media/upload-requests
    val storageRequests = mutableListOf<HttpRequestData>()
    val storageBodies = mutableListOf<ByteArray>()
    var confirms = 0
    var expiresAt: Instant = Instant.parse("2026-10-05T10:10:00Z")
    var storageStatus: (attempt: Int) -> HttpStatusCode = { HttpStatusCode.NoContent }
    var storageOffline = false
    var confirmOfflineTimes = 0 // le prime N conferme cadono per la rete
    var mediaStatus = "READY"

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
                else -> previous?.invoke(this, request, owner)
            }
        }
    }

    /** Uploader sul client autenticato [client] e su uno storage finto, con il motore sul dispatcher del test. */
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

class MediaUploaderTest {

    private val backend = FakeBackend()
    private val media = FakeMedia(backend)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    private val target = MediaTarget(InitUploadRequest.EntityType.HAZARD_REPORT, "h9")

    // Le rotte /media vogliono il login: uploader sul client della sessione aperta
    private suspend fun TestScope.loggedInUploader(): MediaUploader {
        val (session, client) = sessionWith(backend)
        session.restore()
        assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        return with(media) { uploader(client) }
    }

    private suspend fun TestScope.upload(bytes: ByteArray = jpeg): Result<MediaResponse> = loggedInUploader().upload(bytes, target)

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
        // la conferma invece va all'API con il bearer
        assertTrue(backend.requests.any { it.startsWith("POST /api/v1/media/m1/confirm Bearer") }, backend.requests.toString())
    }

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
        // il file è l'ultima parte
        assertEquals(1, Regex("Content-Disposition").findAll(body.substring(body.lastIndexOf("Content-Disposition", file))).count(), body)
    }

    @Test
    fun `una foto QUARANTINED e un esito non un errore`() = runTest {
        media.mediaStatus = "QUARANTINED"

        assertEquals(MediaResponse.Status.QUARANTINED, upload().getOrThrow().status)
    }

    @Test
    fun `dopo expiresAt lo storage rifiuta e si riparte una volta dalla richiesta di upload`() = runTest {
        media.expiresAt = Instant.parse("2026-10-05T09:59:00Z") // già scaduta per l'orologio del test
        media.storageStatus = { attempt -> if (attempt == 1) HttpStatusCode.Forbidden else HttpStatusCode.NoContent }

        val result = upload()

        assertTrue(result.isSuccess, result.toString())
        assertEquals(2, media.uploadRequests.size)
        assertEquals(2, media.storageRequests.size)
        assertTrue(backend.requests.any { it.startsWith("POST /api/v1/media/m2/confirm") }, backend.requests.toString())
    }

    @Test
    fun `un rifiuto dello storage prima della scadenza non si ripete e non si conferma`() = runTest {
        media.storageStatus = { HttpStatusCode.Forbidden }

        val error = (upload().exceptionOrNull() as ApiException).error

        assertTrue(error is ApiError.BusinessRule, error.toString())
        assertEquals(1, media.uploadRequests.size)
        assertEquals(0, media.confirms)
    }

    @Test
    fun `senza rete verso lo storage l'errore e Network e non si conferma`() = runTest {
        media.storageOffline = true

        assertEquals(ApiError.Network, (upload().exceptionOrNull() as ApiException).error)
        assertEquals(0, media.confirms)
    }

    @Test
    fun `la conferma persa per la rete si ripete senza ricaricare la foto`() = runTest {
        media.confirmOfflineTimes = 1

        assertEquals(MediaResponse.Status.READY, upload().getOrThrow().status)
        assertEquals(2, media.confirms)
        assertEquals(1, media.storageRequests.size)
    }

    @Test
    fun `una foto oltre 10 MB non parte`() = runTest {
        val result = upload(ByteArray(MAX_UPLOAD_BYTES + 1))

        assertEquals(ApiError.BusinessRule("media-payload-too-large", null), (result.exceptionOrNull() as ApiException).error)
        assertTrue(media.uploadRequests.isEmpty())
    }
}
