// Costruzione del database Room su Android.
// Il builder viene passato a Koin all'avvio (`initKoin`), che poi crea l'unica istanza di AppDatabase.
package it.unical.geosentinel_mobile.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Restituisce il builder di Room per il file del database nella cartella `databases/` dell'app.
 *
 * La posizione è scelta di proposito: è la cartella che `backup_rules.xml` e `data_extraction_rules.xml`
 * escludono dal backup, così cache e coda delle azioni non vengono copiate su altri dispositivi.
 * Si usa l'application context per non trattenere un'Activity.
 */
internal fun androidDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val app = context.applicationContext
    return Room.databaseBuilder<AppDatabase>(app, app.getDatabasePath(DATABASE_FILE_NAME).absolutePath)
}
