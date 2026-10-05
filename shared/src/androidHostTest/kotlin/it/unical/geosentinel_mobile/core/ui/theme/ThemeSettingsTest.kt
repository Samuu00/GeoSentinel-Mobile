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

class ThemeSettingsTest {

    private val dir: File = createTempDirectory("theme").toFile()
    private val scopes = mutableListOf<CoroutineScope>()

    // ogni istanza ha il suo scope: chiuderlo libera il file, come un riavvio dell'app
    private fun newDataStore() = CoroutineScope(Dispatchers.IO + SupervisorJob()).let { scope ->
        scopes += scope
        PreferenceDataStoreFactory.createWithPath(scope = scope) { File(dir, "test.preferences_pb").toOkioPath() }
    }

    @AfterTest
    fun tearDown() {
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    @Test
    fun `senza scelta salvata il tema segue il sistema`() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, ThemeSettings(newDataStore()).mode.first())
    }

    @Test
    fun `la scelta resta dopo un riavvio`() = runBlocking {
        ThemeSettings(newDataStore()).setMode(ThemeMode.DARK)
        // si aspetta la chiusura del primo DataStore: con il solo cancel() su Linux il file risulta ancora aperto
        // ("multiple DataStores active", FileStorage.kt:52), come in AndroidSecureTokenStoreTest
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        assertEquals(ThemeMode.DARK, ThemeSettings(newDataStore()).mode.first())
    }

    @Test
    fun `un valore sconosciuto torna al tema di sistema`() = runBlocking {
        val store = newDataStore()
        store.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }
        assertEquals(ThemeMode.SYSTEM, ThemeSettings(store).mode.first())
    }
}
