package it.unical.geosentinel_mobile.trail.data

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.distanceTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

// Profilo altimetrico di un sentiero (redesign "Luce di quota"). Il backend non ha le quote: si leggono dalle
// Terrain Tiles di Mapzen (PNG "terrarium", non più sulla mappa dal 23/11: la quota è codificata nei colori del
// pixel). Poche tessere a zoom 12 (circa 30 m per pixel alle nostre latitudini) bastano per un sentiero.

/** Quote a passo costante lungo il percorso, con dislivelli e tempo di cammino stimato. */
data class ElevationProfile(
    val meters: List<Double>,
    val ascentM: Int,
    val descentM: Int,
    val minM: Int,
    val maxM: Int,
)

internal const val ElevationZoom = 12
private const val TileSize = 256
private const val Samples = 96
// sotto questa variazione la quota "trema" (pixel, arrotondamenti): non conta nel dislivello
private const val AscentThresholdM = 5.0

/** Tessera e pixel (x, y) di un punto in Web Mercator. */
internal data class TilePixel(val tileX: Int, val tileY: Int, val px: Int, val py: Int)

internal fun tilePixel(point: GeoPoint, zoom: Int = ElevationZoom): TilePixel {
    val scale = (1 shl zoom) * TileSize.toDouble()
    val x = (point.lon + 180.0) / 360.0 * scale
    val latRad = point.lat * PI / 180.0
    val y = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * scale
    val gx = floor(x).toInt()
    val gy = floor(y).toInt()
    return TilePixel(gx / TileSize, gy / TileSize, gx % TileSize, gy % TileSize)
}

/** Quota in metri di un pixel terrarium (ARGB): (R·256 + G + B/256) − 32768. */
internal fun terrariumMeters(argb: Int): Double {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return r * 256.0 + g + b / 256.0 - 32768.0
}

/** [count] punti a distanza costante lungo il percorso (il primo e l'ultimo compresi). */
internal fun resample(path: List<GeoPoint>, count: Int = Samples): List<GeoPoint> {
    if (path.size < 2) return path
    val cumulative = DoubleArray(path.size)
    for (i in 1 until path.size) cumulative[i] = cumulative[i - 1] + path[i - 1].distanceTo(path[i])
    val total = cumulative.last()
    if (total == 0.0) return listOf(path.first())
    var segment = 1
    return List(count) { index ->
        val target = total * index / (count - 1)
        while (segment < path.size - 1 && cumulative[segment] < target) segment++
        val from = path[segment - 1]
        val to = path[segment]
        val span = cumulative[segment] - cumulative[segment - 1]
        val t = if (span == 0.0) 0.0 else ((target - cumulative[segment - 1]) / span).coerceIn(0.0, 1.0)
        GeoPoint(lat = from.lat + (to.lat - from.lat) * t, lon = from.lon + (to.lon - from.lon) * t)
    }
}

/** Salita e discesa totali (m), contando solo i cambi oltre la soglia: niente dislivello dal rumore dei pixel. */
internal fun climb(meters: List<Double>, threshold: Double = AscentThresholdM): Pair<Int, Int> {
    if (meters.isEmpty()) return 0 to 0
    var up = 0.0
    var down = 0.0
    var reference = meters.first()
    for (value in meters.drop(1)) {
        val delta = value - reference
        if (delta >= threshold) {
            up += delta
            reference = value
        } else if (delta <= -threshold) {
            down -= delta
            reference = value
        }
    }
    return up.roundToInt() to down.roundToInt()
}

/**
 * Tempo di cammino stimato in minuti (DIN 33466, la regola delle guide escursionistiche): 4 km/h in piano, 300 m/h
 * in salita, 500 m/h in discesa; la parte minore tra orizzontale e verticale vale la metà.
 */
internal fun hikingMinutes(lengthM: Double, ascentM: Int, descentM: Int): Int {
    val horizontal = lengthM / 4000.0
    val vertical = ascentM / 300.0 + descentM / 500.0
    val hours = maxOf(horizontal, vertical) + minOf(horizontal, vertical) / 2
    return (hours * 60).roundToInt()
}

/**
 * Scarica le tessere delle quote (una volta per processo: le stesse servono a più sentieri vicini) e calcola il
 * profilo. null se una tessera non arriva: la pagina del sentiero mostra solo i dati del server.
 * [client]: un client senza bearer, il token non va mai verso altri host.
 */
class ElevationRepository(
    private val client: () -> HttpClient,
    private val tileUrl: (z: Int, x: Int, y: Int) -> String = { z, x, y -> "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/$z/$x/$y.png" },
) {
    // un client solo per tutte le tessere
    private val http by lazy(client)
    private val tiles = mutableMapOf<Pair<Int, Int>, IntArray>()
    private val lock = Mutex()

    suspend fun profile(path: List<GeoPoint>): ElevationProfile? {
        val points = resample(path)
        if (points.size < 2) return null
        val meters = try {
            points.map { point ->
                val pixel = tilePixel(point)
                val tile = tile(pixel.tileX, pixel.tileY) ?: return null
                terrariumMeters(tile[pixel.py * TileSize + pixel.px])
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        val (up, down) = climb(meters)
        return ElevationProfile(meters, up, down, meters.min().roundToInt(), meters.max().roundToInt())
    }

    /** Quota di un punto (m), dalle Terrain Tiles; null se la tessera non arriva. Per i rifugi senza quota nei dati. */
    suspend fun metersAt(point: GeoPoint): Int? = try {
        val pixel = tilePixel(point)
        tile(pixel.tileX, pixel.tileY)?.let { terrariumMeters(it[pixel.py * TileSize + pixel.px]).roundToInt() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun tile(x: Int, y: Int): IntArray? = lock.withLock {
        tiles[x to y]?.let { return it }
        val response = http.get(tileUrl(ElevationZoom, x, y))
        if (!response.status.isSuccess()) return null
        val bytes = response.readRawBytes()
        val pixels = withContext(Dispatchers.Default) {
            val bitmap: ImageBitmap = bytes.decodeToImageBitmap()
            IntArray(TileSize * TileSize).also { bitmap.readPixels(it, width = TileSize, height = TileSize) }
        }
        // ponytail: cache in memoria senza limite; un sentiero tocca 1-4 tessere da 256 KB. LRU se si apre mezzo parco
        tiles[x to y] = pixels
        pixels
    }
}
