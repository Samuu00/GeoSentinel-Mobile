// Calcolo del profilo altimetrico di un sentiero. Il backend non conosce le quote: l'app le legge dalle Terrain Tiles
// di Mapzen, immagini PNG in formato "terrarium" in cui la quota di ogni punto è codificata nei colori del pixel. Poche
// tessere a zoom 12 (circa 30 m per pixel alle latitudini della Calabria) bastano per un sentiero. Da qui si ricavano
// anche dislivelli e tempo di cammino stimato mostrati nella pagina del sentiero.
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

/**
 * Profilo altimetrico di un sentiero.
 *
 * @property meters quote in metri campionate a distanza costante lungo il percorso.
 * @property ascentM salita totale in metri.
 * @property descentM discesa totale in metri.
 * @property minM quota minima in metri.
 * @property maxM quota massima in metri.
 */
data class ElevationProfile(
    val meters: List<Double>,
    val ascentM: Int,
    val descentM: Int,
    val minM: Int,
    val maxM: Int,
)

/** Livello di zoom delle tessere delle quote (circa 30 m per pixel alle nostre latitudini). */
internal const val ElevationZoom = 12

/** Lato di una tessera in pixel. */
private const val TileSize = 256

/** Numero di punti in cui si campiona la quota lungo il sentiero. */
private const val Samples = 96

/**
 * Variazione minima di quota, in metri, che conta nel dislivello. Sotto questa soglia la quota "trema" per effetto dei
 * pixel e degli arrotondamenti, e sommare queste oscillazioni gonfierebbe il dislivello.
 */
private const val AscentThresholdM = 5.0

/** Coordinate della tessera e del pixel (x, y) dentro la tessera che contengono un punto, in proiezione Web Mercator. */
internal data class TilePixel(val tileX: Int, val tileY: Int, val px: Int, val py: Int)

/** Calcola tessera e pixel del punto [point] al livello [zoom] con le formule standard di Web Mercator. */
internal fun tilePixel(point: GeoPoint, zoom: Int = ElevationZoom): TilePixel {
    val scale = (1 shl zoom) * TileSize.toDouble()
    val x = (point.lon + 180.0) / 360.0 * scale
    val latRad = point.lat * PI / 180.0
    val y = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * scale
    val gx = floor(x).toInt()
    val gy = floor(y).toInt()
    return TilePixel(gx / TileSize, gy / TileSize, gx % TileSize, gy % TileSize)
}

/** Decodifica la quota in metri di un pixel terrarium (in formato ARGB) con la formula R * 256 + G + B / 256 - 32768. */
internal fun terrariumMeters(argb: Int): Double {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return r * 256.0 + g + b / 256.0 - 32768.0
}

/**
 * Ricampiona il percorso in [count] punti a distanza costante, compresi il primo e l'ultimo, interpolando linearmente
 * lungo i segmenti. Un percorso con meno di due punti viene restituito così com'è; uno di lunghezza zero diventa un
 * solo punto.
 */
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

/**
 * Calcola salita e discesa totali in metri. Un cambio di quota conta solo quando, rispetto all'ultimo punto di
 * riferimento, supera [threshold]: così il rumore dei pixel non produce dislivello fittizio.
 */
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
 * Stima il tempo di cammino in minuti secondo la norma DIN 33466, la regola usata dalle guide escursionistiche:
 * 4 km/h in piano, 300 m/h in salita e 500 m/h in discesa; delle due componenti (orizzontale e verticale) si somma la
 * maggiore più metà della minore.
 */
internal fun hikingMinutes(lengthM: Double, ascentM: Int, descentM: Int): Int {
    val horizontal = lengthM / 4000.0
    val vertical = ascentM / 300.0 + descentM / 500.0
    val hours = maxOf(horizontal, vertical) + minOf(horizontal, vertical) / 2
    return (hours * 60).roundToInt()
}

/**
 * Scarica le tessere delle quote e calcola profili e quote puntuali. Ogni tessera si scarica una volta per processo e
 * resta in memoria, perché le stesse servono a più sentieri vicini.
 *
 * @param client client HTTP senza il token: il bearer non deve mai essere inviato a host diversi dal backend.
 * @param tileUrl costruisce l'URL di una tessera da zoom e coordinate; è sostituibile nei test.
 */
class ElevationRepository(
    private val client: () -> HttpClient,
    private val tileUrl: (z: Int, x: Int, y: Int) -> String = { z, x, y -> "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/$z/$x/$y.png" },
) {
    /** Un solo client per tutte le tessere, creato al primo uso. */
    private val http by lazy(client)

    /** Pixel delle tessere già scaricate, per coordinate (x, y). */
    private val tiles = mutableMapOf<Pair<Int, Int>, IntArray>()

    /** Protegge la cache e fa sì che la stessa tessera non venga scaricata due volte in parallelo. */
    private val lock = Mutex()

    /**
     * Calcola il profilo altimetrico del percorso [path]. Restituisce null se il percorso ha meno di due punti o se una
     * tessera non arriva: in quel caso la pagina del sentiero mostra solo i dati del server.
     */
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
            // La cancellazione si propaga; qualunque altro errore (rete, decodifica) rende il profilo non disponibile.
            throw e
        } catch (_: Exception) {
            return null
        }
        val (up, down) = climb(meters)
        return ElevationProfile(meters, up, down, meters.min().roundToInt(), meters.max().roundToInt())
    }

    /**
     * Restituisce la quota in metri di un punto letta dalle Terrain Tiles, oppure null se la tessera non arriva. Serve
     * per i rifugi che non hanno la quota nei dati del server.
     */
    suspend fun metersAt(point: GeoPoint): Int? = try {
        val pixel = tilePixel(point)
        tile(pixel.tileX, pixel.tileY)?.let { terrariumMeters(it[pixel.py * TileSize + pixel.px]).roundToInt() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * Restituisce i pixel ARGB della tessera (x, y), dalla cache o scaricandola; null se il server non risponde con
     * successo. La decodifica del PNG avviene su Dispatchers.Default per non occupare il main thread.
     */
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
        // La cache in memoria non ha un limite: un sentiero tocca da una a quattro tessere da 256 KB ciascuna. Se
        // l'uso crescesse molto (per esempio aprendo mezzo parco) andrebbe sostituita con una cache LRU.
        tiles[x to y] = pixels
        pixels
    }
}
