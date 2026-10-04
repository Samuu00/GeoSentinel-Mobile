package it.unical.geosentinel_mobile.core.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

// In Application Support e non in Documents: Documents finisce nel backup di iCloud e si vede nell'app File. Il
// database (da M5/M6: outbox, posizioni dell'SOS) resta su questo telefono, come i token nel Keychain ThisDeviceOnly.
// Compilato solo nel job ios della CI.
@OptIn(ExperimentalForeignApi::class)
internal fun iosDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val directory = requireNotNull(
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    ) { "cartella Application Support non disponibile" }
    directory.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    val path = requireNotNull(directory.path) { "cartella Application Support non disponibile" }
    return Room.databaseBuilder<AppDatabase>(name = "$path/$DATABASE_FILE_NAME")
}
