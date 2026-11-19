package it.unical.geosentinel_mobile.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DaylightTest {
    // Roma (41,9 N, 12,5 E): effemeridi pubblicate, al minuto
    private val lat = 41.9028
    private val lon = 12.4964

    private fun assertNear(expected: String, actual: Instant?) {
        assertNotNull(actual)
        val diff = (actual - Instant.parse(expected)).absoluteValue
        assertTrue(diff <= 4.minutes, "atteso $expected, calcolato $actual")
    }

    @Test
    fun `solstizio estivo a Roma`() {
        val times = sunTimes(Instant.parse("2026-06-21T12:00:00Z"), lat, lon)
        assertNear("2026-06-21T03:35:00Z", times.sunrise)
        assertNear("2026-06-21T18:48:00Z", times.sunset)
    }

    @Test
    fun `solstizio invernale a Roma`() {
        val times = sunTimes(Instant.parse("2026-12-21T12:00:00Z"), lat, lon)
        assertNear("2026-12-21T06:33:00Z", times.sunrise)
        assertNear("2026-12-21T15:42:00Z", times.sunset)
    }

    @Test
    fun `fasi del cielo nella giornata`() {
        assertEquals(SkyPhase.NIGHT, daylightAt(Instant.parse("2026-06-21T01:00:00Z"), lat, lon).phase)
        assertEquals(SkyPhase.GOLDEN, daylightAt(Instant.parse("2026-06-21T04:00:00Z"), lat, lon).phase)
        assertEquals(SkyPhase.DAY, daylightAt(Instant.parse("2026-06-21T11:00:00Z"), lat, lon).phase)
        assertEquals(SkyPhase.GOLDEN, daylightAt(Instant.parse("2026-06-21T18:00:00Z"), lat, lon).phase)
        assertEquals(SkyPhase.NIGHT, daylightAt(Instant.parse("2026-06-21T21:00:00Z"), lat, lon).phase)
    }

    @Test
    fun `luce che resta solo di giorno e alba di domani dopo il tramonto`() {
        val noon = daylightAt(Instant.parse("2026-06-21T16:48:00Z"), lat, lon)
        val remaining = assertNotNull(noon.remaining)
        assertTrue(remaining in 116.minutes..124.minutes, "restano $remaining")
        val night = daylightAt(Instant.parse("2026-06-21T21:00:00Z"), lat, lon)
        assertNull(night.remaining)
        assertNear("2026-06-22T03:35:00Z", night.nextSunrise)
    }

    @Test
    fun `notte polare senza alba`() {
        val times = sunTimes(Instant.parse("2026-12-21T12:00:00Z"), 80.0, 15.0)
        assertNull(times.sunrise)
    }
}
