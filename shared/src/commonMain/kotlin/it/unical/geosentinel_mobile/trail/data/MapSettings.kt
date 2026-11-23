// Preferenze della mappa salvate in DataStore: il tipo di mappa scelto e lo stato del foglio "Vicino a te".
package it.unical.geosentinel_mobile.trail.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Tipi di mappa disponibili. Ogni voce corrisponde a uno stile in composeResources/files/map, generato dallo script in
 * tools/map-styles: STREETS è la mappa stradale, SATELLITE le immagini satellitari.
 */
enum class MapType { STREETS, SATELLITE }

/**
 * Preferenze della mappa scelte dall'utente, salvate nello stesso DataStore del tema (come fa ThemeSettings). Le letture
 * sono Flow, così la UI si aggiorna quando il valore cambia.
 */
class MapSettings(private val dataStore: DataStore<Preferences>) {

    /** Tipo di mappa scelto; la mappa stradale se non è mai stato scelto. */
    val type: Flow<MapType> = dataStore.data
        // Se il file delle preferenze non si legge si usa la mappa stradale invece di andare in crash.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        // Un valore salvato che non corrisponde più a nessun tipo (per esempio uno rimosso in una versione
        // precedente) torna alla mappa stradale.
        .map { prefs -> prefs[KEY]?.let { name -> MapType.entries.find { it.name == name } } ?: MapType.STREETS }

    /** Salva il tipo di mappa scelto. */
    suspend fun setType(type: MapType) {
        dataStore.edit { it[KEY] = type.name }
    }

    /** Indica se il foglio "Vicino a te" sulla mappa è aperto: la prima volta sì, poi come l'ha lasciato l'utente. */
    val nearMeOpen: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[NEAR_ME_OPEN] ?: true }

    /** Salva lo stato aperto o chiuso del foglio "Vicino a te". */
    suspend fun setNearMeOpen(open: Boolean) {
        dataStore.edit { it[NEAR_ME_OPEN] = open }
    }

    /** Chiavi delle preferenze nel DataStore. */
    private companion object {
        val KEY = stringPreferencesKey("map_type")
        val NEAR_ME_OPEN = booleanPreferencesKey("near_me_open")
    }
}
