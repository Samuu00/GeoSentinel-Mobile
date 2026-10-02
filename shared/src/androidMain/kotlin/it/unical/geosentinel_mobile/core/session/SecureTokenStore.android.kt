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

internal actual fun createSecureTokenStore(filesDir: String): SecureTokenStore = AndroidSecureTokenStore(
    dataStore = PreferenceDataStoreFactory.createWithPath { "$filesDir/$SESSION_FILE_NAME".toPath() },
    key = SessionKey::get,
)

/**
 * Token cifrati con AES-GCM e salvati in un DataStore tutto loro. La chiave sta nell'Android Keystore e non esce
 * mai dal dispositivo; nel file ci sono solo l'IV e il testo cifrato. [key] è un parametro per i test sulla JVM,
 * dove l'Android Keystore non c'è.
 */
internal class AndroidSecureTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val key: () -> SecretKey,
) : SecureTokenStore {

    // Il Keystore passa da un servizio di sistema: anche lui fuori dal main thread
    override suspend fun read(): AuthTokens? = withContext(Dispatchers.IO) {
        val blob = dataStore.data.first()[TOKENS] ?: return@withContext null
        try {
            decodeAuthTokens(decrypt(blob))
        } catch (_: GeneralSecurityException) {
            unreadable()
        } catch (_: ProviderException) {
            unreadable() // errori del Keystore su alcuni dispositivi
        } catch (_: IllegalArgumentException) {
            unreadable() // dati troncati o formato non valido
        }
    }

    override suspend fun write(tokens: AuthTokens) = withContext(Dispatchers.IO) {
        val blob = encrypt(tokens.encode())
        dataStore.edit { it[TOKENS] = blob }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        dataStore.edit { it.remove(TOKENS) }
        Unit
    }

    // Es. backup ripristinato su un altro telefono, o chiave del Keystore cancellata: si riparte da ospite
    private suspend fun unreadable(): AuthTokens? {
        clear()
        return null
    }

    // IV generato dal Cipher (il Keystore non accetta IV scelti da fuori), salvato in testa al testo cifrato
    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size <= IV_SIZE) throw GeneralSecurityException("Dati della sessione troncati")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE))
        }
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    private companion object {
        val TOKENS = byteArrayPreferencesKey("tokens")
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}

// Chiave AES-256 nell'Android Keystore, creata al primo uso
internal object SessionKey {
    private const val ALIAS = "geosentinel_session"

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
