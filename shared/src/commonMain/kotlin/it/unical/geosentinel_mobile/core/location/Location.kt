// Posizione dell'utente e conversioni tra i formati di coordinate.
// La posizione arriva dal modulo location di MapLibre Compose, già presente come dipendenza della mappa: usa
// LocationManager su Android e CLLocationManager su iOS e gestisce lo stato e la richiesta del permesso. Il
// provider si ottiene nella UI (`rememberLocationState`), perché su Android per chiedere il permesso serve
// l'Activity e non l'Application. Qui ci sono solo il punto dell'API e due modi di leggere la posizione.
package it.unical.geosentinel_mobile.core.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.compose.location.LocationEvent
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.LocationRequest
import org.maplibre.spatialk.geojson.Position
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Un punto come lo usa l'API (`GeoPointDto`): latitudine e longitudine, in quest'ordine, in gradi decimali. */
data class GeoPoint(val lat: Double, val lon: Double)

// GeoJSON e MapLibre vogliono prima la longitudine, l'API prima la latitudine. Le conversioni stanno solo qui,
// così l'ordine non si può sbagliare altrove (lo verifica un test di regressione in LocationTest).

/** Converte il punto dell'API nella `Position` di MapLibre. */
fun GeoPoint.toPosition(): Position = Position(longitude = lon, latitude = lat)
/** Converte una `Position` di MapLibre nel punto dell'API. */
fun Position.toGeoPoint(): GeoPoint = GeoPoint(lat = latitude, lon = longitude)

/**
 * Calcola il nuovo centro della mappa dopo un trascinamento di [dxDp] e [dyDp] (positivi verso destra e verso il
 * basso), sapendo quanti metri corrispondono a un dp ([metersPerDp]). La mappa segue il dito, quindi il centro si
 * sposta nella direzione opposta.
 *
 * Usa un'approssimazione locale (un grado di latitudine vale circa 111 km), più che sufficiente per i pochi metri
 * di un gesto. La latitudine resta entro ±85 gradi, il limite della proiezione Web Mercator, e la longitudine
 * viene riportata nell'intervallo da -180 a 180.
 */
fun Position.dragged(dxDp: Double, dyDp: Double, metersPerDp: Double): Position {
    val lat = (latitude + dyDp * metersPerDp / METERS_PER_DEGREE).coerceIn(-85.0, 85.0)
    val lon = longitude - dxDp * metersPerDp / (METERS_PER_DEGREE * cos(latitude * PI / 180))
    return Position(longitude = (lon + 540.0) % 360.0 - 180.0, latitude = lat)
}

// Metri in un grado di latitudine (e di longitudine all'equatore).
private const val METERS_PER_DEGREE = 111_320.0

/**
 * Restituisce la posizione attuale, per chi ne ha bisogno una volta sola (sentieri vicini, SOS): il primo fix
 * ricevuto entro [timeout]. Su Android il primo fix può essere l'ultima posizione nota.
 *
 * Restituisce null, senza mai lanciare eccezioni, se manca il permesso, se la localizzazione è spenta, se non
 * arriva un fix in tempo o se la piattaforma non ha un provider. Chi chiama ha sempre un'alternativa (un'area
 * predefinita, il pulsante "Chiama 112").
 */
suspend fun LocationProvider.current(timeout: Duration = 10.seconds): GeoPoint? {
    if (permission.value !is LocationPermission.Granted) return null
    return try {
        withTimeoutOrNull(timeout) { fixes(LocationRequest()).firstOrNull() }
    } catch (_: IllegalStateException) {
        null // Provider senza implementazione (piattaforma non supportata).
    }
}

/**
 * Aggiornamenti continui della posizione, al massimo uno ogni [interval]; li usa l'SOS attivo per mandare la
 * posizione ai soccorritori. Gli eventi di posizione non disponibile vengono saltati.
 */
fun LocationProvider.positions(interval: Duration): Flow<GeoPoint> = fixes(LocationRequest(minimumInterval = interval))

/** Trasforma gli eventi del provider in punti, tenendo solo gli aggiornamenti con una misura. */
private fun LocationProvider.fixes(request: LocationRequest): Flow<GeoPoint> =
    updates(request).filterIsInstance<LocationEvent.Update>().map { it.measurement.position.toGeoPoint() }

/**
 * Formatta una coordinata con 5 decimali (circa un metro di precisione), per mostrarla ai soccorritori o nella coda
 * del gestore. Si fa a mano perché `String.format` non esiste nel codice comune di Kotlin.
 */
internal fun Double.decimals5(): String {
    val scaled = (this * 100_000).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val whole = abs(scaled)
    return "$sign${whole / 100_000}.${(whole % 100_000).toString().padStart(5, '0')}"
}
