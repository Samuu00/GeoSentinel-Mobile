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

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// Tema scelto dall'utente, salvato in DataStore (che lavora già fuori dal main thread)
class ThemeSettings(private val dataStore: DataStore<Preferences>) {

    val mode: Flow<ThemeMode> = dataStore.data
        // file illeggibile: si torna al tema di sistema invece di far crashare l'app
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY]?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.SYSTEM }

    suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { it[KEY] = mode.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("theme_mode")
    }
}
