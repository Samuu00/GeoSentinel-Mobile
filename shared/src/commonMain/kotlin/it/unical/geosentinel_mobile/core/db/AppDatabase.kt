package it.unical.geosentinel_mobile.core.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import it.unical.geosentinel_mobile.trail.data.local.CatalogDao
import it.unical.geosentinel_mobile.trail.data.local.CatalogEtagEntity
import it.unical.geosentinel_mobile.trail.data.local.PoiEntity
import it.unical.geosentinel_mobile.trail.data.local.TrailDetailEntity
import it.unical.geosentinel_mobile.trail.data.local.TrailSummaryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        TrailSummaryEntity::class,
        TrailDetailEntity::class,
        PoiEntity::class,
        CatalogEtagEntity::class,
        // outbox (M5.2): prenotazioni, poi SOS
        PendingActionEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // 1 → 2 aggiunge solo la tabella dell'outbox: la cache del catalogo resta (schemi in shared/schemas, da committare)
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
// Sui target non Android Room crea il database da qui (l'actual lo genera il compilatore di Room)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun outboxDao(): OutboxDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

const val DATABASE_FILE_NAME = "geosentinel.db"

/**
 * Il database dal builder della piattaforma (Android: in `databases/`, escluso dal backup; iOS: Application Support).
 * Niente migrazione distruttiva: da M5/M6 qui c'è anche l'outbox (SOS, prenotazioni), che non si può perdere.
 * Query su Dispatchers.IO (§4).
 */
fun createAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
