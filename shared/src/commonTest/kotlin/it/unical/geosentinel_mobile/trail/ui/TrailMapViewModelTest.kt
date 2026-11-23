// Test di TrailMapViewModel, il ViewModel della mappa principale: caricamento di sentieri e POI dell'area visibile
// con attesa dopo l'ultimo movimento, limite dei 50 km dall'utente, funzionamento offline, "Sentieri vicini" e
// percorso A-B calcolato dal backend.
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

/** Verifica il comportamento della mappa al variare di vista, zoom e rete, contro il catalogo finto. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrailMapViewModelTest {

    private val backend = FakeBackend()
    private val catalog = FakeCatalog(backend).also { it.install() }
    private val dao = FakeCatalogDao()

    /** Vista sulla Sila come la invia la mappa a zoom 10. */
    private val sila = GeoBounds.of(16.40, 39.25, 16.60, 39.40)

    /**
     * Crea il ViewModel della mappa da ospite (sentieri e POI sono pubblici). viewModelScope usa Dispatchers.Main, che
     * qui viene sostituito con il dispatcher del test.
     */
    private suspend fun TestScope.viewModel(): TrailMapViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (session, client) = sessionWith(backend)
        session.restore()
        return TrailMapViewModel(TrailRepository({ client }, TEST_BASE_URL, dao, converting = StandardTestDispatcher(testScheduler)))
    }

    /** Ripristina il dispatcher principale dopo ogni test. */
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Estrae i quattro numeri del parametro bbox da una query string. */
    private fun bboxValues(query: String): List<Double> =
        query.removePrefix("bbox=").decodeURLQueryComponent().split(",").map { it.toDouble() }

    /**
     * Quando la mappa si ferma la richiesta parte solo dopo l'attesa di 400 ms (debounce), e il percorso del sentiero
     * ricevuto mantiene l'ordine [lon, lat].
     */
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

    /**
     * Più movimenti ravvicinati producono una sola richiesta, con l'ultima vista allargata di metà della sua ampiezza
     * per lato, così piccoli spostamenti successivi restano dentro l'area già caricata.
     */
    @Test
    fun `movimenti ravvicinati fanno una sola richiesta con l'ultima vista e un margine`() = runTest {
        val viewModel = viewModel()

        viewModel.onCameraIdle(GeoBounds.of(10.0, 40.0, 11.0, 41.0), zoom = 10.0)
        advanceTimeBy(200)
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()

        assertEquals(1, catalog.count("bbox"))
        // L'ordine atteso dal backend è minLon, minLat, maxLon, maxLat.
        val (minLon, minLat, maxLon, maxLat) = bboxValues(catalog.queries("bbox").single())
        assertEquals(16.30, minLon, 1e-9)
        assertEquals(39.175, minLat, 1e-9)
        assertEquals(16.70, maxLon, 1e-9)
        assertEquals(39.475, maxLat, 1e-9)
    }

    /** Una vista contenuta nell'area già caricata non produce richieste, una che ne esce sì. */
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

    /**
     * Una vista che supera il limite dei 50 km dall'utente chiede solo la parte interna al limite; l'area caricata
     * viene ritagliata allo stesso modo, quindi la stessa vista una seconda volta risulta già coperta.
     */
    @Test
    fun `una vista oltre i 50 km chiede solo la parte dentro e una volta sola`() = runTest {
        val viewModel = viewModel()
        val limit = GeoBounds.around(GeoPoint(39.3, 16.5), TrailMapViewModel.NearRadiusMeters)
        val view = GeoBounds.of(16.9, 39.2, 17.3, 39.4)

        viewModel.onCameraIdle(view, zoom = 10.0, limit = limit)
        advanceUntilIdle()
        viewModel.onCameraIdle(view, zoom = 10.0, limit = limit)
        advanceUntilIdle()

        assertEquals(1, catalog.count("bbox"), "l'area caricata è ritagliata come la vista: la seconda volta è coperta")
        val (_, _, maxLon, _) = bboxValues(catalog.queries("bbox").single())
        assertTrue(maxLon <= limit.maxLon + 1e-6)
    }

    /**
     * Se la vista è tutta oltre i 50 km dall'utente non si chiede nulla, non si mostra nulla e la cache viene ripulita
     * da quello che è fuori dal limite.
     */
    @Test
    fun `fuori dai 50 km non si mostra e non si chiede nulla e la cache si svuota`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()
        assertEquals(1, dao.summaries.value.size)

        // L'utente è a Roma: la Sila è a più di 50 km.
        val limit = GeoBounds.around(GeoPoint(41.9, 12.5), TrailMapViewModel.NearRadiusMeters)
        viewModel.onCameraIdle(sila, zoom = 10.0, limit = limit)
        advanceUntilIdle()

        assertEquals(1, catalog.count("bbox"))
        assertTrue(viewModel.state.value.trails.isEmpty())
        assertTrue(dao.summaries.value.isEmpty(), "la cache tiene solo quello entro 50 km")
    }

    /**
     * Se il server restituisce il numero massimo di sentieri l'area potrebbe essere incompleta: la UI avvisa, e anche
     * una vista interna a quell'area produce una nuova richiesta.
     */
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

    /** Senza rete restano visibili i sentieri salvati e compare l'errore; "Riprova" ripete la richiesta. */
    @Test
    fun `senza rete restano i sentieri di prima e riprova li ricarica`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()

        // La nuova vista esce dall'area già caricata (quindi si chiede al server) ma contiene ancora il sentiero salvato.
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

    /** Con uno zoom troppo basso non parte nessuna richiesta e la UI invita ad avvicinarsi. */
    @Test
    fun `da lontano non si chiede niente e si invita ad avvicinarsi`() = runTest {
        val viewModel = viewModel()

        viewModel.onCameraIdle(GeoBounds.of(6.0, 36.0, 19.0, 47.0), zoom = 5.0)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.zoomTooLow)
        assertEquals(0, catalog.count("bbox"))
    }

    /** I POI si chiedono solo sopra la loro soglia di zoom, più alta di quella dei sentieri. */
    @Test
    fun `i POI si chiedono solo da vicino`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = TrailMapViewModel.MinPoiZoom - 0.5)
        advanceUntilIdle()
        assertEquals(0, catalog.count("pois-nearby"))

        viewModel.onCameraIdle(GeoBounds.of(16.48, 39.29, 16.50, 39.30), zoom = 14.0)
        advanceUntilIdle()
        assertEquals(1, catalog.count("pois-nearby"))
        assertEquals("p1", viewModel.state.value.pois.single().id)
    }

    /** "Sentieri vicini" aspetta la posizione e poi la invia al server senza scambiare latitudine e longitudine. */
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

    /** Se l'utente chiude il foglio "Sentieri vicini", la risposta che arriva dopo non lo riapre. */
    @Test
    fun `chiuso il foglio la risposta in arrivo non lo riapre`() = runTest {
        val viewModel = viewModel()

        viewModel.onNearbyFix(GeoPoint(lat = 39.33, lon = 16.45))
        viewModel.closeNearby()
        advanceUntilIdle()

        assertEquals(NearbyTrails.Hidden, viewModel.state.value.nearby)
    }

    /** La mancanza di una posizione mostra l'errore solo se il foglio la stava aspettando. */
    @Test
    fun `senza fix l'errore compare solo se si stava aspettando`() = runTest {
        val viewModel = viewModel()

        viewModel.onNearbyNoFix()
        assertEquals(NearbyTrails.Hidden, viewModel.state.value.nearby)
        viewModel.onNearbyRequested()
        viewModel.onNearbyNoFix()
        assertIs<NearbyTrails.Failed>(viewModel.state.value.nearby)
    }

    /**
     * Se un nuovo movimento annulla una richiesta ancora in corso (tornando in un'area già caricata), l'indicatore di
     * caricamento non resta acceso per sempre.
     */
    @Test
    fun `un movimento che annulla la richiesta non lascia Carico per sempre`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()

        // La vista esce dall'area caricata: la richiesta parte e resta in corso per 5 secondi.
        catalog.bboxDelayMillis = 5_000
        viewModel.onCameraIdle(GeoBounds.of(16.90, 39.25, 17.10, 39.40), zoom = 10.0)
        advanceTimeBy(1_000)
        assertTrue(viewModel.state.value.loading)

        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.loading)
    }

    /**
     * Sotto la soglia di zoom dei sentieri la lista resta nello stato (è la mappa a nasconderli con una dissolvenza),
     * ma non si fanno richieste al server.
     */
    @Test
    fun `da lontano restano quelli in cache e non si chiede al server`() = runTest {
        val viewModel = viewModel()
        // Si torna nell'area già caricata prima che arrivi la risposta.
        viewModel.onCameraIdle(sila, zoom = 10.0)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.trails.size)

        viewModel.onCameraIdle(GeoBounds.of(6.0, 36.0, 19.0, 47.0), zoom = TrailMapViewModel.MinTrailZoom - 2)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.trails.size)
        assertEquals(1, catalog.count("bbox"))
    }


    /** Destinazione di prova per il percorso A-B. */
    private val rifugio = Place("Rifugio", GeoPoint(lat = 39.31, lon = 16.52))

    /**
     * Il percorso verso un luogo parte dalla posizione dell'utente: prima aspetta la posizione, poi chiede al server
     * il percorso con partenza e arrivo nell'ordine longitudine e latitudine, e mostra lunghezza e numero di sentieri.
     */
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

    /** Se il server risponde che non esiste un percorso, il pannello mostra il messaggio dedicato. */
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

    /**
     * Chiudere il pannello del percorso scarta la risposta ancora in corso; se manca la posizione, scegliere come
     * partenza il centro della mappa riporta il pannello in attesa della partenza.
     */
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
