// Test dei ViewModel del soccorso, usati dagli operatori: lista paginata delle allerte SOS aperte con rilettura
// periodica, dettaglio con invio di una squadra e chiusura dell'allerta, allerte sulla mappa.
package it.unical.geosentinel_mobile.rescue

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_alert_already_taken
import geosentinel_mobile.shared.generated.resources.error_forbidden
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.rescue.data.RescueRepository
import it.unical.geosentinel_mobile.rescue.ui.RescueAlertDetailViewModel
import it.unical.geosentinel_mobile.rescue.ui.RescueAlertsListViewModel
import it.unical.geosentinel_mobile.rescue.ui.RescueMapViewModel
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

/**
 * Verifica lista, dettaglio e mappa delle allerte contro un backend finto che applica le stesse regole del dominio
 * per l'invio della squadra e la chiusura.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RescueViewModelsTest {

    private val backend = FakeBackend()

    /** Id delle allerte aperte sul server finto, restituite a pagine. */
    private var openAlerts = (1..25).map { "a$it" }
    /** Stato dell'allerta "a1" sul server finto. */
    private var status = "ACTIVE"
    /** Squadra assegnata all'allerta "a1", null se nessuna. */
    private var team: String? = null
    /** Se vero, un altro operatore invia la sua squadra un attimo prima del test, e l'invio riceve 409. */
    private var takenByColleague = false
    /** Se vero, tutte le rotte del soccorso rispondono 403. */
    private var forbidden = false
    /** Se vero, tutte le rotte del soccorso falliscono con un errore di rete. */
    private var offline = false
    /** Registro delle richieste ricevute, nella forma "METODO percorso" con i parametri rilevanti. */
    private val calls = mutableListOf<String>()

    /** JSON di una squadra di soccorso disponibile con l'id indicato. */
    private fun teamJson(id: String) =
        """{"id":"$id","name":"Squadra $id","callSign":"AQUILA-$id","contactNumber":"+39 000","status":"AVAILABLE","baseLocation":{"lat":39.3,"lon":16.5}}"""

    /** JSON di un'allerta con stato e squadra indicati (di default quelli correnti del server finto). */
    private fun alertJson(id: String, status: String = this.status, team: String? = this.team): String {
        val assigned = team?.let { ""","assignedTeam":${teamJson(it)}""" }.orEmpty()
        return """{"id":"$id","hikerId":"h1","status":"$status","location":{"lat":39.31,"lon":16.52},""" +
            """"createdAt":"2026-10-08T10:00:00Z","locationUpdatedAt":"2026-10-08T10:05:00Z","medicalNotes":"diabete"$assigned}"""
    }

    /** Installa le rotte finte del soccorso, fa l'accesso, svuota il registro e restituisce il repository. */
    private suspend fun TestScope.repository(): RescueRepository {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        backend.extraRoutes = { request, _ ->
            val path = request.url.encodedPath.removePrefix("/api/v1")
            val ok = headersOf(HttpHeaders.ContentType, "application/json")
            calls += "${request.method.value} $path"
            if (offline) throw IOException("rete assente")
            with(backend) {
                when {
                    forbidden -> problem(HttpStatusCode.Forbidden, "forbidden")
                    request.method == HttpMethod.Get && path == "/sos" -> {
                        val page = request.url.parameters["page"]!!.toInt()
                        val size = request.url.parameters["size"]!!.toInt()
                        calls[calls.lastIndex] += "?page=$page&size=$size"
                        val content = openAlerts.drop(page * size).take(size).map { alertJson(it, "ACTIVE", null) }
                        val totalPages = (openAlerts.size + size - 1) / size
                        respond(
                            """{"content":[${content.joinToString(",")}],"page":{"size":$size,"number":$page,"totalElements":${openAlerts.size},"totalPages":$totalPages}}""",
                            HttpStatusCode.OK,
                            ok,
                        )
                    }
                    request.method == HttpMethod.Get && path == "/sos/nearby" -> {
                        calls[calls.lastIndex] += "?radius=${request.url.parameters["radius"]}"
                        respond("[${alertJson("a1")}]", HttpStatusCode.OK, ok)
                    }
                    request.method == HttpMethod.Get && path == "/sos/a1" -> respond(alertJson("a1"), HttpStatusCode.OK, ok)
                    request.method == HttpMethod.Get && path == "/rescue-teams/available-nearby" -> {
                        // Le squadre disponibili si cercano attorno alla posizione dell'allerta.
                        calls[calls.lastIndex] += "?lat=${request.url.parameters["lat"]}&lon=${request.url.parameters["lon"]}"
                        respond("[${teamJson("t1")},${teamJson("t2")}]", HttpStatusCode.OK, ok)
                    }
                    request.method == HttpMethod.Patch && path == "/sos/a1/dispatch" -> {
                        if (takenByColleague) {
                            status = "DISPATCHED"
                            team = "t9"
                        }
                        if (status == "DISPATCHED") {
                            problem(HttpStatusCode.Conflict, "alert-already-taken")
                        } else {
                            team = Regex("\"teamId\"\\s*:\\s*\"([^\"]+)\"").find(request.body.toByteArray().decodeToString())!!.groupValues[1]
                            status = "DISPATCHED"
                            respond(alertJson("a1"), HttpStatusCode.OK, ok)
                        }
                    }
                    request.method == HttpMethod.Patch && path == "/sos/a1/resolve" -> {
                        if (status != "ACTIVE" && status != "DISPATCHED") {
                            problem(HttpStatusCode.UnprocessableEntity, "business-rule")
                        } else {
                            status = "RESOLVED"
                            respond(alertJson("a1"), HttpStatusCode.OK, ok)
                        }
                    }
                    else -> null
                }
            }
        }
        val (session, client) = sessionWith(backend)
        session.restore()
        assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        calls.clear()
        return RescueRepository({ client }, TEST_BASE_URL)
    }

    /** Ripristina il dispatcher principale dopo ogni test. */
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()


    /**
     * La lista carica le allerte una pagina da 20 alla volta fino in fondo; arrivati in fondo un ulteriore
     * caricamento non fa richieste.
     */
    @Test
    fun `la lista carica le allerte una pagina alla volta fino in fondo`() = runTest {
        val viewModel = RescueAlertsListViewModel(repository())
        advanceUntilIdle()
        assertEquals(20, viewModel.state.value.items.size)
        assertFalse(viewModel.state.value.endReached)

        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals((1..25).map { "a$it" }, viewModel.state.value.items.map { it.id })
        assertTrue(viewModel.state.value.endReached)
        assertEquals(listOf("GET /sos?page=0&size=20", "GET /sos?page=1&size=20"), calls)
    }

    /**
     * La rilettura periodica riparte dalla prima pagina senza mostrare indicatori di caricamento e toglie le allerte
     * nel frattempo chiuse.
     */
    @Test
    fun `poll rilegge da capo senza indicatori e toglie le allerte chiuse`() = runTest {
        val viewModel = RescueAlertsListViewModel(repository())
        advanceUntilIdle()

        openAlerts = listOf("a2", "a3")
        viewModel.poll()
        val during = viewModel.state.value
        assertFalse(during.loading || during.refreshing || during.loadingMore)
        advanceUntilIdle()

        assertEquals(listOf("a2", "a3"), viewModel.state.value.items.map { it.id })
        assertTrue(viewModel.state.value.endReached)
    }

    /**
     * La rilettura periodica chiede in una sola richiesta tutte le allerte già a schermo (una pagina grande quanto la
     * lista), così la lista non si accorcia alla prima pagina.
     */
    @Test
    fun `poll rilegge tutte le allerte gia a schermo in una richiesta e la lista non si accorcia`() = runTest {
        val viewModel = RescueAlertsListViewModel(repository())
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        calls.clear()

        viewModel.poll()
        advanceUntilIdle()

        assertEquals((1..25).map { "a$it" }, viewModel.state.value.items.map { it.id })
        assertTrue(viewModel.state.value.endReached)
        assertEquals(listOf("GET /sos?page=0&size=40"), calls)
    }

    /** Una rilettura periodica fallita per la rete non mostra errori e non impedisce di caricare la pagina successiva. */
    @Test
    fun `poll senza rete non mostra errori e non blocca la pagina dopo`() = runTest {
        val viewModel = RescueAlertsListViewModel(repository())
        advanceUntilIdle()

        offline = true
        viewModel.poll()
        advanceUntilIdle()
        assertNull(viewModel.state.value.error)
        assertEquals(20, viewModel.state.value.items.size)

        offline = false
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(25, viewModel.state.value.items.size)
    }

    /**
     * Chiedere la pagina successiva durante una rilettura annulla la rilettura in corso: si applica una sola
     * risposta e la lista non ha buchi.
     */
    @Test
    fun `una pagina chiesta durante un poll non lascia buchi nella lista`() = runTest {
        val viewModel = RescueAlertsListViewModel(repository())
        advanceUntilIdle()

        viewModel.poll()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals((1..25).map { "a$it" }, viewModel.state.value.items.map { it.id })
        viewModel.poll()
        advanceUntilIdle()
        assertEquals((1..25).map { "a$it" }, viewModel.state.value.items.map { it.id })
    }

    /** Un 403 sulla lista (utente senza il ruolo di operatore) diventa il messaggio di permesso negato. */
    @Test
    fun `un 403 della lista diventa il messaggio del ruolo`() = runTest {
        forbidden = true
        val viewModel = RescueAlertsListViewModel(repository())
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_forbidden), viewModel.state.value.error)
        assertTrue(viewModel.state.value.items.isEmpty())
    }


    /**
     * Il dettaglio carica l'allerta, comprese le note mediche, e poi le squadre libere cercate attorno alla posizione
     * dell'allerta.
     */
    @Test
    fun `il dettaglio carica l'allerta e le squadre libere attorno alla sua posizione`() = runTest {
        val viewModel = RescueAlertDetailViewModel("a1", repository())
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals(SosAlertResponse.Status.ACTIVE, ui.alert?.status)
        assertEquals("diabete", ui.alert?.medicalNotes)
        assertEquals(listOf("t1", "t2"), ui.teams.map { it.id })
        assertEquals(listOf("GET /sos/a1", "GET /rescue-teams/available-nearby?lat=39.31&lon=16.52"), calls)
    }

    /** Se il primo caricamento fallisce, la rilettura porta sia l'allerta sia le squadre. */
    @Test
    fun `se il primo caricamento fallisce la rilettura porta anche le squadre`() = runTest {
        offline = true
        val viewModel = RescueAlertDetailViewModel("a1", repository())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loadError != null)

        offline = false
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(SosAlertResponse.Status.ACTIVE, viewModel.state.value.alert?.status)
        assertEquals(listOf("t1", "t2"), viewModel.state.value.teams.map { it.id })
    }

    /** Senza una squadra scelta l'invio non produce nessuna richiesta. */
    @Test
    fun `senza una squadra scelta l'invio non parte`() = runTest {
        val viewModel = RescueAlertDetailViewModel("a1", repository())
        advanceUntilIdle()
        calls.clear()

        viewModel.dispatch()
        advanceUntilIdle()

        assertTrue(calls.isEmpty())
    }

    /**
     * L'invio della squadra scelta porta l'allerta a DISPATCHED con quella squadra; un doppio tocco produce una sola
     * richiesta e alla fine non restano squadre da scegliere.
     */
    @Test
    fun `l'invio della squadra scelta porta l'allerta a DISPATCHED`() = runTest {
        val viewModel = RescueAlertDetailViewModel("a1", repository())
        advanceUntilIdle()

        viewModel.selectTeam("t2")
        viewModel.dispatch()
        viewModel.dispatch()
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals(SosAlertResponse.Status.DISPATCHED, ui.alert?.status)
        assertEquals("t2", ui.alert?.assignedTeam?.id)
        assertTrue(ui.teams.isEmpty())
        assertFalse(ui.busy)
        assertNull(ui.actionError)
        assertEquals(1, calls.count { it == "PATCH /sos/a1/dispatch" })
    }

    /**
     * Se un collega ha già inviato la sua squadra, il server risponde 409: compare il messaggio, il dettaglio viene
     * riletto e mostra la squadra del collega, senza più squadre da scegliere.
     */
    @Test
    fun `se un collega arriva prima si vede il messaggio e la sua squadra`() = runTest {
        val viewModel = RescueAlertDetailViewModel("a1", repository())
        advanceUntilIdle()
        takenByColleague = true

        viewModel.selectTeam("t1")
        viewModel.dispatch()
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals(UiMessage(Res.string.error_alert_already_taken), ui.actionError)
        assertEquals(SosAlertResponse.Status.DISPATCHED, ui.alert?.status)
        assertEquals("t9", ui.alert?.assignedTeam?.id)
        assertTrue(ui.teams.isEmpty())
        assertNull(ui.selectedTeamId)
        assertFalse(ui.busy)
    }

    /** La chiusura porta l'allerta a RESOLVED e segnala alla schermata di tornare indietro. */
    @Test
    fun `la chiusura porta a RESOLVED e la schermata torna indietro`() = runTest {
        status = "DISPATCHED"
        team = "t1"
        val viewModel = RescueAlertDetailViewModel("a1", repository())
        advanceUntilIdle()

        viewModel.resolve()
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertTrue(ui.resolved)
        assertEquals(SosAlertResponse.Status.RESOLVED, ui.alert?.status)
        assertTrue("PATCH /sos/a1/resolve" in calls)
    }

    /**
     * Se una rilettura parte prima dell'invio (con l'allerta ancora ACTIVE sul server), la sua risposta non deve
     * riportare indietro lo stato: vince l'invio.
     */
    @Test
    fun `una rilettura partita prima dell'invio non riporta indietro lo stato`() = runTest {
        val viewModel = RescueAlertDetailViewModel("a1", repository())
        advanceUntilIdle()

        viewModel.selectTeam("t1")
        viewModel.refresh()
        viewModel.dispatch()
        advanceUntilIdle()

        assertEquals(SosAlertResponse.Status.DISPATCHED, viewModel.state.value.alert?.status)
    }


    /**
     * Le allerte compaiono sulla mappa solo quando il livello è abilitato, e il raggio della richiesta non supera il
     * limite di 100 km del server anche se la vista è più ampia. A mappa ferma l'aggiornamento rilegge la stessa
     * vista anche dentro l'area già caricata; disabilitando il livello le allerte spariscono.
     */
    @Test
    fun `le allerte sulla mappa solo se abilitate e con il raggio entro il limite del server`() = runTest {
        val viewModel = RescueMapViewModel(repository())
        val wide = GeoBounds.of(15.5, 38.5, 17.5, 40.0)

        viewModel.onCameraIdle(wide, 9.0)
        advanceUntilIdle()
        assertTrue(calls.isEmpty())
        assertTrue(viewModel.state.value.isEmpty())

        viewModel.setEnabled(true)
        advanceUntilIdle()
        assertEquals(listOf("a1"), viewModel.state.value.map { it.id })
        assertEquals(listOf("GET /sos/nearby?radius=100000.0"), calls)

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, calls.size)

        viewModel.setEnabled(false)
        assertTrue(viewModel.state.value.isEmpty())
    }
}
