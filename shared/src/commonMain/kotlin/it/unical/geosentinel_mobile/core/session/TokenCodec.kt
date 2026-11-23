// Serializzazione dei token di sessione e lettura del soggetto dal JWT.
// Il formato dei byte è lo stesso su entrambe le piattaforme: su Android viene poi cifrato, su iOS
// finisce direttamente nel Keychain.
package it.unical.geosentinel_mobile.core.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

/** Rappresentazione JSON dei token salvati; la scadenza è in millisecondi dall'epoca Unix. */
@Serializable
private class StoredTokens(val access: String, val refresh: String, val accessExpiresAtMillis: Long)

/** Converte i token nei byte (JSON in UTF-8) da affidare all'archivio sicuro. */
internal fun AuthTokens.encode(): ByteArray =
    Json.encodeToString(StoredTokens(accessToken, refreshToken, accessExpiresAt.toEpochMilliseconds())).encodeToByteArray()

/**
 * Ricostruisce i token dai byte salvati con [encode].
 * Lancia `IllegalArgumentException` (di cui fa parte anche `SerializationException`) se i byte non sono un
 * salvataggio valido; gli archivi la intercettano e trattano il caso come "nessuna sessione".
 */
internal fun decodeAuthTokens(bytes: ByteArray): AuthTokens {
    val stored = Json.decodeFromString<StoredTokens>(bytes.decodeToString())
    return AuthTokens(stored.access, stored.refresh, Instant.fromEpochMilliseconds(stored.accessExpiresAtMillis))
}

/** Parser JSON per i claim del JWT: ignora tutti i campi tranne quelli dichiarati. */
private val subjectJson = Json { ignoreUnknownKeys = true }

/** I soli claim del JWT che interessano all'app. */
@Serializable
private class JwtClaims(val sub: String? = null)

/**
 * Legge il claim `sub` (l'id dell'utente) dall'access token senza verificarne la firma, che verifica il server.
 * Serve a sapere a quale utente appartengono le azioni in coda anche quando il profilo non è ancora stato caricato.
 * Restituisce null se il token non è un JWT leggibile.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun jwtSubject(accessToken: String): String? = try {
    val payload = accessToken.split('.').getOrNull(1) ?: return null
    val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload)
    subjectJson.decodeFromString<JwtClaims>(bytes.decodeToString()).sub
} catch (_: IllegalArgumentException) {
    null // Base64 o JSON non validi (anche SerializationException è una IllegalArgumentException).
}
