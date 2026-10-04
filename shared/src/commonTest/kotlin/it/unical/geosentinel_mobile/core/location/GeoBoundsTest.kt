package it.unical.geosentinel_mobile.core.location

import it.unical.geosentinel_mobile.api.model.GeoJsonLineString
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoBoundsTest {

    // Il percorso del primo sentiero del seed del backend (Sila), come arriva dall'API: [lon, lat]
    private val seedPath = GeoJsonLineString(
        coordinates = listOf(listOf(16.4852, 39.2934), listOf(16.4910, 39.2980), listOf(16.5020, 39.3050), listOf(16.5150, 39.3100)),
        type = "LineString",
    )

    // ---- regressione lon/lat ----------------------------------------------------------------------------------

    @Test
    fun `il percorso del seed si disegna in Calabria e non nel Golfo di Aden`() {
        val positions = seedPath.toPositions()

        assertEquals(4, positions.size)
        positions.forEach { position ->
            // Calabria: lon circa 16, lat circa 39. Con l'ordine invertito sarebbe lat 16, lon 39 (Golfo di Aden)
            assertTrue(position.longitude in 15.5..17.5, "longitudine fuori dalla Calabria")
            assertTrue(position.latitude in 38.5..40.0, "latitudine fuori dalla Calabria")
        }
        assertEquals(16.4852, positions.first().longitude)
        assertEquals(39.2934, positions.first().latitude)
    }

    @Test
    fun `le coppie malformate si saltano`() {
        val path = GeoJsonLineString(coordinates = listOf(listOf(16.48, 39.29), listOf(16.5), listOf(16.51, 39.31)), type = "LineString")

        assertEquals(2, path.toPositions().size)
    }

    // ---- bbox ---------------------------------------------------------------------------------------------------

    @Test
    fun `la bbox va al backend come minLon minLat maxLon maxLat`() {
        assertEquals("16.47,39.29,16.51,39.31", GeoBounds.of(16.47, 39.29, 16.51, 39.31).toQuery())
    }

    @Test
    fun `la bbox resta dentro i limiti del mondo`() {
        val bounds = GeoBounds.of(-200.0, -95.0, 200.0, 95.0)

        assertEquals("-180.0,-90.0,180.0,90.0", bounds.toQuery())
        assertTrue(bounds.isValid)
        assertFalse(GeoBounds.of(16.5, 39.3, 16.5, 39.4).isValid, "il backend vuole min minore di max")
    }

    @Test
    fun `contains ed expandedBy`() {
        val view = GeoBounds.of(16.0, 39.0, 17.0, 40.0)
        val area = view.expandedBy(0.5)

        assertEquals(GeoBounds.of(15.5, 38.5, 17.5, 40.5), area)
        assertTrue(view in area)
        assertFalse(area in view)
        assertFalse(GeoBounds.of(17.2, 39.0, 17.8, 40.0) in area)
    }

    @Test
    fun `around trova il riquadro del percorso`() {
        assertEquals(GeoBounds.of(16.4852, 39.2934, 16.5150, 39.3100), GeoBounds.around(seedPath.toPositions()))
        assertNull(GeoBounds.around(emptyList()))
    }

    @Test
    fun `la distanza è in metri`() {
        // un grado di latitudine: circa 111 km
        val meters = GeoPoint(39.0, 16.0).distanceTo(GeoPoint(40.0, 16.0))
        assertTrue(abs(meters - 111_195) < 200)
        assertTrue(GeoBounds.of(16.0, 39.0, 16.02, 39.02).radiusMeters in 1_000.0..2_000.0)
    }

    @Test
    fun `toString non contiene coordinate`() {
        assertFalse("16.47" in GeoBounds.of(16.47, 39.29, 16.51, 39.31).toString())
    }

    @Test
    fun `la bbox vicino allo zero non usa la notazione scientifica`() {
        assertEquals("-0.0001,0.00005,0.001,0.002", GeoBounds.of(-0.0001, 0.00005, 0.001, 0.002).toQuery())
    }
}
