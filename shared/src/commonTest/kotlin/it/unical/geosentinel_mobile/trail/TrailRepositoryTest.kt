package it.unical.geosentinel_mobile.trail

import io.ktor.http.HttpStatusCode
import it.unical.geosentinel_mobile.api.model.TrailDetailResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.toPositions
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import it.unical.geosentinel_mobile.trail.data.local.TrailSummaryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TrailRepositoryTest {

    private val backend = FakeBackend()
    private val catalog = FakeCatalog(backend).also { it.install() }
    private val dao = FakeCatalogDao()
    private var clock = Instant.fromEpochSeconds(1_790_000_000)

    private suspend fun TestScope.repository(): TrailRepository {
        val (session, client) = sessionWith(backend)
        session.restore()
        return TrailRepository({ client }, TEST_BASE_URL, dao, now = { clock }, converting = StandardTestDispatcher(testScheduler))
    }

    private val sila = GeoBounds.of(16.30, 39.10, 16.80, 39.50)

    // ---- dettaglio con ETag ----------------------------------------------------------------------------------

    @Test
    fun `la seconda apertura del sentiero manda l'ETag e con il 304 cambia solo la data`() = runTest {
        val repository = repository()

        assertTrue(repository.refreshDetail(SEED_TRAIL_ID).isSuccess)
        val first = assertNotNull(repository.observeDetail(SEED_TRAIL_ID).first())
        assertEquals("Sentiero delle Vette Silane", first.value.name)

        clock = Instant.fromEpochSeconds(1_790_000_600)
        assertTrue(repository.refreshDetail(SEED_TRAIL_ID).isSuccess)

        assertEquals(1, catalog.count("304"), "la seconda risposta è un 304")
        val second = assertNotNull(repository.observeDetail(SEED_TRAIL_ID).first())
        assertEquals(first.value, second.value, "il 304 non cambia il sentiero")
        assertEquals(clock, second.updatedAt, "ma aggiorna la data")
    }

    @Test
    fun `un sentiero cambiato sul server arriva con un ETag nuovo`() = runTest {
        val repository = repository()
        repository.refreshDetail(SEED_TRAIL_ID)

        catalog.detailVersion = 2
        catalog.detailStatusValue = "OPEN"
        repository.refreshDetail(SEED_TRAIL_ID)

        assertEquals(0, catalog.count("304"))
        assertEquals(TrailDetailResponse.Status.OPEN, repository.observeDetail(SEED_TRAIL_ID).first()?.value?.status)
        assertEquals("W/\"d2\"", dao.etags["trail/$SEED_TRAIL_ID"])
    }

    @Test
    fun `senza la copia in cache l'ETag non si manda`() = runTest {
        val repository = repository()
        repository.refreshDetail(SEED_TRAIL_ID)
        dao.details.value = emptyMap() // copia persa, ETag rimasto

        repository.refreshDetail(SEED_TRAIL_ID)

        assertEquals(0, catalog.count("304"), "un 304 lascerebbe il dettaglio vuoto")
        assertNotNull(repository.observeDetail(SEED_TRAIL_ID).first())
    }

    @Test
    fun `in modalità aereo il sentiero già aperto resta con la sua data`() = runTest {
        val repository = repository()
        repository.refreshDetail(SEED_TRAIL_ID)
        val savedAt = clock

        catalog.offline = true
        clock = Instant.fromEpochSeconds(1_790_003_600)
        val failure = repository.refreshDetail(SEED_TRAIL_ID).exceptionOrNull()

        assertIs<ApiError.Network>(assertIs<ApiException>(failure).error)
        val cached = assertNotNull(repository.observeDetail(SEED_TRAIL_ID).first())
        assertEquals(savedAt, cached.updatedAt, "la data resta quella dell'ultima conferma del server")
    }

    @Test
    fun `il percorso salvato resta in lon lat`() = runTest {
        val repository = repository()
        repository.refreshDetail(SEED_TRAIL_ID)

        val start = assertNotNull(repository.observeDetail(SEED_TRAIL_ID).first()).value.path.toPositions().first()
        assertEquals(16.4852, start.longitude)
        assertEquals(39.2934, start.latitude)
    }

    // ---- POI del sentiero ------------------------------------------------------------------------------------

    @Test
    fun `i POI del sentiero si rivalidano con l'ETag e quelli tolti spariscono`() = runTest {
        val repository = repository()
        repository.refreshPoisOf(SEED_TRAIL_ID)
        assertEquals(listOf("p1"), repository.observePoisOf(SEED_TRAIL_ID).first().map { it.id })

        repository.refreshPoisOf(SEED_TRAIL_ID)
        assertEquals(1, catalog.count("304"))

        catalog.poisVersion = 2
        catalog.pois = emptyList()
        repository.refreshPoisOf(SEED_TRAIL_ID)
        assertTrue(repository.observePoisOf(SEED_TRAIL_ID).first().isEmpty())
    }

    // ---- bbox ------------------------------------------------------------------------------------------------

    @Test
    fun `la bbox completa toglie dalla cache i sentieri che il server non ha più`() = runTest {
        val repository = repository()
        catalog.trails = listOf(trailJson(), trailJson(id = "vecchio", name = "Sentiero tolto"))
        assertEquals(2, repository.refreshInBounds(sila).getOrThrow())

        catalog.trails = listOf(trailJson())
        repository.refreshInBounds(sila)

        assertEquals(listOf(SEED_TRAIL_ID), repository.observeInBounds(sila).first().map { it.id })
    }

    @Test
    fun `con 200 sentieri la cache non si svuota`() = runTest {
        val repository = repository()
        dao.upsertSummaries(listOf(summary("fuori-dalla-risposta")))
        catalog.trails = (1..200).map { trailJson(id = "t$it") }

        assertEquals(200, repository.refreshInBounds(sila).getOrThrow())

        val ids = repository.observeInBounds(sila).first().map { it.id }
        assertTrue("fuori-dalla-risposta" in ids, "con una risposta parziale non si cancella niente")
    }

    @Test
    fun `dalla cache tornano solo i sentieri che toccano la vista`() = runTest {
        val repository = repository()
        repository.refreshInBounds(sila)

        assertEquals(1, repository.observeInBounds(GeoBounds.of(16.49, 39.29, 16.50, 39.30)).first().size)
        assertTrue(repository.observeInBounds(GeoBounds.of(10.0, 44.0, 11.0, 45.0)).first().isEmpty())
    }

    @Test
    fun `una riga illeggibile si salta senza crash`() = runTest {
        val repository = repository()
        dao.upsertSummaries(listOf(summary("rotto").copy(simplifiedGeoJson = "{non json")))

        assertNull(repository.observeInBounds(sila).first().firstOrNull { it.id == "rotto" })
    }

    private fun summary(id: String) = TrailSummaryEntity(
        id = id, name = "Sentiero", difficulty = "EASY", lengthMeters = 1000.0, status = "OPEN",
        simplifiedGeoJson = SEED_TRAIL_PATH, minLon = 16.48, minLat = 39.29, maxLon = 16.52, maxLat = 39.31, updatedAt = clock,
    )

    // ---- dalla review di M3 --------------------------------------------------------------------------------

    @Test
    fun `un sentiero a L che tocca l'area solo con il riquadro non si cancella`() = runTest {
        val repository = repository()
        // il riquadro esce dall'area: il server può non restituirlo anche se esiste ancora
        dao.upsertSummaries(listOf(summary("a-elle").copy(minLon = 16.0, minLat = 39.0, maxLon = 16.9, maxLat = 39.9)))

        repository.refreshInBounds(sila)

        assertTrue("a-elle" in dao.summaries.value, "si cancellano solo i riquadri tutti dentro l'area")
    }

    @Test
    fun `un 404 toglie la copia salvata e il suo ETag`() = runTest {
        val repository = repository()
        repository.refreshDetail(SEED_TRAIL_ID)

        catalog.detailStatus = HttpStatusCode.NotFound
        assertTrue(repository.refreshDetail(SEED_TRAIL_ID).isFailure)

        assertNull(repository.observeDetail(SEED_TRAIL_ID).first())
        assertNull(dao.etags["trail/$SEED_TRAIL_ID"])
    }

    @Test
    fun `con la risposta completa i POI tolti dal server spariscono dal riquadro`() = runTest {
        val repository = repository()
        val area = GeoBounds.of(16.48, 39.29, 16.52, 39.31)
        catalog.pois = catalog.pois + """{"id":"p2","name":"Bivacco vecchio","type":"SHELTER","location":{"lat":39.30,"lon":16.50}}"""
        assertTrue(repository.refreshPoisIn(area).getOrThrow())
        assertEquals(setOf("p1", "p2"), repository.observePoisInBounds(area).first().map { it.id }.toSet())

        catalog.pois = catalog.pois.take(1)
        repository.refreshPoisIn(area)

        assertEquals(listOf("p1"), repository.observePoisInBounds(area).first().map { it.id })
    }

    @Test
    fun `un errore del database diventa un fallimento e non un crash`() = runTest {
        val repository = repository()
        dao.failWrites = true

        assertTrue(repository.refreshDetail(SEED_TRAIL_ID).isFailure)
    }
}
