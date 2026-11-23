// Riquadri geografici (bounding box) e funzioni di geometria sulla sfera.
// Ogni conversione tra i formati di coordinate (API, GeoJSON, MapLibre) e ogni bbox inviata al backend passano da
// questo file, così l'ordine latitudine/longitudine non si può sbagliare altrove.
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
 * Un rettangolo della mappa, con i lati nell'ordine usato dal backend (`bbox=minLon,minLat,maxLon,maxLat`).
 *
 * Il costruttore è privato: si crea con [of], che riporta i valori dentro i limiti del mondo, perché su MapLibre
 * la vista può superare i ±180 gradi di longitudine.
 */
class GeoBounds private constructor(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {

    // Vero se il riquadro non è degenere: il backend richiede min < max su entrambi gli assi e risponde 400 altrimenti.
    val isValid: Boolean get() = minLon < maxLon && minLat < maxLat

    /**
     * Il valore del parametro `bbox` per le richieste al backend. I numeri sono scritti in forma decimale semplice,
     * mai in notazione scientifica come fa `Double.toString` vicino allo zero ("1.0E-4"), che il backend leggerebbe male.
     */
    fun toQuery(): String = listOf(minLon, minLat, maxLon, maxLat).joinToString(",") { it.plainDecimal() }

    /** Vero se [other] è interamente contenuto in questo riquadro. */
    operator fun contains(other: GeoBounds): Boolean =
        other.minLon >= minLon && other.maxLon <= maxLon && other.minLat >= minLat && other.maxLat <= maxLat

    /**
     * Restituisce lo stesso rettangolo allargato su ogni lato di [fraction] della sua dimensione, per caricare
     * i dati anche in un margine attorno alla parte visibile.
     */
    fun expandedBy(fraction: Double): GeoBounds {
        val dLon = (maxLon - minLon) * fraction
        val dLat = (maxLat - minLat) * fraction
        return of(minLon - dLon, minLat - dLat, maxLon + dLon, maxLat + dLat)
    }

    /** La parte comune ai due riquadri, oppure null se non si sovrappongono (su un riquadro vuoto il backend darebbe 400). */
    infix fun intersect(other: GeoBounds): GeoBounds? =
        of(maxOf(minLon, other.minLon), maxOf(minLat, other.minLat), minOf(maxLon, other.maxLon), minOf(maxLat, other.maxLat))
            .takeIf { it.isValid }

    /** Il centro del rettangolo. */
    val center: GeoPoint get() = GeoPoint(lat = (minLat + maxLat) / 2, lon = (minLon + maxLon) / 2)

    /** Metà della diagonale in metri: il raggio che, partendo dal centro, copre tutto il rettangolo. */
    val radiusMeters: Double get() = center.distanceTo(GeoPoint(lat = maxLat, lon = maxLon))

    /** Due riquadri sono uguali se hanno gli stessi quattro lati. */
    override fun equals(other: Any?) = other is GeoBounds &&
        minLon == other.minLon && minLat == other.minLat && maxLon == other.maxLon && maxLat == other.maxLat

    /** Coerente con [equals]: si calcola sulla rappresentazione testuale dei quattro lati. */
    override fun hashCode() = toQuery().hashCode()

    // Le coordinate non compaiono nei log, perché rivelano dove l'utente guarda o dove si trova.
    override fun toString() = "GeoBounds(…)"

    companion object {
        /** Crea un riquadro limitando longitudine a ±180 gradi e latitudine a ±90 gradi. */
        fun of(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) = GeoBounds(
            minLon.coerceIn(-180.0, 180.0),
            minLat.coerceIn(-90.0, 90.0),
            maxLon.coerceIn(-180.0, 180.0),
            maxLat.coerceIn(-90.0, 90.0),
        )

        /**
         * Il quadrato che si estende di [radiusMeters] in ogni direzione attorno a [center]. Un grado di latitudine
         * vale circa 111 km, mentre quello di longitudine si restringe con il coseno della latitudine (limitato in
         * basso per non dividere per zero vicino ai poli). Gli angoli del quadrato distano circa 1,4 volte il
         * raggio: per la mappa l'approssimazione va bene.
         */
        fun around(center: GeoPoint, radiusMeters: Double): GeoBounds {
            val dLat = radiusMeters / METERS_PER_DEGREE_LAT
            val dLon = radiusMeters / (METERS_PER_DEGREE_LAT * cos(center.lat.toRadians()).coerceAtLeast(0.01))
            return of(center.lon - dLon, center.lat - dLat, center.lon + dLon, center.lat + dLat)
        }

        /** Il riquadro più piccolo che contiene tutte le [positions], oppure null se la lista è vuota. */
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

/**
 * La parte del riquadro che cade dentro [limit] (null significa nessun limite), oppure null se è tutta fuori.
 * Si usa per restringere le richieste all'area attorno all'utente.
 */
fun GeoBounds.within(limit: GeoBounds?): GeoBounds? = if (limit == null) this else this intersect limit

/** Converte nel `BoundingBox` di MapLibre, che usa lo stesso ordine del backend: ovest, sud, est, nord. */
fun GeoBounds.toBoundingBox(): BoundingBox = BoundingBox(west = minLon, south = minLat, east = maxLon, north = maxLat)

/**
 * Il tracciato di un sentiero come lo vuole MapLibre. In GeoJSON ogni coppia è `[lon, lat]`, cioè il primo valore
 * è la longitudine. Le coppie malformate vengono saltate invece di far fallire il disegno della mappa.
 */
fun GeoJsonLineString.toPositions(): List<Position> =
    coordinates.mapNotNull { pair -> if (pair.size >= 2) Position(longitude = pair[0], latitude = pair[1]) else null }

/** Converte il punto del client generato nel [GeoPoint] dell'app. */
fun GeoPointDto.toGeoPoint(): GeoPoint = GeoPoint(lat = lat, lon = lon)

/**
 * Scrive il numero con al massimo 7 decimali (circa un centimetro), con il punto come separatore e senza esponente:
 * per esempio "16.47", "-180.0", "0.0001". Gli zeri finali vengono tolti, lasciandone almeno uno.
 */
internal fun Double.plainDecimal(): String {
    val scaled = (this * 10_000_000).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val absolute = abs(scaled)
    val fraction = (absolute % 10_000_000).toString().padStart(7, '0').trimEnd('0').ifEmpty { "0" }
    return "$sign${absolute / 10_000_000}.$fraction"
}

/**
 * Distanza in metri tra due punti, calcolata sulla sfera con la formula dell'emisenoverso (haversine).
 * È abbastanza precisa per raggi di ricerca di qualche chilometro.
 */
fun GeoPoint.distanceTo(other: GeoPoint): Double {
    val dLat = (other.lat - lat).toRadians()
    val dLon = (other.lon - lon).toRadians()
    val a = sin(dLat / 2).pow(2) + cos(lat.toRadians()) * cos(other.lat.toRadians()) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

// Raggio medio della Terra in metri.
private const val EARTH_RADIUS_METERS = 6_371_000.0
// Metri in un grado di latitudine.
private const val METERS_PER_DEGREE_LAT = 111_320.0

/** Da gradi a radianti. */
private fun Double.toRadians() = this * PI / 180
