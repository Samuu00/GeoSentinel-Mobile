// Test dei ViewModel dei ticket di manutenzione (lista, dettaglio, creazione). Le modifiche ai ticket usano il
// controllo di concorrenza ottimistico: il client invia in If-Match l'ETag letto, e il server rifiuta con 409 se nel
// frattempo qualcun altro ha cambiato il ticket.
package it.unical.geosentinel_mobile.staff

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_concurrent_modification
import geosentinel_mobile.shared.generated.resources.ticket_missing_version
import geosentinel_mobile.shared.generated.resources.ticket_rule_violation
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.MaintenanceApi
import it.unical.geosentinel_mobile.api.model.CreateTicketRequest
import it.unical.geosentinel_mobile.api.model.TicketResponse
import it.unical.geosentinel_mobile.api.model.UpdateTicketRequest
import it.unical.geosentinel_mobile.api.model.UpdateTicketStatusRequest
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.staff.data.TicketRepository
import it.unical.geosentinel_mobile.staff.ui.TicketCreateViewModel
import it.unical.geosentinel_mobile.staff.ui.TicketDetailViewModel
import it.unical.geosentinel_mobile.staff.ui.TicketListViewModel
import it.unical.geosentinel_mobile.trail.SEED_TRAIL_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifica i ViewModel dei ticket contro un backend finto che tiene un solo ticket con un numero di versione, come
 * il server vero.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TicketViewModelsTest {

    private val backend = FakeBackend()

    /** Versione corrente del ticket sul server finto, restituita come ETag. */
    private var version = 0L
    /** Stato corrente del ticket sul server finto. */
    private var status = "OPEN"
    /** Titolo corrente del ticket sul server finto. */
    private var title = "Staccionata rotta"
    /** Priorità corrente del ticket sul server finto. */
    private var priority = "MEDIUM"
    /** Id dell'operatore a cui è assegnato il ticket, null se non è assegnato. */
    private var assignedTo: String? = null
    /** Valori di If-Match ricevuti dalle richieste di modifica, nell'ordine. */
    private val ifMatches = mutableListOf<String?>()
    /** Query string delle ricerche dei ticket ricevute. */
    private val searchQueries = mutableListOf<String>()
    /** Corpi delle richieste di creazione ricevute. */
    private val createBodies = mutableListOf<String>()
    /** Se falso, le risposte arrivano senza ETag, come se un proxy lo togliesse. */
    private var sendEtag = true
    /** Se vero, la ricerca dei ticket fallisce con un errore di rete. */
    private var searchOffline = false

    /** JSON del ticket con lo stato corrente del server finto. */
    private fun ticketJson() =
        """{"id":"t1","title":"$title","priority":"$priority","status":"$status","trailId":"$SEED_TRAIL_ID","trailName":"Sentiero",""" +
            (assignedTo?.let { "\"assignedTo\":\"$it\"," } ?: "") + """"createdAt":"2026-10-05T10:00:00Z"}"""

    /** Simula un collega che modifica il ticket: cambia il titolo e la versione aumenta. */
    private fun colleagueEdits() {
        title = "Staccionata rotta (aggiornato)"
        version++
    }

    /** Header delle risposte con il ticket: tipo JSON e, se sendEtag è vero, l'ETag con la versione corrente. */
    private fun versioned() = if (sendEtag) {
        headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"$version\""))
    } else {
        headersOf(HttpHeaders.ContentType, "application/json")
    }

    /**
     * Installa le rotte finte dei ticket, fa l'accesso e restituisce il repository dei ticket e la sessione. Le rotte
     * di modifica richiedono If-Match uguale alla versione corrente: senza rispondono 428, con una versione vecchia 409.
     */
    private suspend fun TestScope.setUp(): Pair<TicketRepository, SessionManager> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        backend.extraRoutes = { request, _ ->
            val path = request.url.encodedPath.removePrefix("/api/v1/maintenance-tickets")
            suspend fun body() = request.body.toByteArray().decodeToString()
            fun field(body: String, name: String) = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            with(backend) {
                when {
                    !request.url.encodedPath.startsWith("/api/v1/maintenance-tickets") -> null
                    request.method == HttpMethod.Get && path.isEmpty() -> {
                        if (searchOffline) throw kotlinx.io.IOException("rete assente")
                        searchQueries += request.url.encodedQuery
                        respond(
                            """{"content":[${ticketJson()}],"page":{"size":50,"number":0,"totalElements":1,"totalPages":1}}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    request.method == HttpMethod.Post && path.isEmpty() -> {
                        createBodies += body()
                        respond(ticketJson(), HttpStatusCode.Created, versioned())
                    }
                    request.method == HttpMethod.Get && path == "/t1" -> respond(ticketJson(), HttpStatusCode.OK, versioned())
                    else -> {
                        // Modifiche: If-Match è obbligatorio e deve coincidere con la versione attuale.
                        val ifMatch = request.headers[HttpHeaders.IfMatch]
                        ifMatches += ifMatch
                        val body = body()
                        when {
                            ifMatch == null -> problem(HttpStatusCode.PreconditionRequired, "precondition-required")
                            ifMatch.trim('"') != version.toString() -> problem(HttpStatusCode.Conflict, "concurrent-modification")
                            path == "/t1" -> {
                                title = field(body, "title")!!
                                priority = field(body, "priority")!!
                                version++
                                respond(ticketJson(), HttpStatusCode.OK, versioned())
                            }
                            path == "/t1/assign" -> {
                                assignedTo = field(body, "operatorId")
                                if (status == "OPEN") status = "ASSIGNED"
                                version++
                                respond(ticketJson(), HttpStatusCode.OK, versioned())
                            }
                            path == "/t1/status" -> {
                                val target = field(body, "status")!!
                                // Come nel dominio del backend: un ticket si risolve solo se è ASSIGNED o IN_PROGRESS.
                                if (target == "RESOLVED" && status !in setOf("ASSIGNED", "IN_PROGRESS")) {
                                    problem(HttpStatusCode.UnprocessableEntity, "business-rule")
                                } else {
                                    status = target
                                    version++
                                    respond(ticketJson(), HttpStatusCode.OK, versioned())
                                }
                            }
                            else -> null
                        }
                    }
                }
            }
        }
        val (session, client) = sessionWith(backend)
        session.restore()
        assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        return TicketRepository({ client }, TEST_BASE_URL) to session
    }

    /** Ripristina il dispatcher principale dopo ogni test. */
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** "Assegna a me" invia in If-Match l'ETag letto, assegna il ticket all'utente e salva il nuovo ETag restituito. */
    @Test
    fun `assegna a me manda l'ETag letto e salva quello nuovo`() = runTest {
        val (tickets, session) = setUp()
        val viewModel = TicketDetailViewModel("t1", tickets, session)
        advanceUntilIdle()
        assertEquals("\"0\"", viewModel.state.value.etag)

        viewModel.assignToMe()
        advanceUntilIdle()

        assertEquals(listOf<String?>("\"0\""), ifMatches)
        val state = viewModel.state.value
        assertEquals(TicketResponse.Status.ASSIGNED, state.ticket?.status)
        assertEquals(state.me, state.ticket?.assignedTo)
        assertEquals("\"1\"", state.etag)
        assertNull(state.actionError)
    }

    /**
     * Se un collega ha modificato il ticket, il salvataggio su una versione vecchia riceve 409: compare il messaggio,
     * si rilegge la versione del collega, la modifica in corso si chiude senza sovrascrivere quella del collega e
     * un'azione successiva, sulla versione nuova, riesce.
     */
    @Test
    fun `con una versione vecchia il 409 mostra il messaggio e la versione nuova`() = runTest {
        val (tickets, session) = setUp()
        val viewModel = TicketDetailViewModel("t1", tickets, session)
        advanceUntilIdle()
        viewModel.startEdit()
        viewModel.onDraftChange(viewModel.state.value.draft!!.copy(title = "Il mio titolo", priority = UpdateTicketRequest.Priority.HIGH))
        colleagueEdits()

        viewModel.saveDraft()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(Res.string.error_concurrent_modification, state.actionError?.text)
        assertEquals("Staccionata rotta (aggiornato)", state.ticket?.title, "si vede la versione del collega")
        assertEquals("\"1\"", state.etag)
        assertNull(state.draft, "la modifica fatta su dati vecchi si chiude")
        assertEquals("MEDIUM", priority, "la modifica non ha sovrascritto quella del collega")

        // Il secondo tentativo parte dalla versione appena riletta e passa.
        viewModel.changeStatus(UpdateTicketStatusRequest.Status.IN_PROGRESS)
        advanceUntilIdle()
        assertEquals(TicketResponse.Status.IN_PROGRESS, viewModel.state.value.ticket?.status)
        assertNull(viewModel.state.value.actionError)
    }

    /** Un cambio di stato vietato dalle regole (risolvere un ticket aperto) diventa un messaggio specifico del ticket. */
    @Test
    fun `una transizione non permessa diventa un messaggio del ticket`() = runTest {
        val (tickets, session) = setUp()
        val viewModel = TicketDetailViewModel("t1", tickets, session)
        advanceUntilIdle()

        viewModel.changeStatus(UpdateTicketStatusRequest.Status.RESOLVED)
        advanceUntilIdle()

        assertEquals(Res.string.ticket_rule_violation, viewModel.state.value.actionError?.text)
        assertEquals("OPEN", status)
        assertFalse(viewModel.state.value.busy)
    }

    /**
     * Un titolo con meno di 3 caratteri (il minimo del server) disabilita il salvataggio e non parte nessuna richiesta;
     * un titolo valido crea il ticket sul sentiero, senza spazi ai lati e senza inviare una descrizione vuota.
     */
    @Test
    fun `il titolo troppo corto non parte e quello valido apre il ticket sul sentiero`() = runTest {
        val (tickets, _) = setUp()
        val viewModel = TicketCreateViewModel(SEED_TRAIL_ID, tickets)

        viewModel.onTitleChange("  ab ")
        assertFalse(viewModel.state.value.canSave, "il server vuole almeno 3 caratteri")
        viewModel.save()
        advanceUntilIdle()
        assertTrue(createBodies.isEmpty())

        viewModel.onTitleChange(" Staccionata rotta ")
        viewModel.onPriorityChange(CreateTicketRequest.Priority.HIGH)
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.created)
        val sent = createBodies.single()
        assertTrue("\"title\":\"Staccionata rotta\"" in sent, sent)
        assertTrue("\"trailId\":\"$SEED_TRAIL_ID\"" in sent, sent)
        assertTrue("\"priority\":\"HIGH\"" in sent, sent)
        assertFalse("description" in sent, "una descrizione vuota non si manda: $sent")
    }

    /** I filtri di stato e priorità finiscono nella ricerca, e "solo i miei" filtra con l'id dell'utente. */
    @Test
    fun `i filtri finiscono nella ricerca e solo i miei usa l'id dell'utente`() = runTest {
        val (tickets, session) = setUp()
        val viewModel = TicketListViewModel(tickets, session)
        advanceUntilIdle()

        viewModel.onStatusChange(MaintenanceApi.StatusSearchTickets.IN_PROGRESS)
        viewModel.onPriorityChange(MaintenanceApi.PrioritySearchTickets.HIGH)
        viewModel.onMineChange(true)
        advanceUntilIdle()

        val last = searchQueries.last()
        assertTrue("status=IN_PROGRESS" in last, last)
        assertTrue("priority=HIGH" in last, last)
        assertTrue("assignedTo=${session.currentUserId()!!.encodeURLParameter()}" in last, last)
        assertEquals(listOf("t1"), viewModel.state.value.tickets.map { it.id })
        assertFalse(viewModel.state.value.loading)
    }

    /** Se la rilettura della lista fallisce i ticket già letti restano visibili con l'errore sopra. */
    @Test
    fun `se la rilettura fallisce la lista resta con l'errore sopra`() = runTest {
        val (tickets, session) = setUp()
        val viewModel = TicketListViewModel(tickets, session)
        advanceUntilIdle()
        searchOffline = true

        viewModel.reload()
        advanceUntilIdle()

        assertEquals(listOf("t1"), viewModel.state.value.tickets.map { it.id }, "i ticket già letti restano")
        assertTrue(viewModel.state.value.error != null)
        assertFalse(viewModel.state.value.loading)
    }

    /**
     * Se il ticket è arrivato senza ETag il client non può indicare la versione: l'azione non invia nessuna richiesta
     * e mostra un messaggio che spiega il problema.
     */
    @Test
    fun `senza ETag il tocco non manda nulla ma lo dice e rilegge`() = runTest {
        sendEtag = false
        val (tickets, session) = setUp()
        val viewModel = TicketDetailViewModel("t1", tickets, session)
        advanceUntilIdle()
        assertNull(viewModel.state.value.etag)

        viewModel.assignToMe()
        advanceUntilIdle()

        assertTrue(ifMatches.isEmpty(), "nessuna modifica senza versione")
        assertEquals(Res.string.ticket_missing_version, viewModel.state.value.actionError?.text)
        assertFalse(viewModel.state.value.busy)
    }
}
