package it.unical.geosentinel_mobile.trail.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Tipo di mappa: ogni voce è uno stile in composeResources/files/map (generati da tools/map-styles)
enum class MapType { STREETS, SATELLITE }

// Tipo di mappa scelto dall'utente, salvato nello stesso DataStore del tema (come ThemeSettings)
class MapSettings(private val dataStore: DataStore<Preferences>) {

    val type: Flow<MapType> = dataStore.data
        // file illeggibile: mappa stradale invece di un crash
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        // un valore non più esistente (es. TOPO, tolta il 03/10) torna alla stradale
        .map { prefs -> prefs[KEY]?.let { name -> MapType.entries.find { it.name == name } } ?: MapType.STREETS }

    suspend fun setType(type: MapType) {
        dataStore.edit { it[KEY] = type.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("map_type")
    }
}
