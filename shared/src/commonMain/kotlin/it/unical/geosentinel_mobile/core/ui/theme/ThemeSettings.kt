// Preferenza dell'utente sul tema (Sistema, Chiaro, Scuro).
// La scelta si fa dal Profilo, viene salvata in DataStore e letta da `App` all'avvio per applicare il tema.
package it.unical.geosentinel_mobile.core.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Le tre scelte possibili per il tema. */
enum class ThemeMode {
    /** Segue l'impostazione chiaro/scuro del sistema operativo. */
    SYSTEM,
    /** Sempre chiaro. */
    LIGHT,
    /** Sempre scuro. */
    DARK }

/**
 * Lettura e scrittura della preferenza sul tema. Si appoggia a un DataStore di preferenze, che lavora già
 * fuori dal main thread, quindi non serve cambiare dispatcher.
 */
class ThemeSettings(private val dataStore: DataStore<Preferences>) {

    /** Il tema scelto, aggiornato a ogni modifica; [ThemeMode.SYSTEM] se non è mai stato scelto. */
    val mode: Flow<ThemeMode> = dataStore.data
        // Se il file delle preferenze non si legge si torna al tema di sistema invece di far chiudere l'app.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY]?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.SYSTEM }

    /** Salva la nuova scelta; il flusso [mode] la propaga subito alla UI. */
    suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { it[KEY] = mode.name }
    }

    private companion object {
        /** Chiave con cui il nome del [ThemeMode] è salvato nelle preferenze. */
        val KEY = stringPreferencesKey("theme_mode")
    }
}
