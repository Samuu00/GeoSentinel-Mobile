// Test della ricerca sulla mappa (searchMap), che filtra sentieri, POI e rifugi in base al testo digitato
// dall'utente e li ordina per pertinenza.
package it.unical.geosentinel_mobile.trail.ui

import it.unical.geosentinel_mobile.api.model.GeoJsonLineString
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifica l'ordine dei risultati e la normalizzazione del testo cercato. */
class MapSearchTest {

    /** Crea un sentiero minimo con il nome indicato, usato come dato di ingresso della ricerca. */
    private fun trail(name: String) = TrailSummaryResponse(
        id = name,
        name = name,
        difficulty = TrailSummaryResponse.Difficulty.EASY,
        lengthM = 1000.0,
        status = TrailSummaryResponse.Status.OPEN,
        path = GeoJsonLineString(type = "LineString", coordinates = listOf(listOf(16.0, 39.0), listOf(16.1, 39.1))),
    )

    /** I nomi che iniziano con il testo cercato vengono prima di quelli che lo contengono soltanto; gli altri sono esclusi. */
    @Test
    fun `prima i nomi che iniziano con la ricerca poi quelli che la contengono`() {
        val hits = searchMap("sila", listOf(trail("Anello della Sila"), trail("Sila Grande"), trail("Pollino")), emptyList(), emptyList())
        assertEquals(listOf("Sila Grande", "Anello della Sila"), hits.map { it.name })
    }

    /** La ricerca ignora maiuscole e accenti ("CITTA" trova "città") e una ricerca fatta di soli spazi non dà risultati. */
    @Test
    fun `senza maiuscole ne accenti e niente con la ricerca vuota`() {
        assertEquals(1, searchMap("CITTA", listOf(trail("Sentiero della città")), emptyList(), emptyList()).size)
        assertTrue(searchMap("  ", listOf(trail("Sila")), emptyList(), emptyList()).isEmpty())
    }
}
