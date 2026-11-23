// Definizione dell'unico database locale dell'app, gestito con Room (versione multipiattaforma).
// Contiene due cose: la cache del catalogo (sentieri, POI e relativi ETag), usata anche senza rete, e l'outbox,
// cioè la coda delle azioni dell'utente (prenotazioni, SOS) che devono arrivare al server anche dopo un riavvio.
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

/**
 * Il database Room dell'app. Espone un DAO per la cache del catalogo e uno per l'outbox.
 *
 * Lo schema di ogni versione viene esportato in `shared/schemas` e versionato con il codice: Room lo usa per
 * generare le migrazioni automatiche e per verificarle nei test.
 */
@Database(
    entities = [
        TrailSummaryEntity::class,
        TrailDetailEntity::class,
        PoiEntity::class,
        CatalogEtagEntity::class,
        // Tabella dell'outbox: le azioni in attesa di una risposta definitiva dal server.
        PendingActionEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // Il passaggio dalla versione 1 alla 2 aggiunge solo la tabella dell'outbox: la cache del catalogo
    // resta intatta e Room ricava la migrazione confrontando gli schemi esportati.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
// Sui target diversi da Android Room crea il database attraverso questo costruttore,
// la cui implementazione `actual` è generata dal compilatore di Room.
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    /** Accesso alla cache di sentieri, POI ed ETag. */
    abstract fun catalogDao(): CatalogDao
    /** Accesso alla coda delle azioni in attesa. */
    abstract fun outboxDao(): OutboxDao
}

/**
 * Costruttore del database richiesto da Room KMP. L'avviso sull'`actual` mancante è soppresso perché
 * l'implementazione non la scrive il programmatore: la genera Room per ogni piattaforma.
 */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

// Nome del file SQLite, uguale su Android e iOS.
const val DATABASE_FILE_NAME = "geosentinel.db"

/**
 * Crea il database a partire dal builder fornito dalla piattaforma (su Android nella cartella `databases/`,
 * esclusa dal backup; su iOS in Application Support).
 *
 * Usa il driver SQLite incluso nell'app (lo stesso su entrambe le piattaforme) ed esegue le query su
 * `Dispatchers.IO`, mai sul main thread. Non è abilitata la migrazione distruttiva: l'outbox contiene SOS e
 * prenotazioni non ancora inviati, che non devono mai andare persi per un cambio di schema.
 */
fun createAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
