// Entità Room della cache locale del catalogo (sentieri, dettagli dei sentieri, POI ed ETag). La UI legge sempre da
// queste tabelle e la rete le aggiorna, così l'app mostra i dati anche senza connessione. In ogni entità updatedAt è
// l'ultima volta che il server ha confermato la riga (con una risposta 200, o 304 per le rotte con ETag) ed è l'orario
// mostrato come "aggiornato alle".
package it.unical.geosentinel_mobile.trail.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant

/**
 * Riassunto di un sentiero, usato per disegnarlo sulla mappa e nelle liste. Gli indici sul riquadro permettono di
 * trovare i sentieri che attraversano la vista senza leggere il GeoJSON.
 *
 * @property simplifiedGeoJson la LineString semplificata in GeoJSON, così come arriva dal server.
 * @property minLon riquadro del percorso (insieme a minLat, maxLon, maxLat), in gradi.
 */
@Entity(tableName = "trail_summary", indices = [Index("minLon", "maxLon"), Index("minLat", "maxLat")])
data class TrailSummaryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val difficulty: String,
    val lengthMeters: Double,
    val status: String,
    val simplifiedGeoJson: String,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val updatedAt: Instant
)

/** Dettaglio di un sentiero: il percorso completo in GeoJSON e la descrizione, mostrati nella pagina del sentiero. */
@Entity(tableName = "trail_detail")
data class TrailDetailEntity(
    @PrimaryKey val id: String,
    val name: String,
    val difficulty: String,
    val lengthMeters: Double,
    val status: String,
    val fullGeoJson: String,
    val description: String?,
    val updatedAt: Instant
)

/** Punto di interesse; [trailId] è null per i POI non legati a un sentiero. */
@Entity(tableName = "poi", indices = [Index("trailId"), Index("lat", "lon")])
data class PoiEntity(
    @PrimaryKey val id: String,
    val trailId: String?,
    val name: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val updatedAt: Instant
)

/**
 * ETag dell'ultima risposta 200 di una rotta, identificata da una chiave come "trail/<id>" o "pois/<trailId>". Il
 * backend calcola l'ETag sull'intero body della risposta (ShallowEtagHeaderFilter di Spring): vale per la risposta e
 * non per la singola riga, per questo è salvato in una tabella propria e non nelle entità.
 */
@Entity(tableName = "catalog_etag")
data class CatalogEtagEntity(
    @PrimaryKey val key: String,
    val etag: String,
)
