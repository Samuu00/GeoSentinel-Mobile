// Archivio sicuro dei token di sessione su Android.
// I token vengono cifrati con AES-GCM usando una chiave generata nell'Android Keystore, che non lascia mai il
// dispositivo, e il risultato viene salvato in un DataStore dedicato ed escluso dal backup.
package it.unical.geosentinel_mobile.core.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Crea l'archivio Android: un DataStore nel file [SESSION_FILE_NAME] dentro [filesDir] e la chiave del Keystore. */
internal actual fun createSecureTokenStore(filesDir: String): SecureTokenStore = AndroidSecureTokenStore(
    dataStore = PreferenceDataStoreFactory.createWithPath { "$filesDir/$SESSION_FILE_NAME".toPath() },
    key = SessionKey::get,
)

/**
 * Token cifrati con AES-GCM e salvati in un DataStore riservato alla sessione. La chiave sta nell'Android Keystore
 * e non esce mai dal dispositivo; nel file ci sono solo il vettore di inizializzazione (IV) e il testo cifrato.
 *
 * @param key fornisce la chiave AES; è un parametro per poter eseguire i test sulla JVM, dove l'Android Keystore
 *   non esiste.
 */
internal class AndroidSecureTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val key: () -> SecretKey,
) : SecureTokenStore {

    /**
     * Legge e decifra i token. Se i dati non si possono decifrare li cancella e restituisce null.
     * Si lavora su `Dispatchers.IO` perché anche il Keystore passa da un servizio di sistema.
     */
    override suspend fun read(): AuthTokens? = withContext(Dispatchers.IO) {
        val blob = dataStore.data.first()[TOKENS] ?: return@withContext null
        try {
            decodeAuthTokens(decrypt(blob))
        } catch (_: GeneralSecurityException) {
            unreadable()
        } catch (_: ProviderException) {
            unreadable() // Errori interni del Keystore, che si verificano su alcuni dispositivi.
        } catch (_: IllegalArgumentException) {
            unreadable() // Dati troncati o in un formato non valido.
        }
    }

    /** Cifra i token e li salva al posto dei precedenti. */
    override suspend fun write(tokens: AuthTokens) = withContext(Dispatchers.IO) {
        val blob = encrypt(tokens.encode())
        dataStore.edit { it[TOKENS] = blob }
        Unit
    }

    /** Cancella i token salvati. */
    override suspend fun clear() = withContext(Dispatchers.IO) {
        dataStore.edit { it.remove(TOKENS) }
        Unit
    }

    /**
     * Gestisce dati salvati non più leggibili, per esempio dopo un backup ripristinato su un altro telefono o
     * dopo la cancellazione della chiave dal Keystore: si cancellano e l'utente riparte come ospite.
     */
    private suspend fun unreadable(): AuthTokens? {
        clear()
        return null
    }

    /**
     * Cifra [plain] e restituisce IV e testo cifrato concatenati. L'IV lo genera il Cipher, perché il Keystore non
     * accetta IV scelti dall'esterno; viene salvato in testa per poterlo rileggere alla decifratura.
     */
    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    /**
     * Separa IV e testo cifrato e decifra. GCM verifica anche l'integrità: dati alterati producono una
     * `GeneralSecurityException`, come i dati troppo corti per contenere l'IV.
     */
    private fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size <= IV_SIZE) throw GeneralSecurityException("Dati della sessione troncati")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE))
        }
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    private companion object {
        /** Chiave del DataStore sotto cui sta il blob cifrato. */
        val TOKENS = byteArrayPreferencesKey("tokens")
        /** Algoritmo di cifratura: AES in modalità GCM, che non richiede padding. */
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        /** Lunghezza dell'IV in byte, quella raccomandata per GCM. */
        const val IV_SIZE = 12
        /** Lunghezza in bit del tag di autenticazione di GCM. */
        const val TAG_BITS = 128
    }
}

/** Fornisce la chiave AES-256 della sessione nell'Android Keystore, creandola al primo utilizzo. */
internal object SessionKey {
    /** Nome con cui la chiave è registrata nel Keystore. */
    private const val ALIAS = "geosentinel_session"

    /**
     * Restituisce la chiave esistente o ne genera una nuova. È sincronizzata perché due chiamate contemporanee
     * al primo avvio non generino due chiavi diverse con lo stesso alias.
     */
    @Synchronized
    fun get(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }
}
