// Test di esempio creato insieme al progetto per l'insieme di test iosTest, che gira sul simulatore iOS nella CI.
// Non verifica logica dell'app: controlla solo che il target iOS compili ed esegua i test.
package it.unical.geosentinel_mobile

import kotlin.test.Test
import kotlin.test.assertEquals

/** Classe di prova minima dell'insieme di test specifico di iOS. */
class SharedLogicIOSTest {

    /** Verifica un'uguaglianza banale: se fallisce, il problema è nella configurazione dei test iOS e non nel codice. */
    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }
}