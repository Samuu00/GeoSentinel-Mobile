// Test delle funzioni sugli importi in euro (M12): formato italiano, lettura del testo del gestore e totale del soggiorno.
package it.unical.geosentinel_mobile.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {

    @Test
    fun formatsCentsInItalianFormat() {
        assertEquals("0,00\u00A0€", formatEuro(0))
        assertEquals("35,00\u00A0€", formatEuro(3500))
        assertEquals("0,05\u00A0€", formatEuro(5))
        assertEquals("1.000,00\u00A0€", formatEuro(100_000))
        assertEquals("1.234,56\u00A0€", formatEuro(123_456))
        assertEquals(-1, formatEuro(3500).indexOf(' '), "nessuno spazio normale: l'importo non si spezza")
    }

    @Test
    fun parsesCommaAndDotWithoutDouble() {
        assertEquals(0, parseEuroToCents("0"))
        assertEquals(3500, parseEuroToCents("35"))
        assertEquals(3550, parseEuroToCents("35,5"))
        assertEquals(3550, parseEuroToCents("35.50"))
        assertEquals(1999, parseEuroToCents(" 19,99 "))
        assertEquals(100_000, parseEuroToCents("1000"))
    }

    @Test
    fun rejectsInvalidValues() {
        listOf("", "abc", "-1", "35,555", "1000,01", "10000", "1.000,00", "35,", ",5", "3 5").forEach {
            assertNull(parseEuroToCents(it), "\"$it\" non dovrebbe essere un prezzo")
        }
    }

    @Test
    fun inputRoundTripsWithParse() {
        assertEquals("", centsToEuroInput(null))
        assertEquals("35,00", centsToEuroInput(3500))
        assertEquals("1000,00", centsToEuroInput(100_000))
        listOf(0, 5, 3550, 100_000).forEach { assertEquals(it, parseEuroToCents(centsToEuroInput(it))) }
    }

    @Test
    fun stayTotalIsPriceTimesNightsTimesGuests() {
        assertEquals(21_000, stayTotalCents(3500, nights = 3, guests = 2))
        assertNull(stayTotalCents(null, nights = 3, guests = 2))
        assertEquals(28_000_000, stayTotalCents(100_000, nights = 14, guests = 20))
    }
}
