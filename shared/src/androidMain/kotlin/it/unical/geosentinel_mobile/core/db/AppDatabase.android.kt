package it.unical.geosentinel_mobile.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

// In databases/ (getDatabasePath): la cartella che backup_rules.xml e data_extraction_rules.xml escludono dal backup
internal fun androidDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val app = context.applicationContext
    return Room.databaseBuilder<AppDatabase>(app, app.getDatabasePath(DATABASE_FILE_NAME).absolutePath)
}
