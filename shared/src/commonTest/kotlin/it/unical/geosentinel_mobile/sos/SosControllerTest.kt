// Test di SosController, il componente unico per tutta l'app che invia l'SOS e ne segue lo stato. L'SOS passa per
// l'outbox con una chiave di idempotenza, così un invio interrotto (rete assente, app chiusa) riparte con la stessa
// chiave e il server crea una sola allerta. I test coprono invio, riavvio, annullamento con verifica sul server,
// invio periodico della posizione e lettura dello stato.
package it.unical.geosentinel_mobile.sos

import io.ktor.client.HttpClient
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
import kotlinx.coroutines.delay
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

/** Verifica il ciclo di vita di un SOS contro un backend finto che simula le allerte e l'idempotenza del server. */
class SosControllerTest {

    private val backend = FakeBackend()
    /** Outbox in memoria in cui il controller salva l'SOS prima di inviarlo. */
    private val outbox = FakeOutboxDao()
    /** Id del profilo dell'utente di prova nel backend finto. */
    private val owner = "id-hiker@test.dev"
    /** Posizione da cui parte l'SOS nei test. */
    private val here = GeoPoint(lat = 39.278, lon = 16.452)
    /** Contatore usato per generare chiavi di idempotenza prevedibili. */
    private var keys = 0

    /** Se vero, POST /sos fallisce con un errore di rete. */
    private var sosOffline = false
    /** Se impostato, POST /sos risponde con questo stato e questo slug. */
    private var raiseProblem: Pair<HttpStatusCode, String>? = null
    /** Id dell'allerta attiva sul server, null se non ce n'è. */
    private var activeOnServer: String? = null
    /** Per ogni POST /sos: chiave, corpo e se la chiave era già nell'outbox quando la richiesta è arrivata. */
    private val posts = mutableListOf<Triple<String?, String, Boolean>>()
    /** Allerte create dal server: chiave di idempotenza -> id dell'allerta. Una sola allerta per chiave, come sul server. */
    private val created = mutableMapOf<String, String>()
    /** Id delle allerte annullate con DELETE. */
    private val cancelled = mutableListOf<String>()
    /** Se vero, l'annullamento fallisce con un errore di rete. */
    private var cancelOffline = false
    /** Stato dell'allerta restituito dal server (per esempio DISPATCHED quando parte la squadra). */
    private var serverStatus = "ACTIVE"
    /** Aggiornamenti di posizione ricevuti: id dell'allerta e corpo della richiesta. */
    private val locationPuts = mutableListOf<Pair<String, String>>()
    /** Se vero, l'invio della posizione fallisce con un errore di rete. */
    private var locationOffline = false
    /** Se vero, la lettura dell'allerta attiva fallisce con un errore di rete. */
    private var activeOffline = false
    /** Se vero, il server crea l'allerta ma la risposta si perde per strada. */
    private var loseRaiseResponse = false
    /** Se vero, il server risponde 201 con un corpo fuori contratto, come farebbe per un bug: l'app non deve crashare. */
    private var raiseMalformed = false
    /** Ritardo della lettura dell'allerta attiva in millisecondi di tempo virtuale, come con una rete scarsa in montagna. */
    private var activeDelayMs = 0L
    /** Client dell'ultimo controller creato: un controller nuovo sulla stessa outbox simula un riavvio dell'app. */
    private var httpClient: HttpClient? = null

    /** JSON di un'allerta dell'utente di prova con l'id e lo stato indicati. */
    private fun alertJson(id: String, status: String = "ACTIVE") =
        """{"id":"$id","hikerId":"$owner","location":{"lat":39.278,"lon":16.452},"status":"$status","createdAt":"2026-10-06T10:00:00Z","locationUpdatedAt":"2026-10-06T10:00:00Z"}"""

    /**
     * Installa le rotte finte dell'SOS, crea la sessione (autenticata se [login]) e restituisce un SosController
     * collegato all'outbox in memoria insieme alla sessione.
     */
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
                    } else if (raiseMalformed) {
                        respond("""{"id":42}""", HttpStatusCode.Created, json)
                    } else {
                        // Stessa chiave o allerta già attiva: 200 con quella allerta; altrimenti 201 con un'allerta nuova.
                        val existing = created[key] ?: activeOnServer
                        val id = existing ?: "s${created.size + 1}"
                        created[key ?: id] = id
                        activeOnServer = id
                        if (loseRaiseResponse) throw IOException("risposta persa")
                        respond(alertJson(id), if (existing == null) HttpStatusCode.Created else HttpStatusCode.OK, json)
                    }
                }
                request.method == HttpMethod.Get && path == "/api/v1/sos/active" -> {
                    if (activeOffline) throw IOException("rete assente")
                    if (activeDelayMs > 0) delay(activeDelayMs)
                    val id = activeOnServer
                    if (id == null) with(backend) { problem(HttpStatusCode.NotFound, "not-found") } else respond(alertJson(id, serverStatus), HttpStatusCode.OK, json)
                }
                request.method == HttpMethod.Put && path.startsWith("/api/v1/sos/") && path.endsWith("/location") -> {
                    val id = path.removePrefix("/api/v1/sos/").removeSuffix("/location")
                    locationPuts += id to (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    if (locationOffline) throw IOException("rete assente")
                    // Un'allerta chiusa dal soccorso non accetta più posizioni: il dominio risponde 422.
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
        httpClient = client
        session.restore()
        if (login) assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        val controller = SosController(
            SosRepository({ client }, TEST_BASE_URL),
            outbox,
            ownerId = { session.currentUserId() },
            // backgroundScope ferma con il test i tentativi senza fine; il tempo è virtuale.
            scope = backgroundScope,
            newKey = { "k${++keys}" },
        )
        return controller to session
    }

    /** Aspetta il primo stato del controller che soddisfa [predicate] e lo restituisce. */
    private suspend fun SosController.awaitState(predicate: (SosState) -> Boolean): SosState = state.first(predicate)


    /**
     * La chiave di idempotenza viene salvata nell'outbox prima della richiesta; la richiesta contiene posizione e
     * note mediche senza spazi ai lati, e con il 201 l'allerta è attiva e l'outbox è vuota.
     */
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

    /** Note mediche fatte di soli spazi non vengono inviate. */
    @Test
    fun `note mediche vuote non si mandano`() = runTest {
        val (sos) = controller()

        sos.raise(here, "   ")
        sos.awaitState { it is SosState.Raised }

        assertFalse("medicalNotes\":\"" in posts.single().second)
    }

    /**
     * Senza rete l'SOS resta in invio e in coda, con il numero di tentativi salvato; quando la rete torna arriva al
     * server una sola allerta, sempre con la stessa chiave.
     */
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

    /**
     * Dieci tocchi veloci, mentre la prima richiesta è ancora senza risposta, producono un solo SOS con una sola
     * chiave di idempotenza.
     */
    @Test
    fun `dieci tocchi veloci fanno un solo SOS`() = runTest {
        val (sos) = controller()
        sosOffline = true

        (1..10).map { async { sos.raise(here, null) } }.awaitAll()
        sosOffline = false
        sos.awaitState { it is SosState.Raised }

        assertEquals(1, keys, "una sola Idempotency-Key")
        assertEquals(1, created.size)
    }

    /** Se esiste già un'allerta attiva (lanciata da un altro dispositivo) il server risponde 200 con quella, ed è un successo. */
    @Test
    fun `un 200 con un'allerta già attiva è un successo`() = runTest {
        val (sos) = controller()
        activeOnServer = "s9"

        sos.raise(here, null)
        val raised = assertIs<SosState.Raised>(sos.awaitState { it is SosState.Raised })

        assertEquals("s9", raised.alert.id)
    }

    /** Un 4xx definitivo (qui 403) toglie l'SOS dalla coda, non viene ripetuto e lo stato mostra l'errore. */
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

    /** Da ospite l'SOS non parte e non viene messo in coda: il server richiede un utente autenticato. */
    @Test
    fun `da ospite non parte nulla`() = runTest {
        val (sos) = controller(login = false)

        assertFalse(sos.raise(here, null))

        assertTrue(posts.isEmpty())
        assertTrue(outbox.rows.value.isEmpty())
        assertIs<SosState.Idle>(sos.state.value)
    }


    /** Dopo un riavvio dell'app l'SOS rimasto in coda riparte con la sua chiave originale. */
    @Test
    fun `dopo un riavvio l'SOS in coda riparte con la sua chiave`() = runTest {
        val (sos) = controller()
        outbox.insert(PendingActionEntity("vecchia", ActionKind.SOS, owner, """{"lat":39.0,"lon":16.0,"medicalNotes":null}""", Instant.fromEpochSeconds(1_800_000_000), attempts = 3))

        sos.restore()
        sos.awaitState { it is SosState.Raised }

        assertEquals("vecchia", posts.single().first)
        assertTrue(outbox.rows.value.isEmpty())
    }

    /** Un SOS in coda che appartiene a un altro utente non parte e resta nell'outbox. */
    @Test
    fun `un SOS in coda di un altro utente non parte`() = runTest {
        val (sos) = controller()
        outbox.insert(PendingActionEntity("altro", ActionKind.SOS, "id-altro", """{"lat":39.0,"lon":16.0,"medicalNotes":null}""", Instant.fromEpochSeconds(1_800_000_000)))

        sos.restore()

        assertTrue(posts.isEmpty())
        assertIs<SosState.Idle>(sos.state.value)
        assertEquals(1, outbox.rows.value.size)
    }

    /** All'avvio si rilegge dal server l'allerta attiva, così la schermata mostra l'SOS in corso. */
    @Test
    fun `all'avvio si rilegge l'allerta attiva`() = runTest {
        val (sos) = controller()
        activeOnServer = "s7"

        sos.restore()

        assertEquals("s7", assertIs<SosState.Raised>(sos.state.value).alert.id)
    }

    /** Al logout il controller smette di inviare e torna inattivo, ma l'SOS resta nell'outbox. */
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


    /** Annullare un'allerta attiva la chiude sul server e riporta il controller allo stato inattivo. */
    @Test
    fun `annullare un'allerta la chiude sul server`() = runTest {
        val (sos) = controller()
        sos.raise(here, null)
        sos.awaitState { it is SosState.Raised }

        assertTrue(sos.cancel().isSuccess)

        assertEquals(listOf("s1"), cancelled)
        assertIs<SosState.Idle>(sos.state.value)
    }

    /** Se l'annullamento fallisce per la rete l'allerta resta attiva e viene restituito l'errore. */
    @Test
    fun `annullare senza rete lascia l'allerta e restituisce l'errore`() = runTest {
        val (sos) = controller()
        sos.raise(here, null)
        sos.awaitState { it is SosState.Raised }
        cancelOffline = true

        assertTrue(sos.cancel().isFailure)

        assertIs<SosState.Raised>(sos.state.value)
    }

    /**
     * Annullare un SOS ancora in coda lo toglie e non lo invia più. Resta una verifica sul server, perché la
     * richiesta potrebbe essere arrivata: quando la verifica conferma che non c'è nessuna allerta, anche la sua riga
     * sparisce dall'outbox.
     */
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
        assertTrue(outbox.rows.value.values.none { it.kind == ActionKind.SOS })
        assertEquals(before, posts.size)
        assertTrue(created.isEmpty())
        // La verifica sul server (che non trova allerte) termina e toglie la sua riga.
        testScheduler.advanceTimeBy(30_000)
        assertTrue(outbox.rows.value.isEmpty())
    }

    /**
     * La verifica di un annullamento è salvata nell'outbox: dopo un riavvio riparte, trova l'allerta creata dalla
     * richiesta la cui risposta si era persa e la chiude, senza mai mostrarla all'utente che aveva annullato.
     */
    @Test
    fun `la verifica di un annullamento riparte dopo un riavvio e chiude l'allerta senza mostrarla`() = runTest {
        val (sos, session) = controller()
        loseRaiseResponse = true
        sos.raise(here, null)
        sos.awaitState { it is SosState.Sending && it.attempts >= 1 }
        activeOffline = true
        assertTrue(sos.cancel().isSuccess)
        assertEquals(listOf(ActionKind.SOS_CANCEL), outbox.rows.value.values.map { it.kind })

        activeOffline = false
        // L'app viene chiusa prima che la rete torni: un controller nuovo sulla stessa outbox simula il riavvio.
        val client = httpClient!!
        val restarted = SosController(
            SosRepository({ client }, TEST_BASE_URL),
            outbox,
            ownerId = { session.currentUserId() },
            scope = backgroundScope,
        )
        restarted.restore()

        assertIs<SosState.Idle>(restarted.state.value, "l'utente aveva annullato: niente allerta a schermo")
        testScheduler.advanceTimeBy(30_000)
        assertEquals(1, cancelled.size)
        assertTrue(outbox.rows.value.isEmpty())
    }

    /** Un nuovo SOS toglie la verifica di un annullamento precedente, così l'allerta nuova non viene chiusa per errore. */
    @Test
    fun `un SOS nuovo toglie la verifica di un annullamento precedente`() = runTest {
        val (sos) = controller()
        loseRaiseResponse = true
        sos.raise(here, null)
        sos.awaitState { it is SosState.Sending && it.attempts >= 1 }
        activeOffline = true
        sos.cancel()

        loseRaiseResponse = false
        sos.raise(here, null)
        sos.awaitState { it is SosState.Raised }
        activeOffline = false
        testScheduler.advanceTimeBy(60_000)

        assertTrue(cancelled.isEmpty(), "l'allerta nuova non si chiude")
        assertIs<SosState.Raised>(sos.state.value)
        assertTrue(outbox.rows.value.isEmpty())
    }

    /**
     * Se l'SOS è arrivato al server ma la risposta si è persa, annullarlo chiude anche l'allerta: la verifica in
     * background la trova e la chiude, così il soccorso non cerca una persona che ha annullato.
     */
    @Test
    fun `annullare un SOS arrivato al server con la risposta persa chiude anche l'allerta`() = runTest {
        val (sos) = controller()
        loseRaiseResponse = true
        sos.raise(here, null)
        sos.awaitState { it is SosState.Sending && it.attempts >= 1 }
        val alertId = activeOnServer!!

        assertTrue(sos.cancel().isSuccess)
        assertIs<SosState.Idle>(sos.state.value)

        testScheduler.advanceTimeBy(30_000)
        assertEquals(listOf(alertId), cancelled)
        assertNull(activeOnServer)
        assertTrue(outbox.rows.value.isEmpty())
    }

    /** Senza rete la verifica dell'annullamento continua a riprovare e chiude l'allerta quando la rete torna. */
    @Test
    fun `annullare in coda senza rete continua a verificare e chiude quando la rete torna`() = runTest {
        val (sos) = controller()
        loseRaiseResponse = true
        sos.raise(here, null)
        sos.awaitState { it is SosState.Sending && it.attempts >= 1 }
        activeOffline = true
        cancelOffline = true

        assertTrue(sos.cancel().isSuccess)
        testScheduler.advanceTimeBy(60_000)
        assertTrue(cancelled.isEmpty())

        activeOffline = false
        cancelOffline = false
        testScheduler.advanceTimeBy(60_000)
        assertEquals(1, cancelled.size)
    }

    /**
     * Un SOS non aspetta la lettura lenta dell'allerta attiva fatta all'avvio: passa subito in invio. La risposta di
     * quella lettura, arrivata dopo, non riporta indietro lo stato.
     */
    @Test
    fun `un SOS non aspetta il restore lento all'avvio`() = runTest {
        val (sos) = controller()
        activeDelayMs = 60_000
        val restoring = backgroundScope.async { sos.restore() }
        testScheduler.runCurrent()

        assertTrue(sos.raise(here, null))
        assertIs<SosState.Sending>(sos.state.value, "in invio subito, senza aspettare la rete del restore")
        testScheduler.runCurrent()
        assertEquals(1, posts.size)

        restoring.await()
        // La risposta del restore (nessuna allerta prima dell'SOS, oppure quella nuova) non deve far tornare lo stato inattivo.
        assertTrue(sos.state.value is SosState.Raised || sos.state.value is SosState.Sending)
    }

    /**
     * L'SOS parte anche se la coroutine che l'ha lanciato viene annullata subito dopo il tocco (per esempio perché la
     * schermata si chiude): l'invio vive nello scope del controller.
     */
    @Test
    fun `l'SOS parte anche se chi lo lancia viene annullato subito`() = runTest {
        val (sos) = controller()
        val screen = backgroundScope.async { sos.raise(here, null) }
        testScheduler.runCurrent()
        screen.cancel()

        sos.awaitState { it is SosState.Raised }
        assertEquals(1, created.size)
    }

    /** Una risposta fuori contratto non fa crashare l'app: l'SOS resta in coda e si ritenta finché non arriva una risposta valida. */
    @Test
    fun `una risposta fuori contratto non fa crashare e si ritenta`() = runTest {
        val (sos) = controller()
        raiseMalformed = true
        sos.raise(here, null)
        sos.awaitState { it is SosState.Sending && it.attempts >= 1 }
        assertEquals(1, outbox.rows.value.size, "resta in coda")

        raiseMalformed = false
        sos.awaitState { it is SosState.Raised }
        assertTrue(outbox.rows.value.isEmpty())
    }

    /** Una riga illeggibile nell'outbox non fa crashare il ripristino: viene tolta e lo stato mostra l'errore. */
    @Test
    fun `una riga illeggibile nell'outbox non fa crashare il restore e lo dice`() = runTest {
        val (sos) = controller()
        outbox.insert(PendingActionEntity("rotta", ActionKind.SOS, owner, "non è json", Instant.parse("2026-10-06T10:00:00Z")))

        sos.restore()

        assertIs<SosState.Failed>(sos.state.value)
        assertTrue(outbox.rows.value.isEmpty())
    }

    /**
     * Se l'allerta era stata creata ma la risposta e la riga dell'outbox sono andate perse, una push (per esempio
     * SOS_DISPATCHED) che fa rileggere lo stato porta l'allerta a schermo anche partendo dallo stato inattivo.
     */
    @Test
    fun `una push dopo una risposta persa e un riavvio porta l'allerta anche da Idle`() = runTest {
        val (sos) = controller()
        activeOnServer = "s9"
        assertIs<SosState.Idle>(sos.state.value)

        sos.refresh()

        assertEquals("s9", assertIs<SosState.Raised>(sos.state.value).alert.id)
    }


    /**
     * Il campo "sub" del payload JWT è l'id dell'utente, usato come proprietario dell'SOS quando il profilo non è
     * ancora disponibile; un token non leggibile dà null.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `il sub del JWT è l'id dell'utente e un token illeggibile dà null`() {
        val payload = Base64.UrlSafe.encode("""{"sub":"3f1c-utente","roles":["HIKER"]}""".encodeToByteArray()).trimEnd('=')

        assertEquals("3f1c-utente", jwtSubject("eyJhbGciOiJIUzI1NiJ9.$payload.firma"))
        assertNull(jwtSubject("a1"))
        assertNull(jwtSubject("x.%%%.y"))
    }


    /** Restituisce un controller con un SOS già inviato e attivo sul server. */
    private suspend fun TestScope.raised(): SosController {
        val (sos) = controller()
        sos.raise(here, null)
        sos.awaitState { it is SosState.Raised }
        return sos
    }

    /** Una posizione nuova viene inviata all'allerta attiva, e la risposta del server aggiorna lo stato mostrato. */
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

    /** Finché l'SOS è in coda non c'è ancora un id di allerta, quindi la posizione non viene inviata. */
    @Test
    fun `senza allerta attiva la posizione non parte`() = runTest {
        val (sos) = controller()
        sosOffline = true
        sos.raise(here, null)
        sos.awaitState { it is SosState.Sending }

        sos.updateLocation(here)

        assertTrue(locationPuts.isEmpty(), "in coda non c'è ancora un id")
    }

    /** Se l'invio della posizione fallisce per la rete l'allerta resta com'è. */
    @Test
    fun `posizione senza rete lascia l'allerta com'è`() = runTest {
        val sos = raised()
        locationOffline = true

        sos.updateLocation(here)

        assertEquals("s1", assertIs<SosState.Raised>(sos.state.value).alert.id)
    }

    /** Se l'operatore ha chiuso l'allerta, il server rifiuta la posizione con 422 e il controller torna inattivo. */
    @Test
    fun `allerta chiusa dal soccorso con il 422 sulla posizione si torna a Idle`() = runTest {
        val sos = raised()
        activeOnServer = null

        sos.updateLocation(here)

        assertIs<SosState.Idle>(sos.state.value)
    }

    /** La rilettura periodica dello stato mostra la squadra in arrivo anche senza ricevere una push. */
    @Test
    fun `il refresh porta la squadra in arrivo senza push`() = runTest {
        val sos = raised()
        serverStatus = "DISPATCHED"

        sos.refresh()

        assertEquals(SosAlertResponse.Status.DISPATCHED, assertIs<SosState.Raised>(sos.state.value).alert.status)
    }

    /**
     * Una rilettura fallita per la rete non cambia nulla, sia con un'allerta attiva sia dallo stato inattivo (in cui
     * il controller chiede comunque al server se esiste un'allerta).
     */
    @Test
    fun `refresh senza rete non cambia nulla`() = runTest {
        val sos = raised()
        activeOffline = true

        sos.refresh()
        assertIs<SosState.Raised>(sos.state.value)

        activeOffline = false
        assertTrue(sos.cancel().isSuccess)
        activeOffline = true
        sos.refresh()
        assertIs<SosState.Idle>(sos.state.value)
    }
}
