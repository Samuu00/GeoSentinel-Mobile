// Test delle etichette mostrate per i sentieri: conversione di difficoltà e stato ricevuti dal server nei valori
// usati dalla UI, formattazione della lunghezza e testo "aggiornato alle" del catalogo.
package it.unical.geosentinel_mobile.trail.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.test.assertEquals

/** Verifica le funzioni che trasformano i dati grezzi dei sentieri in testi ed enum per l'interfaccia. */
class TrailLabelsTest {

    /**
     * Le quattro difficoltà del backend si convertono una a una; il valore di riserva del client generato e qualsiasi
     * stringa sconosciuta diventano UNKNOWN, così un valore nuovo lato server non fa crashare l'app.
     */
    @Test
    fun `la difficoltà usa le quattro del backend e una nuova diventa UNKNOWN`() {
        assertEquals(TrailDifficulty.EASY, TrailDifficulty.of("EASY"))
        assertEquals(TrailDifficulty.MODERATE, TrailDifficulty.of("MODERATE"))
        assertEquals(TrailDifficulty.DIFFICULT, TrailDifficulty.of("DIFFICULT"))
        assertEquals(TrailDifficulty.EXPERT, TrailDifficulty.of("EXPERT"))
        assertEquals(TrailDifficulty.UNKNOWN, TrailDifficulty.of("unknown_default_open_api"))
        assertEquals(TrailDifficulty.UNKNOWN, TrailDifficulty.of("UNKNOWN"))
    }

    /** Uno stato del sentiero noto viene riconosciuto, uno sconosciuto diventa UNKNOWN senza eccezioni. */
    @Test
    fun `uno stato nuovo del server diventa UNKNOWN senza crash`() {
        assertEquals(TrailState.CLOSED, TrailState.of("CLOSED"))
        assertEquals(TrailState.UNKNOWN, TrailState.of("unknown_default_open_api"))
        assertEquals(TrailState.UNKNOWN, TrailState.of("UNKNOWN"))
    }

    /** La lunghezza in metri si mostra in chilometri con un decimale arrotondato e la virgola come separatore. */
    @Test
    fun `la lunghezza è in km con un decimale e la virgola`() {
        assertEquals("2,8", formatKm(2830.4))
        assertEquals("0,0", formatKm(0.0))
        assertEquals("12,0", formatKm(11_960.0))
        assertEquals("0,1", formatKm(149.0))
    }

    /**
     * Il risultato dice se l'aggiornamento è di oggi e fornisce data e ora locali da mostrare. L'orario si converte
     * nel fuso orario di Roma, quindi le 12:05 UTC diventano le 14:05 in ora legale.
     */
    @Test
    fun `aggiornato alle con l'ora di oggi o con la data`() {
        val rome = TimeZone.of("Europe/Rome")
        val at = Instant.parse("2026-10-04T12:05:00Z")
        assertEquals(UpdatedAt(today = true, date = "04/10", time = "14:05"), updatedAt(at, Instant.parse("2026-10-04T18:00:00Z"), rome))
        assertEquals(false, updatedAt(at, Instant.parse("2026-10-05T08:00:00Z"), rome).today)
    }
}
