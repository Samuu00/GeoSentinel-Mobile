// Costruzione del database Room su iOS.
// Il builder viene passato a `startKoinWith` da MainViewController, che crea l'unica istanza di AppDatabase.
// Questo file viene compilato solo sul runner macOS della CI, perché iOS non si compila su Windows.
package it.unical.geosentinel_mobile.core.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Restituisce il builder di Room per un file nella cartella Application Support, esclusa dal backup.
 *
 * Non si usa Documents perché finirebbe nel backup di iCloud e sarebbe visibile nell'app File. Il database
 * contiene la coda delle azioni e le posizioni inviate durante un SOS, quindi resta solo su questo telefono,
 * come i token nel Keychain con accesso `ThisDeviceOnly`.
 *
 * Lancia `IllegalArgumentException` se il sistema non fornisce la cartella Application Support.
 */
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
