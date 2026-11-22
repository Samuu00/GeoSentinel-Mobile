package it.unical.geosentinel_mobile.trail.ui

import geosentinel_mobile.shared.generated.resources.route_none
import geosentinel_mobile.shared.generated.resources.Res
import io.ktor.http.decodeURLQueryComponent
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.toPositions
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.trail.FakeCatalog
import it.unical.geosentinel_mobile.trail.FakeCatalogDao
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import it.unical.geosentinel_mobile.trail.trailJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrailMapViewModelTest {

    private val backend = FakeBackend()
    private val catalog = FakeCatalog(backend).also { it.install() }
    private val dao = FakeCatalogDao()

    // La vista sulla Sila, come la manda la mappa a zoom 10
    private val sila = GeoBounds.of(16.40, 39.25, 16.60, 39.40)

    // Ospite: sentieri e POI sono pubblici. viewModelScope usa Dispatchers.Main, sostituito da quello del test.
    private suspend fun TestScope.viewModel(): TrailMapViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (session, client) = sessionWith(backend)
        session.restore()
        return TrailMapViewModel(TrailRepository({ client }, TEST_BASE_URL, dao, converting = StandardTestDispatcher(testScheduler)))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun bboxValues(query: String): List<Double> =
        query.removePrefix("bbox=").decodeURLQueryComponent().split(",").map { it.toDouble() }

    @Test
    fun `la mappa ferma carica i sentieri dopo il debounce e con il percorso in lon lat`() = runTest {
        val viewModel = viewModel()

        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceTimeBy(300)
        assertEquals(0, catalog.count("bbox"), "prima dei 400 ms non parte niente")
        advanceUntilIdle()

        assertEquals(1, catalog.count("bbox"))
        val trail = viewModel.state.value.trails.single()
        val start = trail.path.toPositions().first()
        assertEquals(16.4852, start.longitude)
        assertEquals(39.2934, start.latitude)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `movimenti ravvicinati fanno una sola richiesta con l'ultima vista e un margine`() = runTest {
        val viewModel = viewModel()

        viewModel.onCameraIdle(GeoBounds.of(10.0, 40.0, 11.0, 41.0), zoom = 10.0)
        advanceTimeBy(200)
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()

        assertEquals(1, catalog.count("bbox"))
        // ordine del backend: minLon, minLat, maxLon, maxLat; la vista allargata di metà per lato
        val (minLon, minLat, maxLon, maxLat) = bboxValues(catalog.queries("bbox").single())
        assertEquals(16.30, minLon, 1e-9)
        assertEquals(39.175, minLat, 1e-9)
        assertEquals(16.70, maxLon, 1e-9)
        assertEquals(39.475, maxLat, 1e-9)
    }

    @Test
    fun `una vista dentro l'area già caricata non chiede di nuovo e una fuori sì`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()

        viewModel.onCameraIdle(GeoBounds.of(16.45, 39.30, 16.55, 39.35), zoom = 11.0)
        advanceUntilIdle()
        assertEquals(1, catalog.count("bbox"))

        viewModel.onCameraIdle(GeoBounds.of(16.90, 39.25, 17.10, 39.40), zoom = 10.0)
        advanceUntilIdle()
        assertEquals(2, catalog.count("bbox"))
    }

    @Test
    fun `una vista oltre i 50 km chiede solo la parte dentro e una volta sola`() = runTest {
        val viewModel = viewModel()
        val limit = GeoBounds.around(GeoPoint(39.3, 16.5), TrailMapViewModel.NearRadiusMeters)
        // supera il bordo est del limite
        val view = GeoBounds.of(16.9, 39.2, 17.3, 39.4)

        viewModel.onCameraIdle(view, zoom = 10.0, limit = limit)
        advanceUntilIdle()
        viewModel.onCameraIdle(view, zoom = 10.0, limit = limit)
        advanceUntilIdle()

        assertEquals(1, catalog.count("bbox"), "l'area caricata è ritagliata come la vista: la seconda volta è coperta")
        val (_, _, maxLon, _) = bboxValues(catalog.queries("bbox").single())
        assertTrue(maxLon <= limit.maxLon + 1e-6)
    }

    @Test
    fun `fuori dai 50 km non si mostra e non si chiede nulla e la cache si svuota`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()
        assertEquals(1, dao.summaries.value.size)

        // l'utente è a Roma: la Sila è a più di 50 km
        val limit = GeoBounds.around(GeoPoint(41.9, 12.5), TrailMapViewModel.NearRadiusMeters)
        viewModel.onCameraIdle(sila, zoom = 10.0, limit = limit)
        advanceUntilIdle()

        assertEquals(1, catalog.count("bbox"))
        assertTrue(viewModel.state.value.trails.isEmpty())
        assertTrue(dao.summaries.value.isEmpty(), "la cache tiene solo quello entro 50 km")
    }

    @Test
    fun `con il massimo dei sentieri l'area non è completa e si avvisa`() = runTest {
        catalog.trails = (1..TrailRepository.MAX_BBOX_RESULTS).map { trailJson(id = "t$it") }
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.truncated)
        viewModel.onCameraIdle(GeoBounds.of(16.45, 39.30, 16.55, 39.35), zoom = 11.0)
        advanceUntilIdle()
        assertEquals(2, catalog.count("bbox"), "dentro un'area incompleta si chiede di nuovo")
    }

    @Test
    fun `senza rete restano i sentieri di prima e riprova li ricarica`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()

        // fuori dall'area già caricata (si chiede al server) ma con il sentiero ancora in vista
        catalog.offline = true
        viewModel.onCameraIdle(GeoBounds.of(16.45, 39.20, 16.75, 39.45), zoom = 10.0)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.failed)
        assertEquals(1, viewModel.state.value.trails.size, "senza rete si vedono quelli salvati")

        catalog.offline = false
        viewModel.retry()
        advanceUntilIdle()
        assertNull(viewModel.state.value.failed)
        assertEquals(2, catalog.count("bbox"))
    }

    @Test
    fun `da lontano non si chiede niente e si invita ad avvicinarsi`() = runTest {
        val viewModel = viewModel()

        viewModel.onCameraIdle(GeoBounds.of(6.0, 36.0, 19.0, 47.0), zoom = 5.0)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.zoomTooLow)
        assertEquals(0, catalog.count("bbox"))
    }

    @Test
    fun `i POI si chiedono solo da vicino`() = runTest {
        val viewModel = viewModel()
        // sotto la soglia dei POI ma sopra quella dei sentieri
        viewModel.onCameraIdle(sila, zoom = TrailMapViewModel.MinPoiZoom - 0.5)
        advanceUntilIdle()
        assertEquals(0, catalog.count("pois-nearby"))

        viewModel.onCameraIdle(GeoBounds.of(16.48, 39.29, 16.50, 39.30), zoom = 14.0)
        advanceUntilIdle()
        assertEquals(1, catalog.count("pois-nearby"))
        assertEquals("p1", viewModel.state.value.pois.single().id)
    }

    @Test
    fun `sentieri vicini con la posizione nell'ordine giusto`() = runTest {
        val viewModel = viewModel()

        viewModel.onNearbyRequested()
        assertEquals(NearbyTrails.WaitingForFix, viewModel.state.value.nearby)
        viewModel.onNearbyFix(GeoPoint(lat = 39.33, lon = 16.45))
        advanceUntilIdle()

        assertIs<NearbyTrails.Loaded>(viewModel.state.value.nearby)
        val query = catalog.queries("nearby").single()
        assertTrue("lon=16.45" in query && "lat=39.33" in query, "lon e lat non si scambiano")
    }

    @Test
    fun `chiuso il foglio la risposta in arrivo non lo riapre`() = runTest {
        val viewModel = viewModel()

        viewModel.onNearbyFix(GeoPoint(lat = 39.33, lon = 16.45))
        viewModel.closeNearby()
        advanceUntilIdle()

        assertEquals(NearbyTrails.Hidden, viewModel.state.value.nearby)
    }

    @Test
    fun `senza fix l'errore compare solo se si stava aspettando`() = runTest {
        val viewModel = viewModel()

        viewModel.onNearbyNoFix()
        assertEquals(NearbyTrails.Hidden, viewModel.state.value.nearby)
        viewModel.onNearbyRequested()
        viewModel.onNearbyNoFix()
        assertIs<NearbyTrails.Failed>(viewModel.state.value.nearby)
    }

    @Test
    fun `un movimento che annulla la richiesta non lascia Carico per sempre`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()

        // fuori dall'area caricata: la richiesta parte e resta in volo
        catalog.bboxDelayMillis = 5_000
        viewModel.onCameraIdle(GeoBounds.of(16.90, 39.25, 17.10, 39.40), zoom = 10.0)
        advanceTimeBy(1_000)
        assertTrue(viewModel.state.value.loading)

        // si torna nell'area già caricata prima della risposta
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `da lontano restano quelli in cache e non si chiede al server`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.trails.size)

        // sotto la soglia li nasconde la mappa, in dissolvenza (TrailMapLayers): la lista resta, la rete no
        viewModel.onCameraIdle(GeoBounds.of(6.0, 36.0, 19.0, 47.0), zoom = TrailMapViewModel.MinTrailZoom - 2)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.trails.size)
        assertEquals(1, catalog.count("bbox"))
    }

    // ---- Percorso A→B ----

    private val rifugio = Place("Rifugio", GeoPoint(lat = 39.31, lon = 16.52))

    @Test
    fun `il percorso parte dal fix e chiede al server partenza e arrivo in lon lat`() = runTest {
        val viewModel = viewModel()
        viewModel.routeTo(rifugio)
        assertEquals(RouteResult.WaitingForOrigin, viewModel.state.value.route?.result)
        assertEquals(RouteOrigin.MyLocation, viewModel.state.value.route?.origin)

        viewModel.onRouteOrigin(GeoPoint(lat = 39.30, lon = 16.45))
        advanceUntilIdle()

        val found = assertIs<RouteResult.Found>(viewModel.state.value.route?.result)
        assertEquals(4210.0, found.lengthM)
        assertEquals(2, found.trailCount)
        assertEquals("fromLon=16.45&fromLat=39.3&toLon=16.52&toLat=39.31", catalog.queries("route").single())
    }

    @Test
    fun `nessun percorso dal server diventa il messaggio del percorso`() = runTest {
        catalog.routeFound = false
        val viewModel = viewModel()
        viewModel.routeTo(rifugio)
        viewModel.onRouteOrigin(GeoPoint(lat = 39.30, lon = 16.45))
        advanceUntilIdle()

        val failed = assertIs<RouteResult.Failed>(viewModel.state.value.route?.result)
        assertEquals(Res.string.route_none, failed.message.text)
    }

    @Test
    fun `chiudere il percorso scarta la risposta in volo e cambiare partenza riparte dall attesa`() = runTest {
        val viewModel = viewModel()
        viewModel.routeTo(rifugio)
        viewModel.onRouteOrigin(GeoPoint(lat = 39.30, lon = 16.45))
        viewModel.closeRoute()
        advanceUntilIdle()
        assertNull(viewModel.state.value.route)

        viewModel.routeTo(rifugio)
        viewModel.onRouteNoFix()
        assertIs<RouteResult.Failed>(viewModel.state.value.route?.result)
        viewModel.setRouteOrigin(RouteOrigin.MapCenter)
        assertEquals(RouteResult.WaitingForOrigin, viewModel.state.value.route?.result)
        assertEquals(RouteOrigin.MapCenter, viewModel.state.value.route?.origin)
    }
}
