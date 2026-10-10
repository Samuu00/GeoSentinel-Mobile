package it.unical.geosentinel_mobile.hazard.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.trails_nearby_no_fix
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HazardMapViewModelTest {

    private val backend = FakeBackend()
    // i pericoli che il server restituisce, già dal più vicino
    private var serverHazards = listOf(hazard("h1", "CRITICAL"), hazard("h2", "LOW"))
    // le query di ogni GET /hazards/nearby, nell'ordine
    private val calls = mutableListOf<Parameters>()
    private var offline = false
    // "Le mie segnalazioni" dell'utente (GET /hazards/mine), in ogni stato
    private var mine = listOf<String>()

    // la vista sulla Sila, a zoom 12
    private val sila = GeoBounds.of(16.50, 39.30, 16.60, 39.36)

    private fun hazard(id: String, severity: String, description: String? = null, status: String = "VERIFIED"): String {
        val desc = description?.let { ""","description":"$it"""" }.orEmpty()
        return """{"id":"$id","type":"LANDSLIDE","severity":"$severity","status":"$status","location":{"lat":39.33,"lon":16.55},"createdAt":"2026-10-05T10:00:00Z"$desc}"""
    }

    private suspend fun TestScope.viewModel(): HazardMapViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        backend.extraRoutes = { request, _ ->
            if (request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/hazards/nearby") {
                calls += request.url.parameters
                if (offline) throw IOException("rete assente")
                respond("[${serverHazards.joinToString(",")}]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else if (request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/hazards/mine") {
                respond(
                    """{"content":[${mine.joinToString(",")}],"page":{"size":20,"number":0,"totalElements":${mine.size},"totalPages":1}}""",
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
        return HazardMapViewModel(HazardRepository({ client }, TEST_BASE_URL))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `da loggato la mappa ferma carica i pericoli della vista`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)

        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()

        assertEquals(listOf("h1", "h2"), viewModel.state.value.hazards.map { it.id })
        assertFalse(viewModel.state.value.failed)
        // il centro della vista, non invertito, e un raggio che la contiene
        val query = calls.single()
        assertEquals(39.33, query["lat"]!!.toDouble(), 0.001)
        assertEquals(16.55, query["lon"]!!.toDouble(), 0.001)
        assertTrue(query["radius"]!!.toDouble() > sila.radiusMeters)
    }

    @Test
    fun `le proprie segnalazioni in verifica si aggiungono al livello senza doppioni`() = runTest {
        mine = listOf(hazard("m1", "CRITICAL", status = "SUBMITTED"), hazard("h1", "CRITICAL"), hazard("m2", "LOW", status = "REJECTED"))
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()

        // h1 è già tra i verificati; la respinta non si mostra
        assertEquals(listOf("h1", "h2", "m1"), viewModel.state.value.shown.map { it.id })
        viewModel.select("m1")
        assertEquals("m1", viewModel.state.value.selected?.id)

        // una segnalazione nuova compare al ritorno sulla mappa, senza aspettare un movimento
        mine = listOf(hazard("m3", "HIGH", status = "SUBMITTED")) + mine
        viewModel.refreshMine()
        advanceUntilIdle()
        assertEquals(listOf("m3", "m1"), viewModel.state.value.mine.map { it.id })
    }

    @Test
    fun `da ospite non parte nessuna richiesta`() = runTest {
        val viewModel = viewModel()

        viewModel.onCameraIdle(sila, zoom = 12.0)
        viewModel.onNearbyRequested()
        advanceUntilIdle()

        assertTrue(calls.isEmpty())
        assertTrue(viewModel.state.value.hazards.isEmpty())
        assertEquals(NearbyHazards.Hidden, viewModel.state.value.nearby)
    }

    @Test
    fun `al login carica la vista gia ferma`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()

        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertEquals(1, calls.size)
        assertEquals(2, viewModel.state.value.hazards.size)
    }

    @Test
    fun `al logout livello selezione e foglio spariscono`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()
        viewModel.select("h1")
        viewModel.onNearbyRequested()

        viewModel.setEnabled(false)

        assertEquals(HazardMapUiState(), viewModel.state.value)
    }

    @Test
    fun `sotto lo zoom minimo niente richiesta e livello vuoto`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()

        viewModel.onCameraIdle(GeoBounds.of(15.0, 38.0, 18.0, 41.0), zoom = 7.0)
        advanceUntilIdle()

        assertEquals(1, calls.size)
        assertTrue(viewModel.state.value.hazards.isEmpty())
    }

    @Test
    fun `una vista dentro l'area gia caricata non richiama il server`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()

        viewModel.onCameraIdle(GeoBounds.of(16.52, 39.31, 16.58, 39.35), zoom = 13.0)
        advanceUntilIdle()

        assertEquals(1, calls.size)
    }

    @Test
    fun `dopo uno zoom indietro e uno avanti nella stessa area i pericoli tornano`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()
        viewModel.onCameraIdle(sila, zoom = 6.0)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.hazards.isEmpty())

        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()

        assertEquals(2, calls.size)
        assertEquals(2, viewModel.state.value.hazards.size)
    }

    @Test
    fun `movimenti rapidi fanno una sola richiesta`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)

        repeat(5) { viewModel.onCameraIdle(GeoBounds.of(16.0 + it, 39.30, 16.1 + it, 39.36), zoom = 12.0) }
        advanceUntilIdle()

        assertEquals(1, calls.size)
    }

    @Test
    fun `senza rete il livello resta vuoto con l'avviso e riprova lo ricarica`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()

        offline = true
        viewModel.onCameraIdle(GeoBounds.of(10.0, 45.0, 10.1, 45.06), zoom = 12.0)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.failed)
        assertTrue(viewModel.state.value.hazards.isEmpty())

        offline = false
        viewModel.retry()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.failed)
        assertEquals(2, viewModel.state.value.hazards.size)
    }

    @Test
    fun `pericoli vicini dalla posizione con raggio di 5 km`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        val here = GeoPoint(lat = 39.32, lon = 16.54)

        viewModel.onNearbyRequested()
        assertEquals(NearbyHazards.WaitingForFix, viewModel.state.value.nearby)
        viewModel.onNearbyFix(here)
        advanceUntilIdle()

        val loaded = assertIs<NearbyHazards.Loaded>(viewModel.state.value.nearby)
        assertEquals(listOf("h1", "h2"), loaded.hazards.map { it.id })
        assertEquals(here, loaded.from)
        val query = calls.single()
        assertEquals("39.32", query["lat"])
        assertEquals("16.54", query["lon"])
        assertEquals(5000.0, query["radius"]!!.toDouble())
    }

    @Test
    fun `pericoli vicini senza fix mostra l'errore della posizione`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)

        viewModel.onNearbyRequested()
        viewModel.onNearbyNoFix()

        assertEquals(NearbyHazards.Failed(UiMessage(Res.string.trails_nearby_no_fix)), viewModel.state.value.nearby)
    }

    @Test
    fun `pericoli vicini chiuso prima della risposta resta chiuso`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)

        viewModel.onNearbyRequested()
        viewModel.onNearbyFix(GeoPoint(lat = 39.32, lon = 16.54))
        viewModel.closeNearby()
        advanceUntilIdle()

        assertEquals(NearbyHazards.Hidden, viewModel.state.value.nearby)
    }

    @Test
    fun `pericoli vicini senza rete mostra l'errore`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        offline = true

        viewModel.onNearbyRequested()
        viewModel.onNearbyFix(GeoPoint(lat = 39.32, lon = 16.54))
        advanceUntilIdle()

        assertIs<NearbyHazards.Failed>(viewModel.state.value.nearby)
    }

    @Test
    fun `selezione dal livello e chiusura della card`() = runTest {
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()

        viewModel.select("h2")
        assertEquals("h2", viewModel.state.value.selected?.id)
        viewModel.clearSelection()
        assertNull(viewModel.state.value.selected)
    }

    @Test
    fun `lo stato nei log non ha descrizioni ne posizioni`() = runTest {
        serverHazards = listOf(hazard("h1", "HIGH", description = "Frana sotto la baita"))
        val viewModel = viewModel()
        viewModel.setEnabled(true)
        viewModel.onCameraIdle(sila, zoom = 12.0)
        advanceUntilIdle()
        viewModel.select("h1")
        viewModel.onNearbyRequested()
        viewModel.onNearbyFix(GeoPoint(lat = 39.32, lon = 16.54))
        advanceUntilIdle()

        val text = viewModel.state.value.toString()
        assertFalse("baita" in text)
        assertFalse("39.3" in text)
        assertFalse("16.5" in text)
    }

    @Test
    fun `distanze in metri sotto il chilometro e in km con la virgola`() {
        assertEquals("0 m", distanceLabel(3.0))
        assertEquals("350 m", distanceLabel(347.0))
        assertEquals("990 m", distanceLabel(994.0))
        assertEquals("1,0 km", distanceLabel(996.0))
        assertEquals("2,1 km", distanceLabel(2_140.0))
        assertEquals("12,5 km", distanceLabel(12_460.0))
    }
}
