package it.unical.geosentinel_mobile.core.location

import it.unical.geosentinel_mobile.api.model.GeoJsonLineString
import it.unical.geosentinel_mobile.api.model.GeoPointDto
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rettangolo della mappa nell'ordine del backend (`bbox=minLon,minLat,maxLon,maxLat`, TrailController).
 * Il costruttore lo tiene dentro i limiti del mondo: su MapLibre la vista può superare ±180° di longitudine.
 */
class GeoBounds private constructor(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {

    // Il backend vuole min < max (altrimenti 400)
    val isValid: Boolean get() = minLon < maxLon && minLat < maxLat

    // Mai la notazione scientifica di Double.toString ("1.0E-4" vicino allo 0): il backend la leggerebbe male
    fun toQuery(): String = listOf(minLon, minLat, maxLon, maxLat).joinToString(",") { it.plainDecimal() }

    operator fun contains(other: GeoBounds): Boolean =
        other.minLon >= minLon && other.maxLon <= maxLon && other.minLat >= minLat && other.maxLat <= maxLat

    /** Lo stesso rettangolo allargato di [fraction] della sua misura su ogni lato (per caricare un margine). */
    fun expandedBy(fraction: Double): GeoBounds {
        val dLon = (maxLon - minLon) * fraction
        val dLat = (maxLat - minLat) * fraction
        return of(minLon - dLon, minLat - dLat, maxLon + dLon, maxLat + dLat)
    }

    val center: GeoPoint get() = GeoPoint(lat = (minLat + maxLat) / 2, lon = (minLon + maxLon) / 2)

    /** Metà della diagonale: il raggio che copre tutto il rettangolo dal centro. */
    val radiusMeters: Double get() = center.distanceTo(GeoPoint(lat = maxLat, lon = maxLon))

    override fun equals(other: Any?) = other is GeoBounds &&
        minLon == other.minLon && minLat == other.minLat && maxLon == other.maxLon && maxLat == other.maxLat

    override fun hashCode() = toQuery().hashCode()

    // Niente coordinate nei log (dicono dove guarda o dove si trova l'utente, §4)
    override fun toString() = "GeoBounds(…)"

    companion object {
        fun of(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) = GeoBounds(
            minLon.coerceIn(-180.0, 180.0),
            minLat.coerceIn(-90.0, 90.0),
            maxLon.coerceIn(-180.0, 180.0),
            maxLat.coerceIn(-90.0, 90.0),
        )

        /** Il riquadro che contiene tutte le [positions], o null se non ce ne sono. */
        fun around(positions: List<Position>): GeoBounds? {
            if (positions.isEmpty()) return null
            return of(
                positions.minOf { it.longitude },
                positions.minOf { it.latitude },
                positions.maxOf { it.longitude },
                positions.maxOf { it.latitude },
            )
        }
    }
}

// MapLibre (spatialk) e il backend ordinano allo stesso modo: ovest, sud, est, nord
fun GeoBounds.toBoundingBox(): BoundingBox = BoundingBox(west = minLon, south = minLat, east = maxLon, north = maxLat)

/**
 * Il percorso di un sentiero come lo vuole MapLibre. GeoJSON è `[lon, lat]`: il primo valore è la longitudine
 * (ADR-0010 del backend). Le coppie malformate si saltano invece di far cadere la mappa.
 */
fun GeoJsonLineString.toPositions(): List<Position> =
    coordinates.mapNotNull { pair -> if (pair.size >= 2) Position(longitude = pair[0], latitude = pair[1]) else null }

fun GeoPointDto.toGeoPoint(): GeoPoint = GeoPoint(lat = lat, lon = lon)

// Al più 7 decimali (circa 1 cm), con il punto e senza esponente: "16.47", "-180.0", "0.0001"
internal fun Double.plainDecimal(): String {
    val scaled = (this * 10_000_000).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val absolute = abs(scaled)
    val fraction = (absolute % 10_000_000).toString().padStart(7, '0').trimEnd('0').ifEmpty { "0" }
    return "$sign${absolute / 10_000_000}.$fraction"
}

/** Distanza in metri sulla sfera (haversine): basta per raggi di ricerca di qualche km. */
fun GeoPoint.distanceTo(other: GeoPoint): Double {
    val dLat = (other.lat - lat).toRadians()
    val dLon = (other.lon - lon).toRadians()
    val a = sin(dLat / 2).pow(2) + cos(lat.toRadians()) * cos(other.lat.toRadians()) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private const val EARTH_RADIUS_METERS = 6_371_000.0

private fun Double.toRadians() = this * PI / 180
