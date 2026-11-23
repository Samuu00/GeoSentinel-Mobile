// Test delle funzioni di testo usate nel dettaglio dei sentieri: la separazione del numero dal nome, l'estrazione
// di segnavia e scala CAI dalla descrizione importata da OpenStreetMap e la formattazione delle coordinate.
package it.unical.geosentinel_mobile.trail.ui

import it.unical.geosentinel_mobile.core.ui.coordinatesLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifica come i testi grezzi dei sentieri vengono trasformati in dati da mostrare. */
class TrailTextTest {

    /** "Sentiero 404: ..." viene diviso nel numero (mostrato sopra) e nel titolo; un nome senza numero resta intero. */
    @Test
    fun `il numero del sentiero va sopra il titolo`() {
        assertEquals("404" to "Camigliatello Silano – Spezzano Piccolo", splitTrailName("Sentiero 404: Camigliatello Silano – Spezzano Piccolo"))
        assertEquals(null to "Anello dell'Ariamacina", splitTrailName("Anello dell'Ariamacina"))
    }

    /**
     * Dalla descrizione si estraggono il segnavia e la scala di difficoltà CAI come campi separati, mentre la riga
     * con la fonte dei dati viene tolta dal testo mostrato.
     */
    @Test
    fun `segnavia e scala CAI diventano dati e la fonte sparisce`() {
        val facts = trailFacts("Da San Nicola a Carrumanco\nSegnavia 441\nScala CAI: E\nDati © OpenStreetMap contributors (ODbL).")
        assertEquals("441", facts.waymark)
        assertEquals("E", facts.caiScale)
        assertEquals("Da San Nicola a Carrumanco", facts.text)
    }

    /** Se la descrizione contiene solo dati e la fonte non resta testo da mostrare; una descrizione assente non dà dati. */
    @Test
    fun `una descrizione fatta solo di dati non lascia testo`() {
        val facts = trailFacts("Segnavia 12\nDati © OpenStreetMap contributors (ODbL).")
        assertNull(facts.text)
        assertNull(trailFacts(null).waymark)
    }

    /** Le coordinate si mostrano con quattro decimali, la virgola italiana e i punti cardinali (O per ovest). */
    @Test
    fun `coordinate a quattro decimali con i punti cardinali`() {
        assertEquals("39,3121 N · 16,4211 E", coordinatesLabel(39.31214, 16.42108))
        assertEquals("0,5000 S · 3,0001 O", coordinatesLabel(-0.5, -3.00006))
    }
}
