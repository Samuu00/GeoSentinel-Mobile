// Convertitori di tipo per Room.
// SQLite non ha un tipo per gli istanti di tempo: qui si dice a Room come salvarli e rileggerli.
package it.unical.geosentinel_mobile.core.db

import androidx.room.TypeConverter
import kotlin.time.Instant

/**
 * Converte `Instant` in millisecondi dall'epoca Unix (colonna INTEGER) e viceversa.
 * È registrata su `AppDatabase` e vale per tutte le entità.
 */
class Converters {
    /** Dai millisecondi salvati nel database all'`Instant`; null resta null. */
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }
    /** Dall'`Instant` ai millisecondi da salvare; null resta null. */
    @TypeConverter
    fun toEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()
}
