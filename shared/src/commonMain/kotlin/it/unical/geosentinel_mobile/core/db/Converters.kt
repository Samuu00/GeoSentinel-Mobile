package it.unical.geosentinel_mobile.core.db

import androidx.room.TypeConverter
import kotlin.time.Instant

class Converters {
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }
    @TypeConverter
    fun toEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()
}
