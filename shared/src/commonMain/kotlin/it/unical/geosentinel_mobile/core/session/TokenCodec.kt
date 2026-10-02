package it.unical.geosentinel_mobile.core.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
