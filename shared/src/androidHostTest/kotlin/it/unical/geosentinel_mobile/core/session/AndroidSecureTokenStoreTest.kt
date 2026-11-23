// Test di AndroidSecureTokenStore, lo store dei token di sessione su Android, eseguito sulla JVM. Qui l'Android
// Keystore non esiste, quindi si usa la stessa cifratura AES-GCM con una chiave generata in software; il Keystore
// vero si prova sull'emulatore nei test di androidDeviceTest.
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

/** Verifica persistenza, cifratura e gestione dei dati non decifrabili dello store dei token. */
class AndroidSecureTokenStoreTest {

    /** Cartella temporanea del file della sessione, cancellata alla fine di ogni test. */
    private val dir: File = createTempDirectory("session").toFile()
    /** File in cui DataStore salva la sessione cifrata. */
    private val file = File(dir, SESSION_FILE_NAME)
    /** Scope dei DataStore aperti, da chiudere per simulare un riavvio. */
    private val scopes = mutableListOf<CoroutineScope>()
    /** Chiave AES usata dagli store del test, al posto di quella del Keystore. */
    private val key = newKey()
    /** Token di prova, riconoscibili per controllare che non compaiano in chiaro nel file. */
    private val tokens = AuthTokens("access-segreto-123", "refresh-segreto-456", Instant.fromEpochMilliseconds(1_759_400_000_000))

    /** Genera una nuova chiave AES a 256 bit. */
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    /**
     * Apre un DataStore sul file della sessione con uno scope dedicato: chiuderlo libera il file, come un riavvio
     * dell'app. Si usa lo storage Okio invece di quello predefinito perché sulla JVM in Windows quest'ultimo
     * (File.renameTo) non sostituisce il file alla seconda scrittura; su Android e su Linux il predefinito funziona e
     * la logica provata è la stessa.
     */
    private fun newDataStore() = PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it },
    )

    /** Crea uno store dei token sopra un nuovo DataStore, con la chiave indicata. */
    private fun newStore(key: SecretKey = this.key) = AndroidSecureTokenStore(newDataStore()) { key }

    /**
     * Simula un riavvio dell'app chiudendo tutti i DataStore aperti. Si aspetta che ogni scope sia chiuso davvero:
     * solo allora il DataStore libera il file, e aprirne uno nuovo prima lancerebbe IllegalStateException per più
     * DataStore attivi sullo stesso file. Con il solo cancel() il test dipendeva dai tempi e falliva su Linux.
     */
    private suspend fun restart() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    /** Chiude i DataStore e cancella la cartella temporanea. */
    @AfterTest
    fun tearDown() {
        runBlocking { restart() }
        dir.deleteRecursively()
    }

    /** Senza una sessione salvata la lettura restituisce null. */
    @Test
    fun `senza sessione salvata read restituisce null`() = runBlocking {
        assertNull(newStore().read())
    }

    /** La sessione salvata si rilegge dopo un riavvio; dopo clear e un altro riavvio non c'è più. */
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

    /** Il file sul disco non contiene i token in chiaro. */
    @Test
    fun `il file non contiene i token in chiaro`() = runBlocking {
        newStore().write(tokens)
        restart()

        val content = file.readBytes().decodeToString()
        assertFalse("access-segreto-123" in content)
        assertFalse("refresh-segreto-456" in content)
    }

    /**
     * Se la chiave è diversa (come quando un backup viene ripristinato su un altro telefono, dove la chiave del
     * Keystore non esiste) i dati non si decifrano: la sessione viene cancellata invece di far crashare l'app, e non
     * torna nemmeno con la chiave giusta.
     */
    @Test
    fun `con un'altra chiave, come un backup su un altro telefono, la sessione si cancella invece di andare in crash`() =
        runBlocking {
            newStore().write(tokens)
            restart()

            assertNull(newStore(key = newKey()).read())
            restart()
            assertNull(newStore().read())
        }

    /** Dati troncati o rovinati nel file vengono cancellati invece di far crashare l'app. */
    @Test
    fun `dati troncati o rovinati si cancellano invece di andare in crash`() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit { it[byteArrayPreferencesKey("tokens")] = byteArrayOf(1, 2, 3) }

        assertNull(AndroidSecureTokenStore(dataStore) { key }.read())
    }
}
