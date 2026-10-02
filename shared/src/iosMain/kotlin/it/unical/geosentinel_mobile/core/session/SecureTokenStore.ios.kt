@file:OptIn(ExperimentalForeignApi::class)

package it.unical.geosentinel_mobile.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

internal actual fun createSecureTokenStore(filesDir: String): SecureTokenStore = KeychainTokenStore()

/**
 * Token nel Keychain, leggibili dopo il primo sblocco (anche con lo schermo bloccato, per le push) e solo su questo
 * dispositivo: `ThisDeviceOnly` li esclude dai backup e dal trasferimento su un altro iPhone.
 */
internal class KeychainTokenStore : SecureTokenStore {

    override suspend fun read(): AuthTokens? = withContext(Dispatchers.IO) {
        val bytes = keychainRead() ?: return@withContext null
        try {
            decodeAuthTokens(bytes)
        } catch (_: IllegalArgumentException) {
            keychainDelete() // formato non valido: si riparte da ospite
            null
        }
    }

    // Delete + add invece di update: una sola voce, sempre con gli stessi attributi di accesso
    override suspend fun write(tokens: AuthTokens) = withContext(Dispatchers.IO) {
        keychainDelete()
        keychainAdd(tokens.encode())
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { keychainDelete() }
}

private const val SERVICE = "it.unical.geosentinel_mobile.session"
private const val ACCOUNT = "tokens"

private class KeychainException(status: Int) : Exception("Keychain OSStatus $status")

// Query sulla voce della sessione, più gli attributi extra. Libera tutto ciò che crea (regola "Create" di CF).
private inline fun <T> withSessionQuery(extra: Map<CFStringRef?, CFTypeRef?>, block: (CFDictionaryRef?) -> T): T {
    val service = CFBridgingRetain(SERVICE)
    val account = CFBridgingRetain(ACCOUNT)
    val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    try {
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, service)
        CFDictionaryAddValue(query, kSecAttrAccount, account)
        extra.forEach { (key, value) -> CFDictionaryAddValue(query, key, value) }
        return block(query)
    } finally {
        CFRelease(query)
        CFRelease(service)
        CFRelease(account)
    }
}

private fun keychainRead(): ByteArray? =
    withSessionQuery(mapOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)) { query ->
        memScoped {
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> (CFBridgingRelease(result.value) as NSData).toByteArray()
                errSecItemNotFound -> null
                else -> throw KeychainException(status)
            }
        }
    }

private fun keychainAdd(bytes: ByteArray) {
    val data = CFBridgingRetain(bytes.toNSData())
    try {
        val extra = mapOf(kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        withSessionQuery(extra) { query ->
            val status = SecItemAdd(query, null)
            if (status != errSecSuccess) throw KeychainException(status)
        }
    } finally {
        CFRelease(data)
    }
}

private fun keychainDelete() = withSessionQuery(emptyMap()) { query ->
    val status = SecItemDelete(query)
    if (status != errSecSuccess && status != errSecItemNotFound) throw KeychainException(status)
}

private fun ByteArray.toNSData(): NSData = usePinned { NSData.dataWithBytes(it.addressOf(0), size.convert()) }

private fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}
