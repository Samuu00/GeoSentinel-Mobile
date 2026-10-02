package it.unical.geosentinel_mobile.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant

class TokenCodecTest {

    private val tokens = AuthTokens("access-segreto", "refresh-segreto", Instant.fromEpochMilliseconds(1_759_400_000_123))

    @Test
    fun `i token salvati si rileggono uguali, scadenza al millisecondo compresa`() {
        assertEquals(tokens, decodeAuthTokens(tokens.encode()))
    }

    @Test
    fun `dati non validi lanciano IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { decodeAuthTokens("non json".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { decodeAuthTokens("""{"access":"a"}""".encodeToByteArray()) }
    }

    @Test
    fun `toString non stampa i token`() {
        val text = tokens.toString()

        assertFalse("access-segreto" in text)
        assertFalse("refresh-segreto" in text)
    }
}
