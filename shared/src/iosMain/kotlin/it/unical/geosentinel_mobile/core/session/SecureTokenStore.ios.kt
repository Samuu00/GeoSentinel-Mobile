// Archivio sicuro dei token di sessione su iOS, basato sul Keychain.
// Il Keychain si usa attraverso le API C di Security e CoreFoundation, chiamate da Kotlin/Native con l'interop C:
// per questo il file gestisce a mano la memoria degli oggetti CoreFoundation che crea.
// Viene compilato solo sul runner macOS della CI.
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
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
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

/** Crea l'archivio iOS. [filesDir] non serve, perché i token stanno nel Keychain e non in un file. */
internal actual fun createSecureTokenStore(filesDir: String): SecureTokenStore = KeychainTokenStore()

/**
 * Token salvati come voce "generic password" nel Keychain. Sono leggibili dopo il primo sblocco del telefono
 * (quindi anche a schermo bloccato, quando arriva una notifica push) e solo su questo dispositivo: l'accesso
 * `ThisDeviceOnly` li esclude dai backup e dal trasferimento su un altro iPhone.
 *
 * Gli errori del Keychain diversi da "voce non trovata" vengono lanciati come eccezione; li gestisce
 * `SessionManager`, che li trasforma in un errore o nello stato di ospite.
 */
internal class KeychainTokenStore : SecureTokenStore {

    /**
     * Legge i token. Alla prima lettura dopo un'installazione cancella prima l'eventuale sessione rimasta; se i dati
     * salvati non sono in un formato valido li cancella e restituisce null.
     */
    override suspend fun read(): AuthTokens? = withContext(Dispatchers.IO) {
        clearIfReinstalled()
        val bytes = keychainRead() ?: return@withContext null
        try {
            decodeAuthTokens(bytes)
        } catch (_: IllegalArgumentException) {
            keychainDelete() // Formato non valido: si riparte come ospite.
            null
        }
    }

    /**
     * Salva i token aggiornando la voce esistente e creandola solo se manca. Non si usa "cancella e aggiungi"
     * perché lascerebbe un momento senza token salvati: se l'aggiunta fallisse, i token andrebbero persi mentre il
     * server li ha già ruotati. Gli attributi di accesso li stabilisce l'aggiunta e l'aggiornamento li conserva.
     */
    override suspend fun write(tokens: AuthTokens) = withContext(Dispatchers.IO) {
        val bytes = tokens.encode()
        if (!keychainUpdate(bytes)) keychainAdd(bytes)
    }

    /** Cancella la voce della sessione dal Keychain. */
    override suspend fun clear() = withContext(Dispatchers.IO) { keychainDelete() }
}

// Attributo "service" della voce nel Keychain: identifica la sessione dell'app.
private const val SERVICE = "it.unical.geosentinel_mobile.session"
// Chiave in UserDefaults che segna "Keychain già controllato dopo questa installazione".
private const val INSTALLED_KEY = "session.keychainChecked"
// Attributo "account" della voce nel Keychain.
private const val ACCOUNT = "tokens"

/** Errore del Keychain con il codice OSStatus restituito dal sistema. */
private class KeychainException(status: Int) : Exception("Keychain OSStatus $status")

/**
 * Costruisce la query che individua la voce della sessione (classe, service e account), aggiunge gli attributi
 * [extra] e la passa a [block], restituendone il risultato. Secondo la regola "Create" di CoreFoundation chi crea un
 * oggetto deve rilasciarlo: per questo nel `finally` si liberano la query e le stringhe create qui.
 */
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

/** Legge i byte della voce; null se la voce non esiste. Lancia [KeychainException] per gli altri errori. */
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

/**
 * Crea la voce con i byte dati e l'accesso "dopo il primo sblocco, solo su questo dispositivo".
 * Lancia [KeychainException] se il sistema rifiuta l'aggiunta.
 */
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

/**
 * Cancella la sessione rimasta da un'installazione precedente. Su iOS il Keychain sopravvive alla disinstallazione
 * dell'app, UserDefaults no: se il segno in UserDefaults manca, l'app è stata appena installata e un'eventuale voce
 * nel Keychain appartiene a una vecchia installazione (per esempio un telefono ceduto). Il segno si scrive solo
 * dopo la cancellazione: se il Keychain non risponde, l'eccezione interrompe la funzione e si riprova alla lettura
 * successiva.
 */
private fun clearIfReinstalled() {
    val defaults = NSUserDefaults.standardUserDefaults
    if (defaults.boolForKey(INSTALLED_KEY)) return
    keychainDelete()
    defaults.setBool(true, INSTALLED_KEY)
}

/**
 * Sostituisce i byte della voce esistente. Restituisce false se la voce non esiste ancora; lancia
 * [KeychainException] per gli altri errori.
 */
private fun keychainUpdate(bytes: ByteArray): Boolean {
    val data = CFBridgingRetain(bytes.toNSData())
    val attributes = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    try {
        CFDictionaryAddValue(attributes, kSecValueData, data)
        return withSessionQuery(emptyMap()) { query ->
            when (val status = SecItemUpdate(query, attributes)) {
                errSecSuccess -> true
                errSecItemNotFound -> false
                else -> throw KeychainException(status)
            }
        }
    } finally {
        CFRelease(attributes)
        CFRelease(data)
    }
}

/** Cancella la voce; non è un errore se la voce non esiste. */
private fun keychainDelete() = withSessionQuery(emptyMap()) { query ->
    val status = SecItemDelete(query)
    if (status != errSecSuccess && status != errSecItemNotFound) throw KeychainException(status)
}

/** Copia i byte in un `NSData`, bloccando l'array in memoria durante la copia. */
private fun ByteArray.toNSData(): NSData = usePinned { NSData.dataWithBytes(it.addressOf(0), size.convert()) }

/** Copia il contenuto di un `NSData` in un nuovo `ByteArray`. */
private fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}
