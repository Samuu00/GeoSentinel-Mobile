package it.unical.geosentinel_mobile.trail.data

import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.distanceTo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElevationTest {

    @Test
    fun `quota terrarium dai colori del pixel`() {
        // 32768 + 1500,5 m = 34268,5: R = 133 (34048), G = 220, B = 128 (0,5)
        val argb = (0xFF shl 24) or (133 shl 16) or (220 shl 8) or 128
        assertEquals(1500.5, terrariumMeters(argb), 0.001)
        assertEquals(-32768.0, terrariumMeters(0xFF000000.toInt()), 0.001)
    }

    @Test
    fun `tessera e pixel di un punto della Sila a zoom 12`() {
        val pixel = tilePixel(GeoPoint(lat = 39.33, lon = 16.55))
        assertEquals(2236, pixel.tileX)
        assertEquals(1560, pixel.tileY)
        assertTrue(pixel.px in 0..255 && pixel.py in 0..255)
    }

    @Test
    fun `ricampionamento a passo costante`() {
        val path = listOf(GeoPoint(39.0, 16.0), GeoPoint(39.0, 16.01), GeoPoint(39.0, 16.03))
        val points = resample(path, count = 4)
        assertEquals(4, points.size)
        assertEquals(path.first(), points.first())
        assertTrue(abs(points.last().lon - 16.03) < 1e-9)
        val steps = points.zipWithNext { a, b -> a.distanceTo(b) }
        steps.forEach { assertTrue(abs(it - steps.first()) < 1.0, "passi diversi: $steps") }
    }

    @Test
    fun `il dislivello ignora il rumore sotto la soglia`() {
        assertEquals(100 to 50, climb(listOf(1000.0, 1002.0, 1000.0, 1050.0, 1100.0, 1098.0, 1050.0)))
        assertEquals(0 to 0, climb(listOf(1000.0, 1003.0, 999.0, 1002.0)))
    }

    @Test
    fun `tempo di cammino con la regola DIN 33466`() {
        // 8 km e 600 m di salita: 2 h in piano, 2 h in salita: 2 + 2 / 2 = 3 h
        assertEquals(180, hikingMinutes(8000.0, 600, 0))
        // solo piano: 4 km = 1 h
        assertEquals(60, hikingMinutes(4000.0, 0, 0))
    }
}
