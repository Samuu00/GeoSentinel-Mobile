// Test del calcolo astronomico di alba e tramonto (sunTimes, daylightAt) usato dalle testate dell'app per disegnare
// il cielo dell'ora reale e il chip con la luce che resta prima del tramonto.
package it.unical.geosentinel_mobile.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Confronta i calcoli con le effemeridi pubblicate per Roma, con una tolleranza di 4 minuti, e verifica le fasi
 * del cielo e i casi limite.
 */
class DaylightTest {
    /** Latitudine di Roma in gradi, punto di riferimento per cui esistono effemeridi pubblicate al minuto. */
    private val lat = 41.9028
    /** Longitudine di Roma in gradi. */
    private val lon = 12.4964

    /** Fallisce se [actual] è null o dista più di 4 minuti dall'istante atteso, scritto in formato ISO 8601. */
    private fun assertNear(expected: String, actual: Instant?) {
        assertNotNull(actual)
        val diff = (actual - Instant.parse(expected)).absoluteValue
        assertTrue(diff <= 4.minutes, "atteso $expected, calcolato $actual")
    }

    /** Al solstizio d'estate alba e tramonto a Roma coincidono con le effemeridi (orari in UTC). */
    @Test
    fun `solstizio estivo a Roma`() {
        val times = sunTimes(Instant.parse("2026-06-21T12:00:00Z"), lat, lon)
        assertNear("2026-06-21T03:35:00Z", times.sunrise)
        assertNear("2026-06-21T18:48:00Z", times.sunset)
    }

    /** Al solstizio d'inverno alba e tramonto a Roma coincidono con le effemeridi (orari in UTC). */
    @Test
    fun `solstizio invernale a Roma`() {
        val times = sunTimes(Instant.parse("2026-12-21T12:00:00Z"), lat, lon)
        assertNear("2026-12-21T06:33:00Z", times.sunrise)
        assertNear("2026-12-21T15:42:00Z", times.sunset)
    }

    /** Nel corso di una giornata estiva il cielo passa da notte a luce dorata, giorno, di nuovo luce dorata e notte. */
    @Test
    fun `fasi del cielo nella giornata`() {
        assertEquals(SkyPhase.NIGHT, daylightAt(Instant.parse("2026-06-21T01:00:00Z"), lat, lon).phase)
        assertEquals(SkyPhase.GOLDEN, daylightAt(Instant.parse("2026-06-21T04:00:00Z"), lat, lon).phase)
        assertEquals(SkyPhase.DAY, daylightAt(Instant.parse("2026-06-21T11:00:00Z"), lat, lon).phase)
        assertEquals(SkyPhase.GOLDEN, daylightAt(Instant.parse("2026-06-21T18:00:00Z"), lat, lon).phase)
        assertEquals(SkyPhase.NIGHT, daylightAt(Instant.parse("2026-06-21T21:00:00Z"), lat, lon).phase)
    }

    /**
     * Due ore prima del tramonto la luce che resta vale circa due ore; di notte non c'è luce che resta e viene
     * indicata invece l'alba del giorno dopo.
     */
    @Test
    fun `luce che resta solo di giorno e alba di domani dopo il tramonto`() {
        val noon = daylightAt(Instant.parse("2026-06-21T16:48:00Z"), lat, lon)
        val remaining = assertNotNull(noon.remaining)
        assertTrue(remaining in 116.minutes..124.minutes, "restano $remaining")
        val night = daylightAt(Instant.parse("2026-06-21T21:00:00Z"), lat, lon)
        assertNull(night.remaining)
        assertNear("2026-06-22T03:35:00Z", night.nextSunrise)
    }

    /** A 80 gradi di latitudine nel solstizio d'inverno il sole non sorge, quindi l'alba è null invece di un errore. */
    @Test
    fun `notte polare senza alba`() {
        val times = sunTimes(Instant.parse("2026-12-21T12:00:00Z"), 80.0, 15.0)
        assertNull(times.sunrise)
    }
}
