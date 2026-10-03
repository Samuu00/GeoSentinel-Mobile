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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// La posizione arriva dal modulo location di MapLibre Compose (già una dipendenza della mappa): LocationManager su
// Android, CLLocationManager su iOS, con lo stato del permesso e la sua richiesta. Il provider si crea nella UI con
// rememberDefaultLocationProvider(): su Android per chiedere il permesso serve l'Activity, non l'Application.
// Qui c'è solo quello che serve all'app: GeoPoint dell'API e due modi di leggere la posizione.

/** Punto dell'API (`GeoPointDto`): latitudine e longitudine, in quest'ordine. */
data class GeoPoint(val lat: Double, val lon: Double)

// GeoJSON e MapLibre vogliono prima la longitudine: conversioni in un solo posto (test di regressione in LocationTest)
fun GeoPoint.toPosition(): Position = Position(longitude = lon, latitude = lat)
fun Position.toGeoPoint(): GeoPoint = GeoPoint(lat = latitude, lon = longitude)

/**
 * La posizione attuale, per chi la usa una volta (sentieri vicini, SOS): il primo fix entro [timeout]. Su Android il
 * primo è l'ultima posizione nota, se c'è. Restituisce null senza permesso, con la posizione spenta, senza un fix in
 * tempo o se la piattaforma non ha un provider: mai un'eccezione, chi chiama ha sempre un'alternativa (area di
 * default, "Chiama 112").
 */
suspend fun LocationProvider.current(timeout: Duration = 10.seconds): GeoPoint? {
    if (permission.value !is LocationPermission.Granted) return null
    return try {
        withTimeoutOrNull(timeout) { fixes(LocationRequest()).firstOrNull() }
    } catch (_: IllegalStateException) {
        null // provider senza backend (es. piattaforma non supportata)
    }
}

/** Aggiornamenti continui (SOS attivo, M6), al più uno ogni [interval]. Gli eventi "non disponibile" si saltano. */
fun LocationProvider.positions(interval: Duration): Flow<GeoPoint> = fixes(LocationRequest(minimumInterval = interval))

private fun LocationProvider.fixes(request: LocationRequest): Flow<GeoPoint> =
    updates(request).filterIsInstance<LocationEvent.Update>().map { it.measurement.position.toGeoPoint() }
