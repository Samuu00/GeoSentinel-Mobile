package it.unical.geosentinel_mobile.trail.ui

import io.ktor.http.HttpStatusCode
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.trail.FakeCatalog
import it.unical.geosentinel_mobile.trail.FakeCatalogDao
import it.unical.geosentinel_mobile.trail.SEED_TRAIL_ID
import it.unical.geosentinel_mobile.trail.data.TrailRepository
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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrailDetailViewModelTest {

    private val backend = FakeBackend()
    private val catalog = FakeCatalog(backend).also { it.install() }
    private val dao = FakeCatalogDao()
    private lateinit var session: SessionManager

    private suspend fun TestScope.viewModel(loggedIn: Boolean): TrailDetailViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (manager, client) = sessionWith(backend)
        session = manager
        session.restore()
        if (loggedIn) assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        val http = { client }
        return TrailDetailViewModel(
            SEED_TRAIL_ID,
            TrailRepository(http, TEST_BASE_URL, dao, converting = StandardTestDispatcher(testScheduler)),
            HazardRepository(http, TEST_BASE_URL),
            MediaRepository(http, TEST_BASE_URL),
            session,
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `da ospite si vedono sentiero e POI ma pericoli e foto non si chiedono`() = runTest {
        val viewModel = viewModel(loggedIn = false)
        advanceUntilIdle()

        val ui = viewModel.state.value
        val trail = assertNotNull(ui.trail)
        assertEquals("Sentiero delle Vette Silane", trail.name)
        assertEquals(TrailState.CLOSED, TrailState.of(trail.status.value))
        assertEquals("p1", ui.pois.single().id)
        assertEquals(Section.Hidden, ui.hazards)
        assertEquals(Section.Hidden, ui.photos)
        assertEquals(0, catalog.count("hazards") + catalog.count("media"), "le rotte con login non partono da ospite")
    }

    @Test
    fun `da loggati arrivano i pericoli e solo le foto pronte`() = runTest {
        val viewModel = viewModel(loggedIn = true)
        advanceUntilIdle()

        val ui = viewModel.state.value
        val hazards = assertIs<Section.Loaded<List<HazardResponse>>>(ui.hazards).value
        assertEquals(HazardResponse.Type.FALLEN_TREE, hazards.single().type)
        val photos = assertIs<Section.Loaded<List<MediaResponse>>>(ui.photos).value
        assertEquals(listOf("m1", "m3"), photos.map { it.id }, "quella in quarantena non si mostra")
    }

    @Test
    fun `l'URL della foto si chiede quando compare e di nuovo quando scade ma non all'infinito`() = runTest {
        val viewModel = viewModel(loggedIn = true)
        advanceUntilIdle()

        viewModel.onPhotoShown("m1")
        viewModel.onPhotoShown("m1") // una seconda composizione non chiede un altro URL
        advanceUntilIdle()
        assertEquals(1, catalog.count("download-url"))
        val first = viewModel.state.value.photoUrls.getValue("m1")

        viewModel.onPhotoFailed("m1") // URL scaduto (5 minuti)
        advanceUntilIdle()
        assertNotEquals(first, viewModel.state.value.photoUrls.getValue("m1"), "un URL nuovo")

        repeat(5) {
            viewModel.onPhotoFailed("m1")
            advanceUntilIdle()
        }
        assertEquals(3, catalog.count("download-url"), "il primo URL più due rinnovi")
    }

    @Test
    fun `all'uscita pericoli e foto spariscono`() = runTest {
        val viewModel = viewModel(loggedIn = true)
        advanceUntilIdle()
        viewModel.onPhotoShown("m1")
        advanceUntilIdle()

        session.logout()
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals(Section.Hidden, ui.hazards)
        assertEquals(Section.Hidden, ui.photos)
        assertTrue(ui.photoUrls.isEmpty(), "niente URL firmati dopo l'uscita")
    }

    @Test
    fun `sentiero non trovato e riprova`() = runTest {
        catalog.detailStatus = HttpStatusCode.NotFound
        val viewModel = viewModel(loggedIn = false)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.trail)

        catalog.detailStatus = HttpStatusCode.OK
        viewModel.retry()
        advanceUntilIdle()
        assertNull(viewModel.state.value.error)
        assertNotNull(viewModel.state.value.trail)
    }

    @Test
    fun `pericoli senza rete si possono ricaricare`() = runTest {
        val viewModel = viewModel(loggedIn = true)
        catalog.offline = true
        advanceUntilIdle()
        assertIs<Section.Failed>(viewModel.state.value.hazards)

        catalog.offline = false
        viewModel.retryHazards()
        advanceUntilIdle()
        assertIs<Section.Loaded<*>>(viewModel.state.value.hazards)
    }

    @Test
    fun `senza rete con la copia salvata si mostra quella con la data e l'invito a riprovare`() = runTest {
        // prima apertura con la rete: il sentiero finisce in cache
        viewModel(loggedIn = false)
        advanceUntilIdle()

        catalog.offline = true
        val offline = viewModel(loggedIn = false)
        advanceUntilIdle()

        val ui = offline.state.value
        assertNotNull(ui.trail, "il sentiero già aperto si vede ancora")
        assertNotNull(ui.updatedAt)
        assertNotNull(ui.stale)
        assertNull(ui.error)
        assertEquals("p1", ui.pois.single().id, "anche i POI salvati")

        catalog.offline = false
        offline.retry()
        advanceUntilIdle()
        assertNull(offline.state.value.stale)
    }

    @Test
    fun `senza rete e senza copia salvata c'è l'errore`() = runTest {
        catalog.offline = true
        val viewModel = viewModel(loggedIn = false)
        advanceUntilIdle()

        assertNull(viewModel.state.value.trail)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `se l'URL firmato non arriva la foto mostra l'errore e si riprova`() = runTest {
        val viewModel = viewModel(loggedIn = true)
        advanceUntilIdle()

        catalog.downloadUrlFails = true
        viewModel.onPhotoShown("m1")
        advanceUntilIdle()
        assertTrue("m1" in viewModel.state.value.failedPhotos, "niente caricamento infinito")

        catalog.downloadUrlFails = false
        viewModel.onPhotoRetry("m1")
        advanceUntilIdle()
        assertTrue("m1" !in viewModel.state.value.failedPhotos)
        assertTrue("m1" in viewModel.state.value.photoUrls)
    }

    @Test
    fun `un sentiero cancellato sul server mostra l'errore anche con la copia salvata`() = runTest {
        viewModel(loggedIn = false)
        advanceUntilIdle()

        catalog.detailStatus = HttpStatusCode.NotFound
        val again = viewModel(loggedIn = false)
        advanceUntilIdle()

        assertNull(again.state.value.trail)
        assertNotNull(again.state.value.error)
        assertNull(again.state.value.stale)
    }
}
