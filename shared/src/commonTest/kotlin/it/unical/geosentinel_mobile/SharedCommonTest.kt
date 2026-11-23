// Test di esempio creato insieme al progetto per l'insieme di test comune (commonTest), che gira sia sulla JVM
// sia su iOS. Non verifica logica dell'app: controlla solo che la configurazione dei test multipiattaforma funzioni.
package it.unical.geosentinel_mobile

import kotlin.test.Test
import kotlin.test.assertEquals

/** Classe di prova minima dell'insieme di test comune. */
class SharedCommonTest {

    /** Verifica un'uguaglianza banale: se fallisce, il problema è nella configurazione dei test e non nel codice. */
    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }
}