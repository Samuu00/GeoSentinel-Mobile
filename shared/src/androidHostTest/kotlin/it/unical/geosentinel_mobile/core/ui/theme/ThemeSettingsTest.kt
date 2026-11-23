// Test di ThemeSettings, la classe che salva in DataStore la scelta del tema fatta dall'utente (Sistema, Chiaro o
// Scuro). Gira sulla JVM con un DataStore vero scritto in una cartella temporanea.
package it.unical.geosentinel_mobile.core.ui.theme

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifica il valore predefinito del tema, la sua persistenza e la gestione di valori non riconosciuti. */
class ThemeSettingsTest {

    /** Cartella temporanea che contiene il file delle preferenze, cancellata alla fine di ogni test. */
    private val dir: File = createTempDirectory("theme").toFile()
    /** Scope di tutti i DataStore aperti dal test, da chiudere alla fine. */
    private val scopes = mutableListOf<CoroutineScope>()

    /**
     * Apre un DataStore sul file di test con uno scope dedicato: chiudere lo scope libera il file, e riaprirlo
     * simula un riavvio dell'app.
     */
    private fun newDataStore() = CoroutineScope(Dispatchers.IO + SupervisorJob()).let { scope ->
        scopes += scope
        PreferenceDataStoreFactory.createWithPath(scope = scope) { File(dir, "test.preferences_pb").toOkioPath() }
    }

    /** Chiude tutti i DataStore aperti e cancella la cartella temporanea. */
    @AfterTest
    fun tearDown() {
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    /** Se l'utente non ha mai scelto, il tema segue quello del sistema. */
    @Test
    fun `senza scelta salvata il tema segue il sistema`() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, ThemeSettings(newDataStore()).mode.first())
    }

    /** Il tema scelto viene riletto da un nuovo DataStore aperto sullo stesso file, come dopo un riavvio. */
    @Test
    fun `la scelta resta dopo un riavvio`() = runBlocking {
        ThemeSettings(newDataStore()).setMode(ThemeMode.DARK)
        // Prima di riaprire il file si aspetta che il primo DataStore sia chiuso del tutto: con il solo cancel(), su Linux
        // il file risulta ancora aperto e DataStore segnala più istanze attive sullo stesso file.
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        assertEquals(ThemeMode.DARK, ThemeSettings(newDataStore()).mode.first())
    }

    /** Un valore salvato che non corrisponde a nessun tema (per esempio "SEPIA") viene trattato come tema di sistema. */
    @Test
    fun `un valore sconosciuto torna al tema di sistema`() = runBlocking {
        val store = newDataStore()
        store.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }
        assertEquals(ThemeMode.SYSTEM, ThemeSettings(store).mode.first())
    }
}
