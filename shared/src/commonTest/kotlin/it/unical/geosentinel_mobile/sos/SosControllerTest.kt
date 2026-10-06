package it.unical.geosentinel_mobile.sos

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.db.ActionKind
import it.unical.geosentinel_mobile.core.db.FakeOutboxDao
import it.unical.geosentinel_mobile.core.db.PendingActionEntity
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.jwtSubject
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.sos.data.SosRepository
import it.unical.geosentinel_mobile.sos.data.SosState
import it.unical.geosentinel_mobile.sos.ui.SosController
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SosControllerTest {

    private val backend = FakeBackend()
    private val outbox = FakeOutboxDao()
    private val owner = "id-hiker@test.dev" // l'id del profilo nel backend finto
    private val here = GeoPoint(lat = 39.278, lon = 16.452)
    private var keys = 0

    // il server: rete assente per POST /sos, un errore forzato, le allerte create (una per chiave, come l'idempotenza vera)
    private var sosOffline = false
    private var raiseProblem: Pair<HttpStatusCode, String>? = null
    private var activeOnServer: String? = null
    // per ogni POST /sos: chiave, body e se la chiave era già nell'outbox quando è arrivata la richiesta
    private val posts = mutableListOf<Triple<String?, String, Boolean>>()
    private val created = mutableMapOf<String, String>() // chiave -> id dell'allerta
    private val cancelled = mutableListOf<String>()
    private var cancelOffline = false
    // M6.2: stato dell'allerta sul server, PUT della posizione ricevuti (id, body), rete assente per PUT e GET active
    private var serverStatus = "ACTIVE"
    private val locationPuts = mutableListOf<Pair<String, String>>()
    private var locationOffline = false
    private var activeOffline = false

    private fun alertJson(id: String, status: String = "ACTIVE") =
        """{"id":"$id","hikerId":"$owner","location":{"lat":39.278,"lon":16.452},"status":"$status","createdAt":"2026-10-06T10:00:00Z","locationUpdatedAt":"2026-10-06T10:00:00Z"}"""

    private suspend fun TestScope.controller(login: Boolean = true): Pair<SosController, SessionManager> {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        backend.extraRoutes = { request, _ ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post && path == "/api/v1/sos" -> {
                    val key = request.headers["Idempotency-Key"]
                    posts += Triple(key, (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(), key in outbox.rows.value)
                    if (sosOffline) throw IOException("rete assente")
                    val problem = raiseProblem
                    if (problem != null) {
                        with(backend) { problem(problem.first, problem.second) }
                    } else {
                        // stessa chiave o allerta già attiva: 200 con quella; altrimenti 201 con una nuova
                        val existing = created[key] ?: activeOnServer
                        val id = existing ?: "s${created.size + 1}"
                        created[key ?: id] = id
                        activeOnServer = id
                        respond(alertJson(id), if (existing == null) HttpStatusCode.Created else HttpStatusCode.OK, json)
                    }
                }
                request.method == HttpMethod.Get && path == "/api/v1/sos/active" -> {
                    if (activeOffline) throw IOException("rete assente")
                    val id = activeOnServer
                    if (id == null) with(backend) { problem(HttpStatusCode.NotFound, "not-found") } else respond(alertJson(id, serverStatus), HttpStatusCode.OK, json)
                }
                request.method == HttpMethod.Put && path.startsWith("/api/v1/sos/") && path.endsWith("/location") -> {
                    val id = path.removePrefix("/api/v1/sos/").removeSuffix("/location")
                    locationPuts += id to (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    if (locationOffline) throw IOException("rete assente")
                    // chiusa dal soccorso: il dominio la rifiuta con 422
                    if (activeOnServer != id) with(backend) { problem(HttpStatusCode.UnprocessableEntity, "business-rule") }
                    else respond(alertJson(id, serverStatus), HttpStatusCode.OK, json)
                }
                request.method == HttpMethod.Delete && path.startsWith("/api/v1/sos/") -> {
                    if (cancelOffline) throw IOException("rete assente")
                    cancelled += path.substringAfterLast('/')
                    activeOnServer = null
                    respond("", HttpStatusCode.NoContent)
                }
                else -> null
            }
        }
        val (session, client) = sessionWith(backend)
        session.restore()
        if (login) assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        val controller = SosController(
            SosRepository({ client }, TEST_BASE_URL),
            outbox,
            ownerId = { session.currentUserId() },
            // backgroundScope: i retry senza fine si fermano con il test; il tempo è virtuale
            scope = backgroundScope,
            newKey = { "k${++keys}" },
        )
        return controller to session
    }

    private suspend fun SosController.awaitState(predicate: (SosState) -> Boolean): SosState = state.first(predicate)

    // ---- Invio ----

    @Test
    fun `la chiave si salva prima della richiesta e con il 201 il soccorso ha l'allerta`() = runTest {
        val (sos) = controller()

        assertTrue(sos.raise(here, "  allergico alla penicillina  "))
        val raised = assertIs<SosState.Raised>(sos.awaitState { it is SosState.Raised })

        assertEquals("s1", raised.alert.id)
        val (key, body, savedBefore) = posts.single()
        assertEquals("k1", key)
        assertTrue(savedBefore, "la chiave era già nell'outbox quando è partita la richiesta")
        assertTrue(""""lat":39.278""" in body && """"lon":16.452""" in body)
        assertTrue(""""medicalNotes":"allergico alla penicillina"""" in body, "note senza spazi attorno")
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `note mediche vuote non si mandano`() = runTest {
        val (sos) = controller()

        sos.raise(here, "   ")
        sos.awaitState { it is SosState.Raised }

        assertFalse("medicalNotes\":\"" in posts.single().second)
    }

    @Test
    fun `senza rete resta in invio e quando torna arriva una sola allerta`() = runTest {
        val (sos) = controller()
        sosOffline = true

        sos.raise(here, null)
        val sending = assertIs<SosState.Sending>(sos.awaitState { it is SosState.Sending && it.attempts >= 2 })
        assertEquals(1, outbox.rows.value.size, "l'SOS resta in coda")
        assertEquals(sending.attempts, outbox.rows.value.values.single().attempts)

        sosOffline = false
        sos.awaitState { it is SosState.Raised }

        assertEquals(setOf("k1"), posts.map { it.first }.toSet(), "sempre la stessa chiave")
        assertEquals(1, created.size, "una sola allerta")
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `dieci tocchi veloci fanno un solo SOS`() = runTest {
        val (sos) = controller()
        sosOffline = true // la prima richiesta resta senza risposta mentre arrivano gli altri tocchi

        (1..10).map { async { sos.raise(here, null) } }.awaitAll()
        sosOffline = false
        sos.awaitState { it is SosState.Raised }

        assertEquals(1, keys, "una sola Idempotency-Key")
        assertEquals(1, created.size)
    }

    @Test
    fun `un 200 con un'allerta già attiva è un successo`() = runTest {
        val (sos) = controller()
        activeOnServer = "s9" // lanciata da un altro dispositivo

        sos.raise(here, null)
        val raised = assertIs<SosState.Raised>(sos.awaitState { it is SosState.Raised })

        assertEquals("s9", raised.alert.id)
    }

    @Test
    fun `un 4xx definitivo toglie l'SOS dalla coda e lo dice`() = runTest {
        val (sos) = controller()
        raiseProblem = HttpStatusCode.Forbidden to "forbidden"

        sos.raise(here, null)
        val failed = assertIs<SosState.Failed>(sos.awaitState { it is SosState.Failed })

        assertIs<ApiError.Forbidden>(failed.error)
        assertEquals(1, posts.size, "un 403 non si ripete")
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `da ospite non parte nulla`() = runTest {
        val (sos) = controller(login = false)

        assertFalse(sos.raise(here, null))

        assertTrue(posts.isEmpty())
        assertTrue(outbox.rows.value.isEmpty())
        assertIs<SosState.Idle>(sos.state.value)
    }

    // ---- Riavvio e sessione ----

    @Test
    fun `dopo un riavvio l'SOS in coda riparte con la sua chiave`() = runTest {
        val (sos) = controller()
        outbox.insert(PendingActionEntity("vecchia", ActionKind.SOS, owner, """{"lat":39.0,"lon":16.0,"medicalNotes":null}""", Instant.fromEpochSeconds(1_800_000_000), attempts = 3))

        sos.restore()
        sos.awaitState { it is SosState.Raised }

        assertEquals("vecchia", posts.single().first)
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `un SOS in coda di un altro utente non parte`() = runTest {
        val (sos) = controller()
        outbox.insert(PendingActionEntity("altro", ActionKind.SOS, "id-altro", """{"lat":39.0,"lon":16.0,"medicalNotes":null}""", Instant.fromEpochSeconds(1_800_000_000)))

        sos.restore()

        assertTrue(posts.isEmpty())
        assertIs<SosState.Idle>(sos.state.value)
        assertEquals(1, outbox.rows.value.size)
    }

    @Test
    fun `all'avvio si rilegge l'allerta attiva`() = runTest {
        val (sos) = controller()
        activeOnServer = "s7"

        sos.restore()

        assertEquals("s7", assertIs<SosState.Raised>(sos.state.value).alert.id)
    }

    @Test
    fun `al logout si smette di inviare ma l'SOS resta in coda`() = runTest {
        val (sos) = controller()
        sosOffline = true
        sos.raise(here, null)
        sos.awaitState { it is SosState.Sending && it.attempts >= 1 }

        sos.reset()

        assertIs<SosState.Idle>(sos.state.value)
        assertEquals(1, outbox.rows.value.size)
    }

    // ---- Annullamento ----

    @Test
    fun `annullare un'allerta la chiude sul server`() = runTest {
        val (sos) = controller()
        sos.raise(here, null)
        sos.awaitState { it is SosState.Raised }

        assertTrue(sos.cancel().isSuccess)

        assertEquals(listOf("s1"), cancelled)
        assertIs<SosState.Idle>(sos.state.value)
    }

    @Test
    fun `annullare senza rete lascia l'allerta e restituisce l'errore`() = runTest {
        val (sos) = controller()
        sos.raise(here, null)
        sos.awaitState { it is SosState.Raised }
        cancelOffline = true

        assertTrue(sos.cancel().isFailure)

        assertIs<SosState.Raised>(sos.state.value)
    }

    @Test
    fun `annullare un SOS ancora in coda lo toglie e non lo manda più`() = runTest {
        val (sos) = controller()
        sosOffline = true
        sos.raise(here, null)
        sos.awaitState { it is SosState.Sending && it.attempts >= 1 }
        val before = posts.size

        assertTrue(sos.cancel().isSuccess)
        sosOffline = false

        assertIs<SosState.Idle>(sos.state.value)
        assertTrue(outbox.rows.value.isEmpty())
        assertEquals(before, posts.size)
        assertTrue(created.isEmpty())
    }

    // ---- Id dell'utente dal token ----

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `il sub del JWT è l'id dell'utente e un token illeggibile dà null`() {
        val payload = Base64.UrlSafe.encode("""{"sub":"3f1c-utente","roles":["HIKER"]}""".encodeToByteArray()).trimEnd('=')

        assertEquals("3f1c-utente", jwtSubject("eyJhbGciOiJIUzI1NiJ9.$payload.firma"))
        assertNull(jwtSubject("a1"))
        assertNull(jwtSubject("x.%%%.y"))
    }

    // ---- Posizione e stato (M6.2) ----

    private suspend fun TestScope.raised(): SosController {
        val (sos) = controller()
        sos.raise(here, null)
        sos.awaitState { it is SosState.Raised }
        return sos
    }

    @Test
    fun `la posizione nuova va all'allerta attiva e la risposta aggiorna lo stato`() = runTest {
        val sos = raised()
        serverStatus = "DISPATCHED"

        sos.updateLocation(GeoPoint(lat = 39.31, lon = 16.47))

        val (id, body) = locationPuts.single()
        assertEquals("s1", id)
        assertTrue(""""newLocation":{""" in body && """"lat":39.31""" in body && """"lon":16.47""" in body)
        assertEquals(SosAlertResponse.Status.DISPATCHED, assertIs<SosState.Raised>(sos.state.value).alert.status)
    }

    @Test
    fun `senza allerta attiva la posizione non parte`() = runTest {
        val (sos) = controller()
        sosOffline = true
        sos.raise(here, null)
        sos.awaitState { it is SosState.Sending }

        sos.updateLocation(here)

        assertTrue(locationPuts.isEmpty(), "in coda non c'è ancora un id")
    }

    @Test
    fun `posizione senza rete lascia l'allerta com'è`() = runTest {
        val sos = raised()
        locationOffline = true

        sos.updateLocation(here)

        assertEquals("s1", assertIs<SosState.Raised>(sos.state.value).alert.id)
    }

    @Test
    fun `allerta chiusa dal soccorso con il 422 sulla posizione si torna a Idle`() = runTest {
        val sos = raised()
        activeOnServer = null // risolta dall'operatore

        sos.updateLocation(here)

        assertIs<SosState.Idle>(sos.state.value)
    }

    @Test
    fun `il refresh porta la squadra in arrivo senza push`() = runTest {
        val sos = raised()
        serverStatus = "DISPATCHED"

        sos.refresh()

        assertEquals(SosAlertResponse.Status.DISPATCHED, assertIs<SosState.Raised>(sos.state.value).alert.status)
    }

    @Test
    fun `refresh senza rete non cambia nulla e senza allerta non chiama`() = runTest {
        val sos = raised()
        activeOffline = true

        sos.refresh()
        assertIs<SosState.Raised>(sos.state.value)

        activeOffline = false
        assertTrue(sos.cancel().isSuccess)
        activeOffline = true // se il refresh chiamasse il server, la rete assente lancerebbe
        sos.refresh()
        assertIs<SosState.Idle>(sos.state.value)
    }
}
