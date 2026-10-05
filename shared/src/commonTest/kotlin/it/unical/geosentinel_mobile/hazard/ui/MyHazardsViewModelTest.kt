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

@OptIn(ExperimentalCoroutinesApi::class)
class MyHazardsViewModelTest {

    private val backend = FakeBackend()
    // le segnalazioni dell'utente sul server, dalla più recente
    private var mine = (1..25).map { hazard("h$it") }
    // pagine chieste, nell'ordine
    private val pagesRequested = mutableListOf<Int>()
    // pagine che cadono per la rete
    private val offlinePages = mutableSetOf<Int>()

    private fun hazard(id: String, status: String = "SUBMITTED", description: String? = null): String {
        val desc = description?.let { ""","description":"$it"""" }.orEmpty()
        return """{"id":"$id","type":"FALLEN_TREE","severity":"HIGH","status":"$status","location":{"lat":39.3,"lon":16.5},"createdAt":"2026-10-05T10:00:00Z"$desc}"""
    }

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

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

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

    @Test
    fun `in fondo arriva la seconda pagina senza doppioni e poi la lista e finita`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore() // già in fondo: nessuna richiesta
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals((1..25).map { "h$it" }, ui.items.map { it.id })
        assertTrue(ui.endReached)
        assertEquals(listOf(0, 1), pagesRequested)
    }

    @Test
    fun `due richieste di fila chiedono la pagina dopo una volta sola`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0, 1), pagesRequested)
    }

    @Test
    fun `una segnalazione nuova in cima non duplica la riga che scivola nella pagina dopo`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        // nel frattempo l'utente ne ha fatta un'altra: h20 passa dalla prima alla seconda pagina
        mine = listOf(hazard("nuova")) + mine

        viewModel.loadMore()
        advanceUntilIdle()

        val ids = viewModel.state.value.items.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(25, ids.size)
    }

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

    @Test
    fun `senza rete sulla pagina dopo le righe restano e non si riprova da soli`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        offlinePages += 1

        viewModel.loadMore()
        advanceUntilIdle()
        // la GET è idempotente: il client ripete già da solo sugli errori di rete (RetryPolicy)
        val afterFailure = pagesRequested.size
        viewModel.loadMore() // con l'errore in vista serve "Riprova"
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

    @Test
    fun `uno stato nuovo del server non fa crashare e si legge come non disponibile`() = runTest {
        mine = listOf(hazard("h1", status = "ARCHIVED"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val status = viewModel.state.value.items.single().status
        assertEquals(HazardResponse.Status.unknown_default_open_api, status)
        assertEquals(Res.string.my_hazards_status_unknown, status.label())
    }

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
