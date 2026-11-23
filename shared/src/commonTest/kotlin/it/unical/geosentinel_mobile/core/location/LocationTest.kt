// Test delle funzioni di posizione in core/location: conversione tra GeoPoint e Position di MapLibre, lettura della
// posizione corrente con timeout e permessi, flusso delle posizioni e spostamento del centro della mappa trascinata.
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

/** Verifica le funzioni di posizione usando un LocationProvider finto al posto del GPS. */
class LocationTest {

    /** Un punto della Sila: serve anche a riconoscere un'inversione di latitudine e longitudine. */
    private val sila = GeoPoint(lat = 39.33, lon = 16.55)

    /** Crea un evento di posizione disponibile alle coordinate indicate. */
    private fun update(lat: Double, lon: Double) = LocationEvent.Update(
        LocationMeasurement(position = Position(longitude = lon, latitude = lat), measuredAt = Instant.fromEpochSeconds(0)),
        TimeSource.Monotonic.markNow(),
    )

    /**
     * Provider di posizione finto. [granted] dice se il permesso è concesso; [events] produce il flusso di eventi che
     * il provider emette. Registra in [collected] se qualcuno ha chiesto gli aggiornamenti.
     */
    private class FakeProvider(granted: Boolean = true, private val events: () -> Flow<LocationEvent>) : LocationProvider {
        /** Vero se gli aggiornamenti di posizione sono stati richiesti almeno una volta. */
        var collected = false
        /** Stato del permesso di posizione, concesso con precisione alta oppure non concesso ma richiedibile. */
        override val permission = MutableStateFlow(
            if (granted) LocationPermission.Granted(LocationAccuracyAuthorization.Precise) else LocationPermission.NotGranted(canRequest = true),
        )
        /** Segna che gli aggiornamenti sono stati richiesti e restituisce il flusso di eventi del test. */
        override fun updates(request: LocationRequest): Flow<LocationEvent> {
            collected = true
            return events()
        }
    }


    /** La conversione mette la longitudine per prima, come vuole GeoJSON, e la conversione inversa restituisce lo stesso punto. */
    @Test
    fun `GeoPoint diventa una Position con la longitudine per prima`() {
        val position = sila.toPosition()

        assertEquals(16.55, position.longitude)
        assertEquals(39.33, position.latitude)
        assertEquals(listOf(16.55, 39.33), position.toList(), "in GeoJSON l'ordine è [lon, lat]")
        assertEquals(sila, position.toGeoPoint())
    }


    /** current ignora gli eventi di posizione non disponibile e restituisce la prima posizione valida ricevuta. */
    @Test
    fun `current restituisce il primo fix e salta gli eventi non disponibili`() = runTest {
        val provider = FakeProvider {
            flowOf(LocationEvent.Unavailable(LocationUnavailableReason.ServicesDisabled), update(39.33, 16.55), update(1.0, 1.0))
        }

        assertEquals(sila, provider.current())
    }

    /** Senza permesso current restituisce null e non avvia nemmeno la richiesta di posizione. */
    @Test
    fun `current senza permesso restituisce null senza avviare la posizione`() = runTest {
        val provider = FakeProvider(granted = false) { flowOf(update(39.33, 16.55)) }

        assertNull(provider.current())
        assertFalse(provider.collected)
    }

    /**
     * Se non arriva nessuna posizione entro il timeout, current restituisce null. Il test usa il tempo virtuale di
     * runTest, quindi non aspetta davvero 10 secondi.
     */
    @Test
    fun `current senza fix entro il timeout restituisce null`() = runTest {
        val provider = FakeProvider { flow { awaitCancellation() } }

        assertNull(provider.current(timeout = 10.seconds))
    }

    /** Se il flusso di eventi finisce senza una posizione valida, current restituisce null. */
    @Test
    fun `current con aggiornamenti finiti senza un fix restituisce null`() = runTest {
        val provider = FakeProvider { flowOf(LocationEvent.Unavailable(LocationUnavailableReason.ServicesDisabled)) }

        assertNull(provider.current())
    }

    /** Se il provider lancia un'eccezione (piattaforma senza servizio di posizione), current restituisce null invece di propagarla. */
    @Test
    fun `current su una piattaforma senza provider restituisce null`() = runTest {
        val provider = FakeProvider { throw IllegalStateException("nessun backend") }

        assertNull(provider.current())
    }


    /** Il flusso positions converte ogni posizione valida in GeoPoint e salta gli eventi di posizione non disponibile. */
    @Test
    fun `positions converte ogni fix e salta gli eventi non disponibili`() = runTest {
        val provider = FakeProvider {
            flowOf(update(39.33, 16.55), LocationEvent.Unavailable(LocationUnavailableReason.PermissionDenied), update(39.4, 16.6))
        }

        assertEquals(listOf(sila, GeoPoint(39.4, 16.6)), provider.positions(60.seconds).toList())
    }

    /**
     * Trascinare la mappa con il dito sposta il centro nella direzione opposta: a destra il centro va a ovest, in basso
     * va a nord. Oltre l'antimeridiano la longitudine viene riportata nell'intervallo da -180 a 180.
     */
    @Test
    fun `trascinare la mappa sposta il centro dalla parte opposta al dito`() {
        val center = sila.toPosition()
        // Dito verso destra di 100 dp con 10 metri per dp: il centro si sposta di 1 km verso ovest.
        val west = center.dragged(dxDp = 100.0, dyDp = 0.0, metersPerDp = 10.0)
        assertEquals(39.33, west.latitude, 1e-9)
        assertEquals(16.55 - 1000 / (111_320.0 * kotlin.math.cos(39.33 * kotlin.math.PI / 180)), west.longitude, 1e-9)
        // Dito verso il basso: il centro si sposta verso nord.
        assertEquals(39.33 + 1000 / 111_320.0, center.dragged(0.0, 100.0, 10.0).latitude, 1e-9)
        // Vicino all'antimeridiano lo spostamento verso est riporta la longitudine nel lato negativo.
        val east = org.maplibre.spatialk.geojson.Position(longitude = 179.99, latitude = 0.0).dragged(-1000.0, 0.0, 10.0)
        assertEquals(-179.92, east.longitude, 0.01)
    }
}
