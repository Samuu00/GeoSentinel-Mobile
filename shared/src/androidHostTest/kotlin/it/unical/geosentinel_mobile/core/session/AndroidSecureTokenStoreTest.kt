package it.unical.geosentinel_mobile.core.session

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

// Sulla JVM l'Android Keystore non c'è: stessa cifratura AES-GCM con una chiave software.
// Il Keystore vero si prova sull'emulatore (androidDeviceTest).
class AndroidSecureTokenStoreTest {

    private val dir: File = createTempDirectory("session").toFile()
    private val file = File(dir, SESSION_FILE_NAME)
    private val scopes = mutableListOf<CoroutineScope>()
    private val key = newKey()
    private val tokens = AuthTokens("access-segreto-123", "refresh-segreto-456", Instant.fromEpochMilliseconds(1_759_400_000_000))

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    // Ogni istanza ha il suo scope: chiuderlo libera il file, come un riavvio dell'app.
    // Storage Okio e non quello di default: sulla JVM su Windows il default (File.renameTo) non sostituisce il file
    // alla seconda scrittura. Su Android e su Linux (CI) il default funziona; la logica provata qui è la stessa.
    private fun newDataStore() = PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it },
    )

    private fun newStore(key: SecretKey = this.key) = AndroidSecureTokenStore(newDataStore()) { key }

    // Si aspetta che lo scope sia davvero chiuso: il DataStore libera il file solo allora, e uno nuovo sullo stesso
    // file aperto prima lancia IllegalStateException ("multiple DataStores active"). Con il solo cancel() era una
    // corsa: passava su Windows e falliva nella CI Linux.
    private suspend fun restart() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    @AfterTest
    fun tearDown() {
        runBlocking { restart() }
        dir.deleteRecursively()
    }

    @Test
    fun `senza sessione salvata read restituisce null`() = runBlocking {
        assertNull(newStore().read())
    }

    @Test
    fun `dopo un riavvio la sessione c'e ancora, dopo clear no`() = runBlocking {
        newStore().write(tokens)
        restart()

        val store = newStore()
        assertEquals(tokens, store.read())

        store.clear()
        restart()
        assertNull(newStore().read())
    }

    @Test
    fun `il file non contiene i token in chiaro`() = runBlocking {
        newStore().write(tokens)
        restart()

        val content = file.readBytes().decodeToString()
        assertFalse("access-segreto-123" in content)
        assertFalse("refresh-segreto-456" in content)
    }

    @Test
    fun `con un'altra chiave, come un backup su un altro telefono, la sessione si cancella invece di andare in crash`() =
        runBlocking {
            newStore().write(tokens)
            restart()

            assertNull(newStore(key = newKey()).read())
            restart()
            // cancellata davvero: anche con la chiave giusta non c'è più
            assertNull(newStore().read())
        }

    @Test
    fun `dati troncati o rovinati si cancellano invece di andare in crash`() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit { it[byteArrayPreferencesKey("tokens")] = byteArrayOf(1, 2, 3) }

        assertNull(AndroidSecureTokenStore(dataStore) { key }.read())
    }
}
