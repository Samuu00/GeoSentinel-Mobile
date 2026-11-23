// Test di MyHazardsViewModel, il ViewModel della schermata "Le mie segnalazioni" nel profilo: lista paginata delle
// segnalazioni dell'utente, caricamento della pagina successiva, errori di rete, aggiornamento trascinando in basso.
package it.unical.geosentinel_mobile.hazard.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_network
import geosentinel_mobile.shared.generated.resources.my_hazards_status_unknown
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
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

/** Verifica la paginazione delle segnalazioni dell'utente contro una rotta finta che serve pagine da 20 elementi. */
@OptIn(ExperimentalCoroutinesApi::class)
class MyHazardsViewModelTest {

    private val backend = FakeBackend()
    /** Le segnalazioni dell'utente sul server, dalla più recente alla più vecchia. */
    private var mine = (1..25).map { hazard("h$it") }
    /** Numeri delle pagine richieste al server, nell'ordine. */
    private val pagesRequested = mutableListOf<Int>()
    /** Pagine la cui richiesta fallisce con un errore di rete. */
    private val offlinePages = mutableSetOf<Int>()

    /** JSON di una segnalazione con l'id, lo stato e la descrizione facoltativa indicati. */
    private fun hazard(id: String, status: String = "SUBMITTED", description: String? = null): String {
        val desc = description?.let { ""","description":"$it"""" }.orEmpty()
        return """{"id":"$id","type":"FALLEN_TREE","severity":"HIGH","status":"$status","location":{"lat":39.3,"lon":16.5},"createdAt":"2026-10-05T10:00:00Z"$desc}"""
    }

    /**
     * Installa la rotta finta GET /hazards/mine, che pagina la lista [mine] secondo i parametri page e size, fa
     * l'accesso e restituisce il ViewModel.
     */
    private suspend fun TestScope.viewModel(): MyHazardsViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        backend.extraRoutes = { request, _ ->
            if (request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/hazards/mine") {
                val page = request.url.parameters["page"]!!.toInt()
                val size = request.url.parameters["size"]!!.toInt()
                pagesRequested += page
                if (page in offlinePages) throw IOException("rete assente")
                val content = mine.drop(page * size).take(size)
                val totalPages = (mine.size + size - 1) / size
                respond(
                    """{"content":[${content.joinToString(",")}],"page":{"size":$size,"number":$page,"totalElements":${mine.size},"totalPages":$totalPages}}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                null
            }
        }
        val (session, client) = sessionWith(backend)
        session.restore()
        assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        return MyHazardsViewModel(HazardRepository({ client }, TEST_BASE_URL))
    }

    /** Ripristina il dispatcher principale dopo ogni test. */
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** All'apertura si carica la prima pagina (20 segnalazioni, dalla più recente) e la lista non risulta finita. */
    @Test
    fun `all'apertura carica la prima pagina dalla piu recente`() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.loading)

        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals(20, ui.items.size)
        assertEquals("h1", ui.items.first().id)
        assertFalse(ui.loading)
        assertFalse(ui.endReached)
        assertEquals(listOf(0), pagesRequested)
    }

    /**
     * Arrivati in fondo si carica la seconda pagina, senza doppioni; dopo l'ultima pagina la lista è finita e un
     * ulteriore caricamento non fa richieste.
     */
    @Test
    fun `in fondo arriva la seconda pagina senza doppioni e poi la lista e finita`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals((1..25).map { "h$it" }, ui.items.map { it.id })
        assertTrue(ui.endReached)
        assertEquals(listOf(0, 1), pagesRequested)
    }

    /** Due richieste ravvicinate della pagina successiva producono una sola richiesta al server. */
    @Test
    fun `due richieste di fila chiedono la pagina dopo una volta sola`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0, 1), pagesRequested)
    }

    /**
     * Se nel frattempo l'utente ha inviato una nuova segnalazione, l'ultima della prima pagina scivola nella seconda:
     * la lista unita non deve contenere la stessa riga due volte.
     */
    @Test
    fun `una segnalazione nuova in cima non duplica la riga che scivola nella pagina dopo`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        mine = listOf(hazard("nuova")) + mine

        viewModel.loadMore()
        advanceUntilIdle()

        val ids = viewModel.state.value.items.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(25, ids.size)
    }

    /** Senza rete la prima pagina mostra l'errore di rete; "Riprova" la ricarica quando la rete torna. */
    @Test
    fun `senza rete la prima pagina da l'errore e Riprova la ricarica`() = runTest {
        offlinePages += 0
        val viewModel = viewModel()
        advanceUntilIdle()

        val failed = viewModel.state.value
        assertTrue(failed.items.isEmpty())
        assertFalse(failed.loading)
        assertEquals(UiMessage(Res.string.error_network), failed.error)

        offlinePages.clear()
        viewModel.retry()
        assertTrue(viewModel.state.value.loading)
        advanceUntilIdle()

        assertEquals(20, viewModel.state.value.items.size)
        assertNull(viewModel.state.value.error)
    }

    /**
     * Se fallisce la pagina successiva le righe già caricate restano, e il ViewModel non riprova da solo: con
     * l'errore in vista serve "Riprova". La richiesta GET è idempotente, quindi i tentativi automatici su un errore
     * di rete li fa già il client HTTP.
     */
    @Test
    fun `senza rete sulla pagina dopo le righe restano e non si riprova da soli`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        offlinePages += 1

        viewModel.loadMore()
        advanceUntilIdle()
        val afterFailure = pagesRequested.size
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(20, viewModel.state.value.items.size)
        assertEquals(UiMessage(Res.string.error_network), viewModel.state.value.error)
        assertEquals(afterFailure, pagesRequested.size)
        assertEquals(listOf(0, 1), pagesRequested.distinct())

        offlinePages.clear()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(25, viewModel.state.value.items.size)
        assertNull(viewModel.state.value.error)
    }

    /** Trascinando in basso la lista riparte dalla prima pagina e sostituisce le righe con quelle aggiornate. */
    @Test
    fun `trascinando in basso la lista riparte dalla prima pagina`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        mine = listOf(hazard("nuova", status = "VERIFIED")) + mine.take(2)

        viewModel.refresh()
        assertTrue(viewModel.state.value.refreshing)
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals(listOf("nuova", "h1", "h2"), ui.items.map { it.id })
        assertFalse(ui.refreshing)
        assertTrue(ui.endReached)
    }

    /** Senza segnalazioni la lista è vuota, finita e senza errori. */
    @Test
    fun `nessuna segnalazione da una lista vuota e finita`() = runTest {
        mine = emptyList()
        val viewModel = viewModel()
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertTrue(ui.items.isEmpty())
        assertTrue(ui.endReached)
        assertNull(ui.error)
    }

    /** Uno stato sconosciuto inviato dal server non fa crashare l'app e viene mostrato come "non disponibile". */
    @Test
    fun `uno stato nuovo del server non fa crashare e si legge come non disponibile`() = runTest {
        mine = listOf(hazard("h1", status = "ARCHIVED"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val status = viewModel.state.value.items.single().status
        assertEquals(HazardResponse.Status.unknown_default_open_api, status)
        assertEquals(Res.string.my_hazards_status_unknown, status.label())
    }

    /** Il toString dello stato non contiene le descrizioni né le coordinate delle segnalazioni, che sono dati personali. */
    @Test
    fun `lo stato nei log non contiene le descrizioni`() = runTest {
        mine = listOf(hazard("h1", description = "vicino alla baita di Mario"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val text = viewModel.state.value.toString()
        assertFalse("Mario" in text, text)
        assertFalse("39.3" in text, text)
    }
}
