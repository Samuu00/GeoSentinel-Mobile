package it.unical.geosentinel_mobile.refuge.ui

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.refuge.data.NightAvailability
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.refuge.data.RefugeRepository
import it.unical.geosentinel_mobile.trail.FakeCatalogDao
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import it.unical.geosentinel_mobile.trail.mediaJson
import it.unical.geosentinel_mobile.trail.trailJson
import it.unical.geosentinel_mobile.trail.ui.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RefugeViewModelsTest {

    private val backend = FakeBackend()
    private var capacity = 4
    private val refugeJson get() = """{"id":"r1","name":"Rifugio Monte Botte Donato","location":{"lat":39.278,"lon":16.452},"capacity":$capacity,"managerId":"m1","amenities":[]}"""
    // le foto arrivano fuori contratto (un campo obbligatorio mancante)
    private var mediaBroken = false
    private val today = LocalDate(2026, 10, 5)
    // il percorso e la query di ogni richiesta ai rifugi, nell'ordine
    private val calls = mutableListOf<Pair<String, Parameters>>()
    private var refugeStatus = HttpStatusCode.OK
    private var availabilityOffline = false
    private var offline = false

    private suspend fun TestScope.repository(): RefugeRepository {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        backend.extraRoutes = { request, _ ->
            val path = request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (request.method == HttpMethod.Get && path == "/api/v1/media/attached") {
                calls += path to request.url.parameters
                if (mediaBroken) {
                    respond("""[{"id":"m1"}]""", HttpStatusCode.OK, json)
                } else {
                    respond("[${mediaJson("m1", "READY")},${mediaJson("m2", "QUARANTINED")}]", HttpStatusCode.OK, json)
                }
            } else if (request.method == HttpMethod.Get && path == "/api/v1/trails/nearby") {
                calls += path to request.url.parameters
                respond("[${trailJson()}]", HttpStatusCode.OK, json)
            } else if (request.method != HttpMethod.Get || !path.startsWith("/api/v1/refuges")) {
                null
            } else {
                calls += path to request.url.parameters
                if (offline) throw IOException("rete assente")
                when (path) {
                    "/api/v1/refuges/nearby" -> respond("[$refugeJson]", HttpStatusCode.OK, json)
                    "/api/v1/refuges/r1" -> if (refugeStatus == HttpStatusCode.OK) {
                        respond(refugeJson, HttpStatusCode.OK, json)
                    } else {
                        respond(
                            """{"type":"about:blank","title":"Not Found","status":404}""",
                            refugeStatus,
                            headersOf(HttpHeaders.ContentType, "application/problem+json"),
                        )
                    }
                    "/api/v1/refuges/r1/availability" -> {
                        if (availabilityOffline) throw IOException("rete assente")
                        // in disordine e con una notte piena: l'app le ordina
                        respond(
                            """{"refugeId":"r1","totalCapacity":4,"from":"2026-10-05","to":"2026-10-19","availableBedsPerNight":{"2026-10-07":0,"2026-10-05":4,"2026-10-06":2}}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    }
                    else -> null
                }
            }
        }
        val (session, client) = sessionWith(backend)
        session.restore()
        assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        httpClient = client
        return RefugeRepository({ client }, TEST_BASE_URL)
    }

    private lateinit var httpClient: HttpClient

    private suspend fun TestScope.detailViewModel(): RefugeDetailViewModel {
        val refuges = repository()
        return RefugeDetailViewModel(
            "r1",
            refuges,
            MediaRepository({ httpClient }, TEST_BASE_URL),
            TrailRepository({ httpClient }, TEST_BASE_URL, FakeCatalogDao(), converting = StandardTestDispatcher(testScheduler)),
            today = { today },
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // ---- Dettaglio ----

    @Test
    fun `il dettaglio carica il rifugio e 14 notti da oggi in ordine`() = runTest {
        val viewModel = detailViewModel()
        advanceUntilIdle()

        val refuge = assertIs<Section.Loaded<RefugeResponse>>(viewModel.state.value.refuge).value
        assertEquals(4, refuge.capacity)
        val nights = assertIs<Section.Loaded<List<NightAvailability>>>(viewModel.state.value.nights).value
        assertEquals(listOf(4, 2, 0), nights.map { it.freeBeds })
        assertEquals(LocalDate(2026, 10, 5), nights.first().date)
        assertTrue(nights.last().isFull)
        assertFalse(nights.first().isFull)
        // [from, to): 14 notti esatte, il massimo del server
        val query = calls.single { it.first.endsWith("/availability") }.second
        assertEquals("2026-10-05", query["from"])
        assertEquals("2026-10-19", query["to"])
    }

    @Test
    fun `il dettaglio carica le foto pronte e i sentieri attorno al rifugio`() = runTest {
        val viewModel = detailViewModel()
        advanceUntilIdle()

        // solo la foto READY, non quella in quarantena
        assertEquals(listOf("m1"), viewModel.state.value.photos.map { it.id })
        assertEquals(1, viewModel.state.value.nearbyTrails.size)
        assertEquals("REFUGE", calls.single { it.first == "/api/v1/media/attached" }.second["entityType"])
        // i sentieri si cercano dal rifugio (lat 39.278), non invertito, entro 5 km
        val query = calls.single { it.first == "/api/v1/trails/nearby" }.second
        assertEquals(39.278, query["lat"]!!.toDouble(), 0.0001)
        assertEquals(16.452, query["lon"]!!.toDouble(), 0.0001)
        assertEquals(TRAILS_NEAR_REFUGE_METERS, query["radius"]!!.toDouble(), 0.1)
    }

    @Test
    fun `rifugio inesistente mostra l'errore e riprova ricarica tutto`() = runTest {
        refugeStatus = HttpStatusCode.NotFound
        val viewModel = detailViewModel()
        advanceUntilIdle()
        assertIs<Section.Failed>(viewModel.state.value.refuge)

        refugeStatus = HttpStatusCode.OK
        viewModel.retry()
        advanceUntilIdle()

        assertIs<Section.Loaded<RefugeResponse>>(viewModel.state.value.refuge)
        assertEquals(2, calls.count { it.first == "/api/v1/refuges/r1" })
    }

    @Test
    fun `senza calendario il rifugio resta e riprova chiede solo le notti`() = runTest {
        availabilityOffline = true
        val viewModel = detailViewModel()
        advanceUntilIdle()
        assertIs<Section.Loaded<RefugeResponse>>(viewModel.state.value.refuge)
        assertIs<Section.Failed>(viewModel.state.value.nights)

        availabilityOffline = false
        viewModel.retry()
        advanceUntilIdle()

        assertIs<Section.Loaded<List<NightAvailability>>>(viewModel.state.value.nights)
        assertEquals(1, calls.count { it.first == "/api/v1/refuges/r1" })
    }

    @Test
    fun `refresh rilegge posti e notti senza tornare al caricamento e un errore lascia i dati`() = runTest {
        val viewModel = detailViewModel()
        advanceUntilIdle()
        capacity = 6

        viewModel.refresh()
        assertIs<Section.Loaded<RefugeResponse>>(viewModel.state.value.refuge, "niente schermata di caricamento")
        advanceUntilIdle()
        assertEquals(6, assertIs<Section.Loaded<RefugeResponse>>(viewModel.state.value.refuge).value.capacity)

        offline = true
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(6, assertIs<Section.Loaded<RefugeResponse>>(viewModel.state.value.refuge).value.capacity)
        assertIs<Section.Loaded<List<NightAvailability>>>(viewModel.state.value.nights)
    }

    @Test
    fun `foto fuori contratto non fanno cadere il dettaglio`() = runTest {
        mediaBroken = true
        val viewModel = detailViewModel()
        advanceUntilIdle()

        assertIs<Section.Loaded<RefugeResponse>>(viewModel.state.value.refuge)
        assertTrue(viewModel.state.value.photos.isEmpty())
    }

    @Test
    fun `lo stato nei log non ha il nome del rifugio`() = runTest {
        val viewModel = detailViewModel()
        advanceUntilIdle()

        assertFalse("Botte" in viewModel.state.value.toString())
    }

    // ---- Lista ----

    @Test
    fun `la lista chiede i rifugi entro 50 km dal punto scelto`() = runTest {
        val viewModel = RefugeListViewModel(repository())
        val here = GeoPoint(lat = 39.31, lon = 16.54)

        viewModel.load(here, nearMe = true)
        advanceUntilIdle()

        val loaded = assertIs<RefugeList.Loaded>(viewModel.state.value)
        assertEquals(listOf("r1"), loaded.refuges.map { it.id })
        assertTrue(loaded.nearMe)
        val query = calls.single().second
        assertEquals("39.31", query["lat"])
        assertEquals("16.54", query["lon"])
        assertEquals(50000.0, query["radius"]!!.toDouble())
    }

    @Test
    fun `la lista senza rete mostra l'errore e riprova dallo stesso punto`() = runTest {
        val viewModel = RefugeListViewModel(repository())
        offline = true
        viewModel.load(GeoPoint(lat = 39.33, lon = 16.55), nearMe = false)
        advanceUntilIdle()
        assertIs<RefugeList.Failed>(viewModel.state.value)

        offline = false
        viewModel.retry()
        advanceUntilIdle()

        val loaded = assertIs<RefugeList.Loaded>(viewModel.state.value)
        assertFalse(loaded.nearMe)
        assertEquals(calls[0].second["lat"], calls.last().second["lat"])
    }

    // ---- Mappa ----

    @Test
    fun `sulla mappa da ospite nessuna richiesta`() = runTest {
        val viewModel = RefugeMapViewModel(repository())

        viewModel.onCameraIdle(GeoBounds.of(16.40, 39.25, 16.50, 39.31), zoom = 12.0)
        advanceUntilIdle()

        assertTrue(calls.isEmpty())
        assertTrue(viewModel.state.value.isEmpty())
    }

    @Test
    fun `sulla mappa da loggato carica i rifugi e al logout spariscono`() = runTest {
        val viewModel = RefugeMapViewModel(repository())
        viewModel.setEnabled(true)
        viewModel.onCameraIdle(GeoBounds.of(16.40, 39.25, 16.50, 39.31), zoom = 12.0)
        advanceUntilIdle()
        assertEquals(listOf("r1"), viewModel.state.value.map { it.id })

        // dentro l'area già caricata non si richiede
        viewModel.onCameraIdle(GeoBounds.of(16.42, 39.26, 16.48, 39.30), zoom = 13.0)
        advanceUntilIdle()
        assertEquals(1, calls.size)

        viewModel.setEnabled(false)
        assertTrue(viewModel.state.value.isEmpty())
    }

    @Test
    fun `sulla mappa sotto lo zoom minimo niente richiesta`() = runTest {
        val viewModel = RefugeMapViewModel(repository())
        viewModel.setEnabled(true)

        viewModel.onCameraIdle(GeoBounds.of(15.0, 38.0, 18.0, 41.0), zoom = RefugeMapViewModel.MinZoom - 1)
        advanceUntilIdle()

        assertTrue(calls.isEmpty())
    }
}
