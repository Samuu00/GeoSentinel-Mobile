package it.unical.geosentinel_mobile.core.location

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.maplibre.compose.location.LocationAccuracyAuthorization
import org.maplibre.compose.location.LocationEvent
import org.maplibre.compose.location.LocationMeasurement
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.LocationRequest
import org.maplibre.compose.location.LocationUnavailableReason
import org.maplibre.spatialk.geojson.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

class LocationTest {

    // Un punto della Sila (seed del backend): in Calabria, non nel Golfo di Aden
    private val sila = GeoPoint(lat = 39.33, lon = 16.55)

    private fun update(lat: Double, lon: Double) = LocationEvent.Update(
        LocationMeasurement(position = Position(longitude = lon, latitude = lat), measuredAt = Instant.fromEpochSeconds(0)),
        TimeSource.Monotonic.markNow(),
    )

    private class FakeProvider(granted: Boolean = true, private val events: () -> Flow<LocationEvent>) : LocationProvider {
        var collected = false
        override val permission = MutableStateFlow(
            if (granted) LocationPermission.Granted(LocationAccuracyAuthorization.Precise) else LocationPermission.NotGranted(canRequest = true),
        )
        override fun updates(request: LocationRequest): Flow<LocationEvent> {
            collected = true
            return events()
        }
    }

    // ---- lon/lat ----------------------------------------------------------------------------------------------

    @Test
    fun `GeoPoint diventa una Position con la longitudine per prima`() {
        val position = sila.toPosition()

        assertEquals(16.55, position.longitude)
        assertEquals(39.33, position.latitude)
        assertEquals(listOf(16.55, 39.33), position.toList(), "in GeoJSON l'ordine è [lon, lat]")
        assertEquals(sila, position.toGeoPoint())
    }

    // ---- current ----------------------------------------------------------------------------------------------

    @Test
    fun `current restituisce il primo fix e salta gli eventi non disponibili`() = runTest {
        val provider = FakeProvider {
            flowOf(LocationEvent.Unavailable(LocationUnavailableReason.ServicesDisabled), update(39.33, 16.55), update(1.0, 1.0))
        }

        assertEquals(sila, provider.current())
    }

    @Test
    fun `current senza permesso restituisce null senza avviare la posizione`() = runTest {
        val provider = FakeProvider(granted = false) { flowOf(update(39.33, 16.55)) }

        assertNull(provider.current())
        assertFalse(provider.collected)
    }

    @Test
    fun `current senza fix entro il timeout restituisce null`() = runTest {
        val provider = FakeProvider { flow { awaitCancellation() } }

        assertNull(provider.current(timeout = 10.seconds)) // tempo virtuale: il test non aspetta davvero
    }

    @Test
    fun `current con aggiornamenti finiti senza un fix restituisce null`() = runTest {
        val provider = FakeProvider { flowOf(LocationEvent.Unavailable(LocationUnavailableReason.ServicesDisabled)) }

        assertNull(provider.current())
    }

    @Test
    fun `current su una piattaforma senza provider restituisce null`() = runTest {
        val provider = FakeProvider { throw IllegalStateException("nessun backend") }

        assertNull(provider.current())
    }

    // ---- positions --------------------------------------------------------------------------------------------

    @Test
    fun `positions converte ogni fix e salta gli eventi non disponibili`() = runTest {
        val provider = FakeProvider {
            flowOf(update(39.33, 16.55), LocationEvent.Unavailable(LocationUnavailableReason.PermissionDenied), update(39.4, 16.6))
        }

        assertEquals(listOf(sila, GeoPoint(39.4, 16.6)), provider.positions(60.seconds).toList())
    }
}
