package it.unical.geosentinel_mobile.trail.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.unical.geosentinel_mobile.core.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// La SQL vera di CatalogDao sulla JVM (§9.5): Room in memoria sopra l'SQLite di Robolectric. Il driver bundled non ha
// le librerie native per il PC, quindi qui niente setDriver: la SQL è la stessa che gira sul telefono.
@RunWith(RobolectricTestRunner::class)
class CatalogDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val dao = db.catalogDao()
    private val at = Instant.fromEpochSeconds(1_790_000_000)
    private val world = doubleArrayOf(-180.0, -90.0, 180.0, 90.0)

    @After
    fun close() = db.close()

    private fun summary(id: String, minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, status: String = "OPEN") =
        TrailSummaryEntity(id, id, "EASY", 1000.0, status, "{}", minLon, minLat, maxLon, maxLat, at)

    private fun poi(id: String, trailId: String?, lat: Double, lon: Double) = PoiEntity(id, trailId, id, "VIEWPOINT", lat, lon, at)

    private suspend fun summaryIds(box: DoubleArray = world) =
        dao.observeSummariesInBox(box[0], box[1], box[2], box[3]).first().map { it.id }.sorted()

    private suspend fun poiIds(box: DoubleArray = world) =
        dao.observePoisInBox(box[0], box[1], box[2], box[3]).first().map { it.id }.sorted()

    @Test
    fun `deleteOutsideBox tiene quello che tocca l'area anche sul bordo e toglie il resto`() = runBlocking {
        // area: lon 16..17, lat 39..40
        dao.upsertSummaries(
            listOf(
                summary("dentro", 16.4, 39.4, 16.5, 39.5),
                summary("a-cavallo", 16.9, 39.4, 17.5, 39.5),
                summary("sul-bordo", 17.0, 40.0, 17.2, 40.2), // tocca l'angolo: resta
                summary("appena-fuori", 17.0001, 39.4, 17.2, 39.5),
                summary("lontano", 10.0, 46.0, 10.1, 46.1),
            ),
        )
        dao.upsertPois(
            listOf(
                poi("p-dentro", "t1", 39.5, 16.5),
                poi("p-bordo", null, 40.0, 17.0),
                poi("p-fuori", "t2", 39.5, 17.5),
            ),
        )
        dao.saveEtag(CatalogEtagEntity("pois/t1", "W/\"1\""))
        dao.saveEtag(CatalogEtagEntity("pois/t2", "W/\"2\""))
        dao.saveEtag(CatalogEtagEntity("trail/t2", "W/\"3\""))

        dao.deleteOutsideBox(16.0, 39.0, 17.0, 40.0)

        assertEquals(listOf("a-cavallo", "dentro", "sul-bordo"), summaryIds())
        assertEquals(listOf("p-bordo", "p-dentro"), poiIds())
        // l'ETag dei POI di t2 va via con i suoi POI (altrimenti un 304 lascerebbe il dettaglio senza POI), gli altri restano
        assertNull(dao.etagOf("pois/t2"))
        assertEquals("W/\"1\"", dao.etagOf("pois/t1"))
        assertEquals("W/\"3\"", dao.etagOf("trail/t2"))
    }

    @Test
    fun `le query per riquadro comprendono il bordo`() = runBlocking {
        dao.upsertSummaries(listOf(summary("sila", 16.48, 39.29, 16.52, 39.31), summary("alpi", 10.0, 46.0, 10.1, 46.1)))
        dao.upsertPois(listOf(poi("sul-bordo", null, 39.4, 16.6), poi("fuori", null, 39.41, 16.6)))

        // la vista taglia il sentiero a metà o ne tocca solo il bordo: c'è; un'area lontana: niente
        assertEquals(listOf("sila"), summaryIds(doubleArrayOf(16.50, 39.30, 16.60, 39.40)))
        assertEquals(listOf("sila"), summaryIds(doubleArrayOf(16.52, 39.31, 16.60, 39.40)))
        assertEquals(emptyList(), summaryIds(doubleArrayOf(12.0, 41.0, 13.0, 42.0)))
        assertEquals(listOf("sul-bordo"), poiIds(doubleArrayOf(16.4, 39.2, 16.6, 39.4)))
    }

    @Test
    fun `upsert sostituisce la riga e lo stato cambia senza rileggere`() = runBlocking {
        dao.upsertSummaries(listOf(summary("t1", 16.0, 39.0, 16.1, 39.1)))
        dao.upsertSummaries(listOf(summary("t1", 16.0, 39.0, 16.1, 39.1, status = "CLOSED")))
        assertEquals(listOf("CLOSED"), dao.observeSummariesInBox(world[0], world[1], world[2], world[3]).first().map { it.status })

        dao.updateSummaryStatus("t1", "OPEN")
        assertEquals(listOf("OPEN"), dao.observeSummariesInBox(world[0], world[1], world[2], world[3]).first().map { it.status })
    }

    @Test
    fun `replaceSummariesInBox toglie solo quelli tutti dentro che il server non ha piu`() = runBlocking {
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
        assertEquals(listOf("alpi", "elle", "resta"), summaryIds())

        // risposta vuota: "NOT IN" con la lista vuota non deve rompere la query
        dao.replaceSummariesInBox(16.30, 39.10, 16.80, 39.50, emptyList())
        assertEquals(listOf("alpi", "elle"), summaryIds())
    }

    @Test
    fun `replacePoisInBox e replacePoisOfTrail toccano solo il loro insieme`() = runBlocking {
        dao.upsertPois(
            listOf(poi("resta", "t1", 39.3, 16.5), poi("tolto", "t1", 39.31, 16.51), poi("fuori", "t2", 40.5, 17.5)),
        )

        dao.replacePoisInBox(16.4, 39.2, 16.6, 39.4, listOf(poi("resta", "t1", 39.3, 16.5)))
        assertEquals(listOf("fuori", "resta"), poiIds())
        dao.replacePoisInBox(16.4, 39.2, 16.6, 39.4, emptyList())
        assertEquals(listOf("fuori"), poiIds())

        dao.upsertPois(listOf(poi("senza-sentiero", null, 39.3, 16.5)))
        dao.replacePoisOfTrail("t2", listOf(poi("nuovo", "t2", 39.0, 16.0)))
        assertEquals(listOf("nuovo"), dao.observePoiByTrail("t2").first().map { it.id })
        assertEquals(listOf("nuovo", "senza-sentiero"), poiIds())
    }
}
