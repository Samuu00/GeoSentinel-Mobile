// Test di telUri, la funzione che trasforma un numero di telefono scritto a mano (per esempio quello di un rifugio)
// nell'URI "tel:" passato al compositore del telefono.
package it.unical.geosentinel_mobile.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifica che telUri tenga solo le cifre e il segno più iniziale. */
class TelUriTest {

    /**
     * Un numero con spazi, parentesi e trattini diventa un URI con sole cifre; anche i caratteri dei codici di
     * servizio (asterisco e cancelletto) vengono scartati, così dall'app non si possono comporre comandi USSD.
     */
    @Test
    fun `nel compositore vanno solo cifre e il piu iniziale`() {
        assertEquals("tel:+390984118001", telUri(" +39 (0984) 118-001 "))
        assertEquals("tel:112", telUri("112"))
        assertEquals("tel:21", telUri("*#21#"))
    }
}
