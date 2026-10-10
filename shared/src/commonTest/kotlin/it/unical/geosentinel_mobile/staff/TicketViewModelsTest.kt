package it.unical.geosentinel_mobile.staff

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_concurrent_modification
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

/** M8.2: ticket con If-Match contro un backend finto che tiene la versione come il server vero. */
@OptIn(ExperimentalCoroutinesApi::class)
class TicketViewModelsTest {

    private val backend = FakeBackend()

    // ---- stato del server finto: un ticket ----
    private var version = 0L
    private var status = "OPEN"
    private var title = "Staccionata rotta"
    private var priority = "MEDIUM"
    private var assignedTo: String? = null
    private val ifMatches = mutableListOf<String?>()
    private val searchQueries = mutableListOf<String>()
    private val createBodies = mutableListOf<String>()

    private fun ticketJson() =
        """{"id":"t1","title":"$title","priority":"$priority","status":"$status","trailId":"$SEED_TRAIL_ID","trailName":"Sentiero",""" +
            (assignedTo?.let { "\"assignedTo\":\"$it\"," } ?: "") + """"createdAt":"2026-10-05T10:00:00Z"}"""

    // un collega modifica il ticket: la versione sale
    private fun colleagueEdits() {
        title = "Staccionata rotta (aggiornato)"
        version++
    }

    private fun versioned() = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"$version\""))

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
                        // le modifiche: If-Match obbligatorio e uguale alla versione attuale
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
                                // come il dominio: si risolve solo da ASSIGNED o IN_PROGRESS
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

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

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

        // il secondo tentativo, sulla versione nuova, passa
        viewModel.changeStatus(UpdateTicketStatusRequest.Status.IN_PROGRESS)
        advanceUntilIdle()
        assertEquals(TicketResponse.Status.IN_PROGRESS, viewModel.state.value.ticket?.status)
        assertNull(viewModel.state.value.actionError)
    }

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
}
