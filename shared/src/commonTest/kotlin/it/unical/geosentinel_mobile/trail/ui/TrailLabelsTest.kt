package it.unical.geosentinel_mobile.trail.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.test.assertEquals

class TrailLabelsTest {

    @Test
    fun `la difficoltà usa le quattro del backend e una nuova diventa UNKNOWN`() {
        assertEquals(TrailDifficulty.EASY, TrailDifficulty.of("EASY"))
        assertEquals(TrailDifficulty.MODERATE, TrailDifficulty.of("MODERATE"))
        assertEquals(TrailDifficulty.DIFFICULT, TrailDifficulty.of("DIFFICULT"))
        assertEquals(TrailDifficulty.EXPERT, TrailDifficulty.of("EXPERT"))
        assertEquals(TrailDifficulty.UNKNOWN, TrailDifficulty.of("unknown_default_open_api"))
        assertEquals(TrailDifficulty.UNKNOWN, TrailDifficulty.of("UNKNOWN"))
    }

    @Test
    fun `uno stato nuovo del server diventa UNKNOWN senza crash`() {
        assertEquals(TrailState.CLOSED, TrailState.of("CLOSED"))
        assertEquals(TrailState.UNKNOWN, TrailState.of("unknown_default_open_api"))
        assertEquals(TrailState.UNKNOWN, TrailState.of("UNKNOWN"))
    }

    @Test
    fun `la lunghezza è in km con un decimale e la virgola`() {
        assertEquals("2,8", formatKm(2830.4))
        assertEquals("0,0", formatKm(0.0))
        assertEquals("12,0", formatKm(11_960.0))
        assertEquals("0,1", formatKm(149.0))
    }

    @Test
    fun `aggiornato alle con l'ora di oggi o con la data`() {
        val rome = TimeZone.of("Europe/Rome")
        val at = Instant.parse("2026-10-04T12:05:00Z") // 14:05 a Roma
        assertEquals(UpdatedAt(today = true, date = "04/10", time = "14:05"), updatedAt(at, Instant.parse("2026-10-04T18:00:00Z"), rome))
        assertEquals(false, updatedAt(at, Instant.parse("2026-10-05T08:00:00Z"), rome).today)
    }
}
