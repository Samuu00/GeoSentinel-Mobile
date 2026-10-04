package it.unical.geosentinel_mobile.trail.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant

// Cache del catalogo (M3.3): la UI legge sempre da qui, la rete aggiorna. updatedAt = ultima volta che il server ha
// confermato la riga (200, o 304 per le rotte con ETag): è la data di "aggiornato alle".

@Entity(tableName = "trail_summary", indices = [Index("minLon", "maxLon"), Index("minLat", "maxLat")])
data class TrailSummaryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val difficulty: String,
    val lengthMeters: Double,
    val status: String,
    val simplifiedGeoJson: String, // GeoJsonLineString semplificato (ADR-0020), come arriva dal server
    // riquadro del percorso: per trovare i sentieri che attraversano la vista senza leggere il GeoJSON
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val updatedAt: Instant
)

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
 * ETag dell'ultima risposta 200 di una rotta (es. "trail/<id>", "pois/<trailId>"). Il backend calcola l'ETag
 * sull'intero body (ShallowEtagHeaderFilter): vale per la risposta, non per la singola riga, quindi sta qui e non
 * nelle entità.
 */
@Entity(tableName = "catalog_etag")
data class CatalogEtagEntity(
    @PrimaryKey val key: String,
    val etag: String,
)
