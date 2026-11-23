// Test delle regole sulla lunghezza della password (passwordProblem), che il client controlla prima di inviare
// registrazione e cambio password. Le regole devono coincidere con quelle del backend, che resta l'arbitro finale.
package it.unical.geosentinel_mobile.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifica i limiti minimo e massimo della password e il modo in cui vengono contati. */
class PasswordRulesTest {

    /** Con 11 caratteri la password è troppo corta, con 12 è accettata. */
    @Test
    fun `sotto i 12 caratteri e troppo corta`() {
        assertEquals(PasswordProblem.TOO_SHORT, passwordProblem("a".repeat(11)))
        assertNull(passwordProblem("a".repeat(12)))
    }

    /**
     * Il massimo di 72 si conta in byte UTF-8, come fa il server: 40 lettere accentate
     * occupano 80 byte e quindi sono troppe, anche se i caratteri sono meno di 72.
     */
    @Test
    fun `il massimo si conta in byte UTF-8 - non in caratteri`() {
        assertNull(passwordProblem("a".repeat(72)))
        assertEquals(PasswordProblem.TOO_LONG, passwordProblem("a".repeat(73)))
        assertEquals(PasswordProblem.TOO_LONG, passwordProblem("è".repeat(40)))
        assertNull(passwordProblem("è".repeat(36)))
    }

    /**
     * Il minimo si conta in unità UTF-16, come String.length in Java sul backend: 6 emoji valgono 12 unità e la
     * password è valida, come lo sarebbe per il server.
     */
    @Test
    fun `il minimo si conta come il server - in unita UTF-16`() {
        assertNull(passwordProblem("🏔".repeat(6)))
    }
}
