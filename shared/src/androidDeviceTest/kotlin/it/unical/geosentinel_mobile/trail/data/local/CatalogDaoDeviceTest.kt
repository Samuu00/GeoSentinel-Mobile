package it.unical.geosentinel_mobile.trail.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import it.unical.geosentinel_mobile.core.db.AppDatabase
import it.unical.geosentinel_mobile.core.db.createAppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// La SQL vera di CatalogDao (Room e SQLite bundled), che sulla JVM si prova solo con il DAO finto.
// Nomi senza spazi: D8 non li accetta sotto Android 11.
class CatalogDaoDeviceTest {

    private val db: AppDatabase = createAppDatabase(
        Room.inMemoryDatabaseBuilder<AppDatabase>(InstrumentationRegistry.getInstrumentation().targetContext),
    )
    private val dao = db.catalogDao()
    private val at = Instant.fromEpochSeconds(1_790_000_000)

    @AfterTest
    fun close() = db.close()

    private fun summary(id: String, minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) = TrailSummaryEntity(
        id = id, name = id, difficulty = "EASY", lengthMeters = 1000.0, status = "OPEN", simplifiedGeoJson = "{}",
        minLon = minLon, minLat = minLat, maxLon = maxLon, maxLat = maxLat, updatedAt = at,
    )

    @Test
    fun summariesInBoxAreTheOnesTouchingIt() = runBlocking {
        dao.upsertSummaries(listOf(summary("sila", 16.48, 39.29, 16.52, 39.31), summary("alpi", 10.0, 46.0, 10.1, 46.1)))

        // la vista taglia il sentiero a metà: c'è; un'area lontana: niente
        assertEquals(listOf("sila"), dao.observeSummariesInBox(16.50, 39.30, 16.60, 39.40).first().map { it.id })
        assertEquals(emptyList(), dao.observeSummariesInBox(12.0, 41.0, 13.0, 42.0).first())
    }

    @Test
    fun replaceInBoxRemovesOnlyTheMissingOnesInsideTheBox() = runBlocking {
        dao.upsertSummaries(
            listOf(
                summary("resta", 16.48, 39.29, 16.52, 39.31),
                summary("tolto", 16.55, 39.32, 16.56, 39.33),
                summary("alpi", 10.0, 46.0, 10.1, 46.1),
                // a L: il riquadro tocca l'area ma esce, il server può non restituirlo anche se esiste
                summary("elle", 16.0, 39.0, 16.9, 39.9),
            ),
        )

        dao.replaceSummariesInBox(16.30, 39.10, 16.80, 39.50, listOf(summary("resta", 16.48, 39.29, 16.52, 39.31)))

        assertEquals(listOf("alpi", "elle", "resta"), dao.observeSummariesInBox(-180.0, -90.0, 180.0, 90.0).first().map { it.id }.sorted())
        // risposta vuota: "NOT IN" con la lista vuota non deve rompere la query
        dao.replaceSummariesInBox(16.30, 39.10, 16.80, 39.50, emptyList())
        assertEquals(listOf("alpi", "elle"), dao.observeSummariesInBox(-180.0, -90.0, 180.0, 90.0).first().map { it.id }.sorted())
    }

    @Test
    fun detailTimestampEtagAndPoisOfTrail() = runBlocking {
        dao.upsertTrailDetail(TrailDetailEntity("t1", "Sentiero", "EASY", 1000.0, "OPEN", "{}", null, at))
        val later = Instant.fromEpochSeconds(1_790_000_600)
        dao.updateTrailDetailTimestamp("t1", later)
        assertEquals(later, dao.getTrailDetail("t1")?.updatedAt)

        assertNull(dao.etagOf("trail/t1"))
        dao.saveEtag(CatalogEtagEntity("trail/t1", "W/\"1\""))
        dao.saveEtag(CatalogEtagEntity("trail/t1", "W/\"2\""))
        assertEquals("W/\"2\"", dao.etagOf("trail/t1"))

        dao.upsertPois(listOf(PoiEntity("p1", "t1", "Fonte", "WATER_SOURCE", 39.3, 16.5, at), PoiEntity("p2", null, "Belvedere", "VIEWPOINT", 39.31, 16.51, at)))
        dao.replacePoisOfTrail("t1", emptyList())
        assertEquals(emptyList(), dao.observePoiByTrail("t1").first())
        assertEquals(listOf("p2"), dao.observePoisInBox(16.4, 39.2, 16.6, 39.4).first().map { it.id }, "quelli senza sentiero restano")
    }
}
