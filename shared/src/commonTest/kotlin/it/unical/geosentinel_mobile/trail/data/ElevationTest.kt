// Test delle funzioni che calcolano il profilo altimetrico di un sentiero: lettura della quota dalle tessere
// Terrarium, ricampionamento del percorso, calcolo del dislivello e stima del tempo di cammino.
package it.unical.geosentinel_mobile.trail.data

import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.distanceTo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifica i calcoli numerici usati per il profilo altimetrico e per il tempo di percorrenza. */
class ElevationTest {

    /**
     * Nel formato Terrarium la quota è codificata nei canali del colore: quota = R * 256 + G + B / 256 - 32768.
     * Un pixel costruito per 1500,5 m deve dare esattamente quella quota; il nero corrisponde a -32768 m.
     */
    @Test
    fun `quota terrarium dai colori del pixel`() {
        // 1500,5 m + 32768 = 34268,5: R = 133 vale 34048, G = 220 aggiunge 220, B = 128 aggiunge 0,5.
        val argb = (0xFF shl 24) or (133 shl 16) or (220 shl 8) or 128
        assertEquals(1500.5, terrariumMeters(argb), 0.001)
        assertEquals(-32768.0, terrariumMeters(0xFF000000.toInt()), 0.001)
    }

    /** Un punto della Sila a zoom 12 cade nella tessera attesa e in un pixel valido (da 0 a 255) di quella tessera. */
    @Test
    fun `tessera e pixel di un punto della Sila a zoom 12`() {
        val pixel = tilePixel(GeoPoint(lat = 39.33, lon = 16.55))
        assertEquals(2236, pixel.tileX)
        assertEquals(1560, pixel.tileY)
        assertTrue(pixel.px in 0..255 && pixel.py in 0..255)
    }

    /**
     * Il ricampionamento produce il numero di punti richiesto, parte dal primo punto, arriva all'ultimo e li
     * distanzia in modo uniforme (con una tolleranza di un metro), anche se i tratti originali sono diversi.
     */
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

    /**
     * Il dislivello positivo e negativo somma solo le variazioni sopra una soglia: oscillazioni di pochi metri,
     * dovute al rumore del modello del terreno, non contano.
     */
    @Test
    fun `il dislivello ignora il rumore sotto la soglia`() {
        assertEquals(100 to 50, climb(listOf(1000.0, 1002.0, 1000.0, 1050.0, 1100.0, 1098.0, 1050.0)))
        assertEquals(0 to 0, climb(listOf(1000.0, 1003.0, 999.0, 1002.0)))
    }

    /**
     * Il tempo di cammino segue la norma DIN 33466: si calcolano separatamente il tempo in piano e quello in
     * salita, e il totale è il maggiore più metà del minore.
     */
    @Test
    fun `tempo di cammino con la regola DIN 33466`() {
        // 8 km valgono 2 h in piano e 600 m di salita 2 h: 2 + 2 / 2 = 3 h.
        assertEquals(180, hikingMinutes(8000.0, 600, 0))
        // Solo piano: 4 km valgono un'ora.
        assertEquals(60, hikingMinutes(4000.0, 0, 0))
    }
}
