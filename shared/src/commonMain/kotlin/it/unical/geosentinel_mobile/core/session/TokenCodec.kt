package it.unical.geosentinel_mobile.core.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

// Formato dei token salvati, uguale su Android (poi cifrato) e su iOS (nel Keychain)
@Serializable
private class StoredTokens(val access: String, val refresh: String, val accessExpiresAtMillis: Long)

internal fun AuthTokens.encode(): ByteArray =
    Json.encodeToString(StoredTokens(accessToken, refreshToken, accessExpiresAt.toEpochMilliseconds())).encodeToByteArray()

// IllegalArgumentException (anche SerializationException) se i byte non sono un salvataggio valido
internal fun decodeAuthTokens(bytes: ByteArray): AuthTokens {
    val stored = Json.decodeFromString<StoredTokens>(bytes.decodeToString())
    return AuthTokens(stored.access, stored.refresh, Instant.fromEpochMilliseconds(stored.accessExpiresAtMillis))
}

private val subjectJson = Json { ignoreUnknownKeys = true }

@Serializable
private class JwtClaims(val sub: String? = null)

// Il `sub` dell'access token, senza verificarne la firma (la verifica il server): null se il token non è un JWT leggibile
@OptIn(ExperimentalEncodingApi::class)
internal fun jwtSubject(accessToken: String): String? = try {
    val payload = accessToken.split('.').getOrNull(1) ?: return null
    val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload)
    subjectJson.decodeFromString<JwtClaims>(bytes.decodeToString()).sub
} catch (_: IllegalArgumentException) {
    null // base64 o JSON non validi (SerializationException è una IllegalArgumentException)
}
