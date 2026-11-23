// Test della codifica dei token di sessione (AuthTokens.encode e decodeAuthTokens), il formato con cui i token
// vengono salvati nello store sicuro del dispositivo.
package it.unical.geosentinel_mobile.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant

/** Verifica che la codifica dei token sia reversibile, rifiuti dati corrotti e non esponga i token nei log. */
class TokenCodecTest {

    /** Token di esempio con una scadenza che ha anche i millisecondi, per controllare che non vadano persi. */
    private val tokens = AuthTokens("access-segreto", "refresh-segreto", Instant.fromEpochMilliseconds(1_759_400_000_123))

    /** Codificare e poi decodificare restituisce esattamente gli stessi token, scadenza compresa. */
    @Test
    fun `i token salvati si rileggono uguali - scadenza al millisecondo compresa`() {
        assertEquals(tokens, decodeAuthTokens(tokens.encode()))
    }

    /** Un contenuto che non è JSON, o a cui manca un campo, produce IllegalArgumentException invece di token parziali. */
    @Test
    fun `dati non validi lanciano IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { decodeAuthTokens("non json".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { decodeAuthTokens("""{"access":"a"}""".encodeToByteArray()) }
    }

    /** Il toString di AuthTokens non contiene né l'access token né il refresh token, così non finiscono nei log. */
    @Test
    fun `toString non stampa i token`() {
        val text = tokens.toString()

        assertFalse("access-segreto" in text)
        assertFalse("refresh-segreto" in text)
    }
}
