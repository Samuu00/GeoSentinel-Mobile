// Test di esempio creato insieme al progetto per l'insieme di test androidHostTest (JVM con le API Android).
// Non verifica logica dell'app: serve a controllare che la configurazione di questo insieme di test compili e giri.
package it.unical.geosentinel_mobile

import kotlin.test.Test
import kotlin.test.assertEquals

/** Classe di prova che tiene attivo l'insieme di test host di Android anche se tutti gli altri test venissero spostati. */
class SharedLogicAndroidHostTest {

    /** Verifica un'uguaglianza banale: se fallisce, il problema è nella configurazione dei test e non nel codice. */
    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }
}