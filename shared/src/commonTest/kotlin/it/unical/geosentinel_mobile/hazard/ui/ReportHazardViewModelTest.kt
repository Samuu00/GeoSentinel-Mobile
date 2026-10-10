package it.unical.geosentinel_mobile.hazard.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_forbidden
import geosentinel_mobile.shared.generated.resources.error_network
import geosentinel_mobile.shared.generated.resources.error_validation
import geosentinel_mobile.shared.generated.resources.report_photo_unreadable
import it.unical.geosentinel_mobile.core.ui.UiMessage
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.model.CreateHazardRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import it.unical.geosentinel_mobile.media.data.FakeMedia
import it.unical.geosentinel_mobile.media.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReportHazardViewModelTest {

    private val backend = FakeBackend()
    // corpi delle POST /hazards ricevute
    private val sent = mutableListOf<String>()
    // risposta del backend finto: null = 201 con la segnalazione SUBMITTED
    private var answer: (suspend () -> Pair<HttpStatusCode, String>)? = null

    // Sila, lat 39 e lon 16: invertite finirebbero nel Golfo di Aden
    private val sila = GeoPoint(lat = 39.3, lon = 16.5)

    private suspend fun TestScope.viewModel(trailId: String? = "t1"): ReportHazardViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        backend.extraRoutes = { request, _ ->
            if (request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/hazards") {
                sent += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                val (status, body) = answer?.invoke() ?: (HttpStatusCode.Created to CREATED_JSON)
                respond(body, status, headersOf("Content-Type", if (status.value < 300) "application/json" else "application/problem+json"))
            } else {
                null
            }
        }
        // dopo le rotte dei pericoli: FakeMedia le tiene e aggiunge quelle dei media
        media = FakeMedia(backend)
        val (session, client) = sessionWith(backend)
        session.restore()
        assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        return ReportHazardViewModel(HazardRepository({ client }, TEST_BASE_URL), with(media) { uploader(client) }, MediaRepository({ client }, TEST_BASE_URL), trailId)
    }

    private lateinit var media: FakeMedia
    private val jpeg = byteArrayOf(1, 2, 3, 4)

    private suspend fun TestScope.sentViewModel(): ReportHazardViewModel {
        val viewModel = viewModel()
        viewModel.fill()
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sent)
        return viewModel
    }

    private fun problem(status: Int, slug: String, field: String? = null): String {
        val errors = field?.let { ""","errors":[{"field":"$it","message":"invalid"}]""" }.orEmpty()
        return """{"type":"https://geosentinel.dev/problems/$slug","title":"x","status":$status$errors}"""
    }

    private fun ReportHazardViewModel.fill(description: String = "") {
        onTypeChange(CreateHazardRequest.Type.FALLEN_TREE)
        onSeverityChange(CreateHazardRequest.Severity.HIGH)
        onLocationChange(sila)
        onDescriptionChange(description)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `senza tipo gravità e posizione il form segna i campi e non manda niente`() = runTest {
        val viewModel = viewModel()

        viewModel.submit()
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals(setOf(HazardField.TYPE, HazardField.SEVERITY, HazardField.LOCATION), ui.fieldErrors.keys)
        assertTrue(sent.isEmpty())
        // scegliere il tipo toglie solo il suo errore
        viewModel.onTypeChange(CreateHazardRequest.Type.LANDSLIDE)
        assertEquals(setOf(HazardField.SEVERITY, HazardField.LOCATION), viewModel.state.value.fieldErrors.keys)
    }

    @Test
    fun `la segnalazione parte con tipo gravità lat lon sentiero e descrizione senza spazi`() = runTest {
        val viewModel = viewModel()
        viewModel.fill(description = "  Abete caduto dopo il ponte  ")

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sent)
        val body = sent.single()
        assertTrue("\"type\":\"FALLEN_TREE\"" in body, body)
        assertTrue("\"severity\":\"HIGH\"" in body, body)
        assertTrue("\"lat\":39.3" in body && "\"lon\":16.5" in body, body)
        assertTrue("\"trailId\":\"t1\"" in body, body)
        assertTrue("\"description\":\"Abete caduto dopo il ponte\"" in body, body)
    }

    @Test
    fun `dalla mappa la segnalazione non ha sentiero e una descrizione vuota non si manda`() = runTest {
        val viewModel = viewModel(trailId = null)
        viewModel.fill(description = "   ")

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sent)
        val body = sent.single()
        assertFalse("\"trailId\":\"" in body, body)
        assertFalse("\"description\":\"" in body, body)
    }

    @Test
    fun `una descrizione oltre i 1000 caratteri si ferma nel form`() = runTest {
        val viewModel = viewModel()
        viewModel.fill(description = "a".repeat(HAZARD_DESCRIPTION_MAX + 1))

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(setOf(HazardField.DESCRIPTION), viewModel.state.value.fieldErrors.keys)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `un 400 sulla descrizione segna il campo e uno su un campo che il form non ha da un errore generico`() = runTest {
        val viewModel = viewModel()
        viewModel.fill(description = "ok")
        answer = { HttpStatusCode.BadRequest to problem(400, "validation-error", field = "description") }

        viewModel.submit()
        advanceUntilIdle()
        var ui = viewModel.state.value
        assertEquals(setOf(HazardField.DESCRIPTION), ui.fieldErrors.keys)
        assertNull(ui.error)
        assertFalse(ui.sent)
        assertFalse(ui.submitting)

        answer = { HttpStatusCode.BadRequest to problem(400, "validation-error", field = "trailId") }
        viewModel.submit()
        advanceUntilIdle()
        ui = viewModel.state.value
        assertTrue(ui.fieldErrors.isEmpty())
        assertEquals(Res.string.error_validation, ui.error?.text)
    }

    @Test
    fun `posizione fuori range dal server diventa errore della posizione`() = runTest {
        val viewModel = viewModel()
        viewModel.fill()
        answer = { HttpStatusCode.BadRequest to problem(400, "validation-error", field = "location.lat") }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(setOf(HazardField.LOCATION), viewModel.state.value.fieldErrors.keys)
    }

    @Test
    fun `senza rete un solo tentativo e il form resta compilato`() = runTest {
        val viewModel = viewModel()
        viewModel.fill(description = "frana")
        answer = { throw IOException("rete assente") }

        viewModel.submit()
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals(1, sent.size, "POST senza Idempotency-Key: niente retry automatici")
        assertEquals(Res.string.error_network, ui.error?.text)
        assertFalse(ui.sent)
        assertEquals("frana", ui.description)
        assertEquals(CreateHazardRequest.Type.FALLEN_TREE, ui.type)
    }

    @Test
    fun `un 403 mostra il messaggio del ruolo`() = runTest {
        val viewModel = viewModel()
        viewModel.fill()
        answer = { HttpStatusCode.Forbidden to problem(403, "forbidden") }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(Res.string.error_forbidden, viewModel.state.value.error?.text)
    }

    @Test
    fun `due tocchi su Invia mandano una sola segnalazione`() = runTest {
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()
        viewModel.submit() // già inviata
        advanceUntilIdle()

        assertEquals(1, sent.size)
        assertTrue(viewModel.state.value.sent)
    }

    @Test
    fun `lo stato nei log non contiene posizione né descrizione`() = runTest {
        val viewModel = viewModel()
        viewModel.fill(description = "vicino alla baita di Mario")

        val text = viewModel.state.value.toString()
        assertFalse("39.3" in text || "16.5" in text, text)
        assertFalse("Mario" in text, text)
    }

    @Test
    fun `una foto dopo l'invio si allega alla segnalazione appena creata`() = runTest {
        val viewModel = sentViewModel()

        viewModel.addPhoto { jpeg }
        assertEquals(PhotoStatus.UPLOADING, viewModel.state.value.photos.single().status)
        advanceUntilIdle()

        assertEquals(PhotoStatus.READY, viewModel.state.value.photos.single().status)
        val request = media.uploadRequests.single()
        assertTrue("\"entityType\":\"HAZARD_REPORT\"" in request && "\"entityId\":\"h9\"" in request, request)
        assertEquals(1, media.confirms)
    }

    @Test
    fun `prima dell'invio non si caricano foto`() = runTest {
        val viewModel = viewModel()

        viewModel.addPhoto { jpeg }
        advanceUntilIdle()

        assertTrue(viewModel.state.value.photos.isEmpty())
        assertTrue(media.uploadRequests.isEmpty())
    }

    @Test
    fun `senza rete la foto resta non caricata e Riprova la manda una volta sola`() = runTest {
        val viewModel = sentViewModel()
        media.storageOffline = true

        viewModel.addPhoto { jpeg }
        advanceUntilIdle()
        val photo = viewModel.state.value.photos.single()
        assertEquals(PhotoStatus.FAILED, photo.status)
        assertEquals(UiMessage(Res.string.error_network), viewModel.state.value.photoError)

        media.storageOffline = false
        viewModel.retryPhoto(photo.id)
        viewModel.retryPhoto(photo.id) // doppio tocco
        advanceUntilIdle()

        assertEquals(PhotoStatus.READY, viewModel.state.value.photos.single().status)
        assertNull(viewModel.state.value.photoError)
        assertEquals(1, media.confirms)
    }

    @Test
    fun `una foto in quarantena e non accettata senza Riprova`() = runTest {
        val viewModel = sentViewModel()
        media.mediaStatus = "QUARANTINED"

        viewModel.addPhoto { jpeg }
        advanceUntilIdle()

        val photo = viewModel.state.value.photos.single()
        assertEquals(PhotoStatus.REJECTED, photo.status)
        viewModel.retryPhoto(photo.id)
        advanceUntilIdle()
        assertEquals(1, media.uploadRequests.size)
    }

    @Test
    fun `una foto illeggibile non entra in lista e da un messaggio`() = runTest {
        val viewModel = sentViewModel()

        viewModel.addPhoto { throw IOException("formato non supportato") }
        advanceUntilIdle()

        assertTrue(viewModel.state.value.photos.isEmpty())
        assertEquals(UiMessage(Res.string.report_photo_unreadable), viewModel.state.value.photoError)
        assertTrue(media.uploadRequests.isEmpty())
    }

    @Test
    fun `togliere una foto caricata la cancella sul server e la toglie dalla lista`() = runTest {
        val viewModel = sentViewModel()
        viewModel.addPhoto { jpeg }
        advanceUntilIdle()
        val photo = viewModel.state.value.photos.single()

        viewModel.removePhoto(photo.id)
        assertEquals(PhotoStatus.REMOVING, viewModel.state.value.photos.single().status)
        viewModel.removePhoto(photo.id) // doppio tocco
        advanceUntilIdle()

        assertEquals(listOf("m1"), media.deleted)
        assertTrue(viewModel.state.value.photos.isEmpty())
    }

    @Test
    fun `se la cancellazione non riesce la foto resta con l'errore`() = runTest {
        val viewModel = sentViewModel()
        viewModel.addPhoto { jpeg }
        advanceUntilIdle()
        media.deleteStatus = HttpStatusCode.InternalServerError

        viewModel.removePhoto(viewModel.state.value.photos.single().id)
        advanceUntilIdle()

        assertEquals(PhotoStatus.READY, viewModel.state.value.photos.single().status)
        assertTrue(viewModel.state.value.photoError != null)
    }

    @Test
    fun `una foto non partita si toglie senza chiamare il server`() = runTest {
        val viewModel = sentViewModel()
        media.storageOffline = true
        viewModel.addPhoto { jpeg }
        advanceUntilIdle()

        viewModel.removePhoto(viewModel.state.value.photos.single().id)

        assertTrue(viewModel.state.value.photos.isEmpty())
        assertTrue(media.deleted.isEmpty())
    }

    @Test
    fun `oltre il limite di foto non se ne aggiungono altre`() = runTest {
        val viewModel = sentViewModel()

        repeat(MAX_HAZARD_PHOTOS + 1) { viewModel.addPhoto { jpeg } }
        advanceUntilIdle()

        assertEquals(MAX_HAZARD_PHOTOS, viewModel.state.value.photos.size)
        assertEquals(MAX_HAZARD_PHOTOS, media.uploadRequests.size)
    }

    private companion object {
        const val CREATED_JSON =
            """{"id":"h9","trailId":"t1","type":"FALLEN_TREE","severity":"HIGH","status":"SUBMITTED","location":{"lat":39.3,"lon":16.5},"createdAt":"2026-10-05T10:00:00Z"}"""
    }
}
