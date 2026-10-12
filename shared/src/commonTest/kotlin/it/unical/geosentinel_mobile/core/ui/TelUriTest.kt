package it.unical.geosentinel_mobile.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TelUriTest {

    @Test
    fun `nel compositore vanno solo cifre e il piu iniziale`() {
        assertEquals("tel:+390984118001", telUri(" +39 (0984) 118-001 "))
        assertEquals("tel:112", telUri("112"))
        // i codici di servizio non passano
        assertEquals("tel:21", telUri("*#21#"))
    }
}
