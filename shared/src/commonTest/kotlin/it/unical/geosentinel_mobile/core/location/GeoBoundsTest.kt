// Test di GeoBounds e delle funzioni geografiche di base: conversione dei percorsi GeoJSON in posizioni della mappa,
// riquadri (bbox) inviati al backend, distanze in metri. L'ordine delle coordinate ([lon, lat] in GeoJSON) è una
// fonte classica di errori, per questo ha test dedicati.
package it.unical.geosentinel_mobile.core.location

import it.unical.geosentinel_mobile.api.model.GeoJsonLineString
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifica la gestione delle coordinate e dei riquadri geografici usati da mappa e richieste al server. */
class GeoBoundsTest {

    /** Percorso del primo sentiero dei dati di prova del backend (in Sila), nel formato GeoJSON dell'API: [lon, lat]. */
    private val seedPath = GeoJsonLineString(
        coordinates = listOf(listOf(16.4852, 39.2934), listOf(16.4910, 39.2980), listOf(16.5020, 39.3050), listOf(16.5150, 39.3100)),
        type = "LineString",
    )


    /**
     * Il percorso convertito resta in Calabria (longitudine circa 16, latitudine circa 39). Se l'ordine di longitudine
     * e latitudine venisse invertito, i punti finirebbero nel Golfo di Aden.
     */
    @Test
    fun `il percorso del seed si disegna in Calabria e non nel Golfo di Aden`() {
        val positions = seedPath.toPositions()

        assertEquals(4, positions.size)
        positions.forEach { position ->
            assertTrue(position.longitude in 15.5..17.5, "longitudine fuori dalla Calabria")
            assertTrue(position.latitude in 38.5..40.0, "latitudine fuori dalla Calabria")
        }
        assertEquals(16.4852, positions.first().longitude)
        assertEquals(39.2934, positions.first().latitude)
    }

    /** Le coppie di coordinate con un solo valore vengono saltate invece di far fallire la conversione. */
    @Test
    fun `le coppie malformate si saltano`() {
        val path = GeoJsonLineString(coordinates = listOf(listOf(16.48, 39.29), listOf(16.5), listOf(16.51, 39.31)), type = "LineString")

        assertEquals(2, path.toPositions().size)
    }


    /** Il riquadro si invia al backend come "minLon,minLat,maxLon,maxLat". */
    @Test
    fun `la bbox va al backend come minLon minLat maxLon maxLat`() {
        assertEquals("16.47,39.29,16.51,39.31", GeoBounds.of(16.47, 39.29, 16.51, 39.31).toQuery())
    }

    /**
     * Un riquadro oltre i limiti del mondo viene ridotto a longitudine da -180 a 180 e latitudine da -90 a 90; un
     * riquadro con minimo uguale al massimo non è valido, perché il backend richiede il minimo strettamente minore.
     */
    @Test
    fun `la bbox resta dentro i limiti del mondo`() {
        val bounds = GeoBounds.of(-200.0, -95.0, 200.0, 95.0)

        assertEquals("-180.0,-90.0,180.0,90.0", bounds.toQuery())
        assertTrue(bounds.isValid)
        assertFalse(GeoBounds.of(16.5, 39.3, 16.5, 39.4).isValid, "il backend vuole min minore di max")
    }

    /** expandedBy allarga il riquadro dello stesso margine in gradi su ogni lato; contains controlla l'inclusione completa. */
    @Test
    fun `contains ed expandedBy`() {
        val view = GeoBounds.of(16.0, 39.0, 17.0, 40.0)
        val area = view.expandedBy(0.5)

        assertEquals(GeoBounds.of(15.5, 38.5, 17.5, 40.5), area)
        assertTrue(view in area)
        assertFalse(area in view)
        assertFalse(GeoBounds.of(17.2, 39.0, 17.8, 40.0) in area)
    }

    /** around calcola il riquadro che contiene tutti i punti di un percorso, e restituisce null per una lista vuota. */
    @Test
    fun `around trova il riquadro del percorso`() {
        assertEquals(GeoBounds.of(16.4852, 39.2934, 16.5150, 39.3100), GeoBounds.around(seedPath.toPositions()))
        assertNull(GeoBounds.around(emptyList()))
    }

    /**
     * Il riquadro attorno a un punto ha i lati alla distanza richiesta (50 km, con 200 m di tolleranza) sia in
     * latitudine sia in longitudine, ed è centrato sul punto.
     */
    @Test
    fun `il riquadro attorno a un punto ha i lati alla distanza chiesta`() {
        val center = GeoPoint(39.3, 16.5)
        val box = GeoBounds.around(center, 50_000.0)
        assertTrue(abs(center.distanceTo(GeoPoint(box.maxLat, center.lon)) - 50_000) < 200)
        assertTrue(abs(center.distanceTo(GeoPoint(center.lat, box.minLon)) - 50_000) < 200)
        assertEquals(center.lat, box.center.lat, 1e-9)
        assertEquals(center.lon, box.center.lon, 1e-9)
    }

    /** L'intersezione di due riquadri è la parte comune, null se non si toccano; within(null) lascia il riquadro invariato. */
    @Test
    fun `l'intersezione è la parte comune e null se i riquadri non si toccano`() {
        val a = GeoBounds.of(16.0, 39.0, 17.0, 40.0)
        assertEquals(GeoBounds.of(16.5, 39.5, 17.0, 40.0), a intersect GeoBounds.of(16.5, 39.5, 18.0, 41.0))
        assertNull(a intersect GeoBounds.of(17.5, 39.0, 18.0, 40.0))
        assertEquals(a, a.within(null))
    }

    /**
     * La distanza tra due punti è in metri: un grado di latitudine vale circa 111 km. Anche il raggio di un riquadro
     * piccolo è espresso in metri.
     */
    @Test
    fun `la distanza è in metri`() {
        val meters = GeoPoint(39.0, 16.0).distanceTo(GeoPoint(40.0, 16.0))
        assertTrue(abs(meters - 111_195) < 200)
        assertTrue(GeoBounds.of(16.0, 39.0, 16.02, 39.02).radiusMeters in 1_000.0..2_000.0)
    }

    /** Il toString di un riquadro non contiene coordinate, perché la posizione dell'utente non deve finire nei log. */
    @Test
    fun `toString non contiene coordinate`() {
        assertFalse("16.47" in GeoBounds.of(16.47, 39.29, 16.51, 39.31).toString())
    }

    /** Valori molto vicini allo zero si scrivono in notazione decimale e non in notazione scientifica. */
    @Test
    fun `la bbox vicino allo zero non usa la notazione scientifica`() {
        assertEquals("-0.0001,0.00005,0.001,0.002", GeoBounds.of(-0.0001, 0.00005, 0.001, 0.002).toQuery())
    }
}
