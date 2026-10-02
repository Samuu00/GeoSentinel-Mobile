package it.unical.geosentinel_mobile.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PasswordRulesTest {

    @Test
    fun `sotto i 12 caratteri e troppo corta`() {
        assertEquals(PasswordProblem.TOO_SHORT, passwordProblem("a".repeat(11)))
        assertNull(passwordProblem("a".repeat(12)))
    }

    @Test
    fun `il massimo si conta in byte UTF-8, non in caratteri`() {
        assertNull(passwordProblem("a".repeat(72)))
        assertEquals(PasswordProblem.TOO_LONG, passwordProblem("a".repeat(73)))
        // 40 caratteri accentati = 80 byte: troppi anche se i caratteri sono meno di 72
        assertEquals(PasswordProblem.TOO_LONG, passwordProblem("è".repeat(40)))
        assertNull(passwordProblem("è".repeat(36)))
    }

    @Test
    fun `il minimo si conta come il server, in unita UTF-16`() {
        // 6 emoji = 12 unità UTF-16 (come String.length in Java) e 24 byte: valida, come per il backend
        assertNull(passwordProblem("🏔".repeat(6)))
    }
}
