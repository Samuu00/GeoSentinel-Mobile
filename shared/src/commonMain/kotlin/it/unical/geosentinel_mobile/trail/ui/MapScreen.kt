package it.unical.geosentinel_mobile.trail.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.hazards_load_failed
import geosentinel_mobile.shared.generated.resources.hazards_nearby_button
import geosentinel_mobile.shared.generated.resources.rescue_map_partial
import geosentinel_mobile.shared.generated.resources.location_denied_body
import geosentinel_mobile.shared.generated.resources.location_denied_title
import geosentinel_mobile.shared.generated.resources.location_open_settings
import geosentinel_mobile.shared.generated.resources.location_rationale_allow
import geosentinel_mobile.shared.generated.resources.location_rationale_body
import geosentinel_mobile.shared.generated.resources.location_rationale_title
import geosentinel_mobile.shared.generated.resources.map_my_location
import geosentinel_mobile.shared.generated.resources.report_open
import geosentinel_mobile.shared.generated.resources.map_type_button
import geosentinel_mobile.shared.generated.resources.map_type_satellite
import geosentinel_mobile.shared.generated.resources.map_type_streets
import geosentinel_mobile.shared.generated.resources.map_type_title
import geosentinel_mobile.shared.generated.resources.map_zoom_in
import geosentinel_mobile.shared.generated.resources.map_zoom_out
import geosentinel_mobile.shared.generated.resources.poi_close
import geosentinel_mobile.shared.generated.resources.poi_open_trail
import geosentinel_mobile.shared.generated.resources.trail_length_km
import geosentinel_mobile.shared.generated.resources.trail_retry
import geosentinel_mobile.shared.generated.resources.trails_load_failed
import geosentinel_mobile.shared.generated.resources.trails_loading
import geosentinel_mobile.shared.generated.resources.trails_nearby_button
import geosentinel_mobile.shared.generated.resources.trails_nearby_empty
import geosentinel_mobile.shared.generated.resources.trails_nearby_title
import geosentinel_mobile.shared.generated.resources.trails_nearby_waiting
import geosentinel_mobile.shared.generated.resources.trails_too_many
import geosentinel_mobile.shared.generated.resources.trails_zoom_in_hint
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.theme.LocalStatusBarOverDark
import it.unical.geosentinel_mobile.core.push.PushEvent
import it.unical.geosentinel_mobile.core.push.PushManager
import it.unical.geosentinel_mobile.hazard.ui.HazardCard
import it.unical.geosentinel_mobile.hazard.ui.HazardMapViewModel
import it.unical.geosentinel_mobile.hazard.ui.HazardMarkers
import it.unical.geosentinel_mobile.hazard.ui.NearbyHazards
import it.unical.geosentinel_mobile.hazard.ui.NearbyHazardsSheet
import it.unical.geosentinel_mobile.refuge.ui.RefugeMapViewModel
import it.unical.geosentinel_mobile.refuge.ui.RefugeMarkers
import it.unical.geosentinel_mobile.rescue.ui.RescueMapViewModel
import it.unical.geosentinel_mobile.rescue.ui.RescueRefreshEvery
import it.unical.geosentinel_mobile.rescue.ui.SosAlertMarkers
import it.unical.geosentinel_mobile.trail.data.MapSettings
import it.unical.geosentinel_mobile.trail.data.MapType
import kotlin.math.max
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import it.unical.geosentinel_mobile.sos.data.SosState
import it.unical.geosentinel_mobile.sos.ui.SosController
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.location.rememberSystemSettingsLauncher
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.attributions
import org.maplibre.compose.overlay.DisappearingCompassButton
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.LocalViewportInsets
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.style.BaseStyle

// Senza posizione la mappa parte dalla Sila, dove stanno i sentieri del seed. Anche la lista dei rifugi parte da qui
internal val DefaultArea = GeoPoint(lat = 39.33, lon = 16.55)
private const val DefaultZoom = 9.5
private const val MyLocationZoom = 14.0
// La mappa del form della segnalazione parte almeno da qui: abbastanza vicina per mettere il segnaposto
private const val ReportZoom = 13.0

// Stili della mappa in composeResources/files/map, generati da tools/map-styles (tutti gratuiti e senza chiave):
// Stradale = OpenFreeMap ricolorato con una palette tipo Google Maps (chiaro e scuro), Satellite = Sentinel-2 di EOX
// con strade ed etichette sopra. Le attribuzioni stanno nelle sorgenti di ogni stile.
// (Niente mappe raster PNG come OpenTopoMap: il motore di maplibre-compose 0.19 su Android non le disegna in modo
// affidabile; la Topografica è stata tolta il 03/10.)
// Fuori dal main thread (centinaia di KB). File mancante o illeggibile: mappa vuota invece di un crash.
private suspend fun loadStyle(type: MapType, dark: Boolean): BaseStyle = withContext(Dispatchers.Default) {
    val file = when (type) {
        MapType.STREETS -> if (dark) "streets_dark" else "streets_light"
        MapType.SATELLITE -> "satellite"
    }
    try {
        BaseStyle.Json(Res.readBytes("files/map/$file.json").decodeToString())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        BaseStyle.Empty
    }
}

/** Il tipo di mappa scelto dall'utente: null finché non si è letta la scelta salvata. */
@Composable
internal fun rememberSavedMapType(): MapType? = koinInject<MapSettings>().type.collectAsState(initial = null).value

/**
 * Lo stile della mappa per [mapType] nel tema attuale. Vuoto finché il tipo non è noto: niente stradale caricata e
 * subito sostituita dal satellite. Lo usano la mappa e l'anteprima del dettaglio.
 */
@Composable
internal fun rememberMapStyle(mapType: MapType?): BaseStyle {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // lo stile si legge dal file (pochi ms): fino ad allora la mappa è vuota
    val style by produceState<BaseStyle>(BaseStyle.Empty, mapType, dark) {
        value = mapType?.let { loadStyle(it, dark) } ?: BaseStyle.Empty
    }
    return style
}

// Il bordo chiaro (o scuro) sotto le linee dei sentieri: le stacca dalla mappa
internal fun trailCasing(mapIsDark: Boolean): Color = if (mapIsDark) Color.Black.copy(alpha = 0.55f) else Color.White

// Quanto si aspetta un fix dopo "La mia posizione" o "Sentieri vicini" (posizione spenta, nessun segnale)
private val FixWait = 15.seconds
// Un fix più vecchio non vale per "Sentieri vicini": su Android il primo è l'ultima posizione nota, anche di giorni fa
private val MaxFixAge = 2.minutes

// Cosa mostra il dialogo della posizione
private enum class LocationPrompt { None, Rationale, Denied }

// Cosa fare quando arriva il permesso
private enum class LocationGoal { Center, Nearby, NearbyHazards }

// Il tocco su "Pericoli vicini" porta almeno a questo zoom: il triangolo si vede in mezzo ai sentieri
private const val HazardZoom = 15.0

/**
 * [showHazards]: pericoli sulla mappa e "Pericoli vicini", solo da loggati (M4.4). Al logout spariscono.
 * [onOpenRefuge]: i rifugi sulla mappa (M5.1), null per l'ospite (le rotte dei rifugi richiedono il login).
 * [onOpenAlert]: le allerte SOS aperte (M7.2), solo per gli operatori; null per tutti gli altri.
 */
@Composable
fun MapScreen(
    onOpenTrail: (String) -> Unit,
    onReportHazard: ((center: GeoPoint, zoom: Double) -> Unit)?,
    showHazards: Boolean,
    onOpenRefuge: ((String) -> Unit)?,
    onOpenAlert: ((String) -> Unit)?,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = MaterialTheme.colorScheme
    // Provider di default di MapLibre (LocationManager / CLLocationManager). Non chiede il permesso da solo:
    // se c'è già, il pallino compare subito; se non c'è, la mappa funziona lo stesso dall'area di default.
    val location = rememberLocationState()
    val mapSettings = koinInject<MapSettings>()
    val mapType = rememberSavedMapType()
    val style = rememberMapStyle(mapType)
    val viewModel = koinViewModel<TrailMapViewModel>()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val hazardViewModel = koinViewModel<HazardMapViewModel>()
    val hazardUi by hazardViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(showHazards) { hazardViewModel.setEnabled(showHazards) }
    val refugeViewModel = koinViewModel<RefugeMapViewModel>()
    val refuges by refugeViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(onOpenRefuge != null) { refugeViewModel.setEnabled(onOpenRefuge != null) }
    val alertViewModel = koinViewModel<RescueMapViewModel>()
    val alerts by alertViewModel.state.collectAsStateWithLifecycle()
    val alertsPartial by alertViewModel.partial.collectAsStateWithLifecycle()
    LaunchedEffect(onOpenAlert != null) { alertViewModel.setEnabled(onOpenAlert != null) }
    // A mappa ferma le allerte si rileggono come nella scheda Allerte: subito a ogni ritorno sulla mappa (dalla schermata
    // SOS, da un dettaglio, dal background), poi ogni 30 s, e a ogni SOS nuovo (push). Il proprio SOS non arriva come
    // push: si rilegge quando cambia (lanciato, annullato, chiuso)
    if (onOpenAlert != null) {
        val push = koinInject<PushManager>()
        val sos = koinInject<SosController>()
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    alertViewModel.refresh()
                    delay(RescueRefreshEvery)
                }
            }
        }
        LaunchedEffect(push) { push.received.filterIsInstance<PushEvent.SosRaised>().collect { alertViewModel.refresh() } }
        LaunchedEffect(sos) {
            sos.state.map { (it as? SosState.Raised)?.alert?.let { alert -> alert.id to alert.status } }
                .distinctUntilChanged()
                .drop(1)
                .collect { alertViewModel.refresh() }
        }
    }
    var choosingType by rememberSaveable { mutableStateOf(false) }
    var selectedPoiId by rememberSaveable { mutableStateOf<String?>(null) }
    // La mappa sta sotto la barra di stato: icone chiare su satellite e stradale scura, scure sulla stradale chiara.
    // Lasciando la mappa tornano a seguire il tema.
    val statusBarOverDark = LocalStatusBarOverDark.current
    val mapIsDark = mapType == MapType.SATELLITE || dark
    DisposableEffect(mapIsDark) {
        statusBarOverDark.value = mapIsDark
        onDispose { statusBarOverDark.value = null }
    }
    val casing = trailCasing(mapIsDark)
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = DefaultArea.toPosition(), zoom = DefaultZoom),
    ) {
        TrailLines(ui.trails, casing = casing, unknownColor = colors.onSurfaceVariant, onTrailClick = onOpenTrail)
        PoiMarkers(
            ui.pois,
            background = colors.surfaceContainerLowest,
            outline = colors.outline,
            symbol = colors.primary,
            onPoiClick = {
                selectedPoiId = it
                hazardViewModel.clearSelection()
            },
        )
        if (onOpenRefuge != null) {
            RefugeMarkers(refuges, fill = colors.primary, outline = colors.surfaceContainerLowest, symbol = colors.onPrimary, onRefugeClick = onOpenRefuge)
        }
        // sopra sentieri, POI e rifugi: un pericolo non deve restare nascosto
        HazardMarkers(hazardUi.hazards, halo = casing, otherColor = colors.onSurfaceVariant) { id ->
            selectedPoiId = null
            hazardViewModel.select(id)
        }
        // le allerte sopra i pericoli: per l'operatore sono la cosa più importante della mappa
        if (onOpenAlert != null) {
            SosAlertMarkers(alerts, light = colors.surfaceContainerLowest, onAlertClick = onOpenAlert)
        }
        // il pallino sopra tutto
        LocationIndicatorLayer(id = "posizione", locationState = location)
    }
    val settings = rememberSystemSettingsLauncher()
    val scope = rememberCoroutineScope()
    var prompt by rememberSaveable { mutableStateOf(LocationPrompt.None) }
    var goal by rememberSaveable { mutableStateOf(LocationGoal.Center) }
    // "La mia posizione" toccato: si centra appena arriva un fix (subito, se c'è già)
    var centerOnFix by remember { mutableStateOf(false) }

    // Mappa ferma: sentieri e POI dell'area visibile (il ViewModel aspetta 400 ms e salta le aree già caricate)
    LaunchedEffect(mapState) {
        snapshotFlow { if (mapState.isCameraMoving) null else mapState.viewport }.filterNotNull().collect { viewport ->
            val bounds = viewport.visibleBounds
            val view = GeoBounds.of(bounds.west, bounds.south, bounds.east, bounds.north)
            viewModel.onCameraIdle(view, viewport.cameraPosition.zoom)
            hazardViewModel.onCameraIdle(view, viewport.cameraPosition.zoom)
            refugeViewModel.onCameraIdle(view, viewport.cameraPosition.zoom)
            alertViewModel.onCameraIdle(view, viewport.cameraPosition.zoom)
        }
    }

    LaunchedEffect(centerOnFix, location.lastLocation) {
        val fix = location.lastLocation ?: return@LaunchedEffect
        if (!centerOnFix) return@LaunchedEffect
        // nello scope della schermata: centerOnFix = false cambia la chiave e annullerebbe un'animazione lanciata qui
        scope.launch {
            mapState.animateCamera(CameraUpdate(target = fix.position, zoom = max(mapState.cameraPosition.zoom, MyLocationZoom)))
        }
        centerOnFix = false
    }
    // Si smette di aspettare il fix dopo FixWait (anche con il permesso negato nel dialogo di sistema)
    // o appena l'utente sposta la mappa da sé: la camera non deve saltare dopo
    LaunchedEffect(centerOnFix) {
        if (!centerOnFix) return@LaunchedEffect
        delay(FixWait)
        centerOnFix = false
    }
    LaunchedEffect(mapState.isCameraMoving, mapState.cameraMoveReason) {
        if (mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.GESTURE) centerOnFix = false
    }

    // "Sentieri vicini": serve un fix recente, non l'ultima posizione nota
    val waitingForNearbyFix = ui.nearby == NearbyTrails.WaitingForFix
    LaunchedEffect(waitingForNearbyFix, location.lastLocation) {
        if (!waitingForNearbyFix) return@LaunchedEffect
        location.recentFix()?.let(viewModel::onNearbyFix)
    }
    LaunchedEffect(waitingForNearbyFix) {
        if (!waitingForNearbyFix) return@LaunchedEffect
        delay(FixWait)
        viewModel.onNearbyNoFix()
    }
    // "Pericoli vicini": la stessa attesa
    val waitingForHazardsFix = hazardUi.nearby == NearbyHazards.WaitingForFix
    LaunchedEffect(waitingForHazardsFix, location.lastLocation) {
        if (!waitingForHazardsFix) return@LaunchedEffect
        location.recentFix()?.let(hazardViewModel::onNearbyFix)
    }
    LaunchedEffect(waitingForHazardsFix) {
        if (!waitingForHazardsFix) return@LaunchedEffect
        delay(FixWait)
        hazardViewModel.onNearbyNoFix()
    }

    // Prima del dialogo di sistema si spiega perché: il permesso si chiede solo quando serve (§M3.1)
    fun withLocation(target: LocationGoal, action: () -> Unit) {
        goal = target
        prompt = when (val permission = location.permission) {
            is LocationPermission.Granted -> {
                action()
                LocationPrompt.None
            }
            // canRequest false: negato per sempre, il sistema non mostra più il dialogo
            is LocationPermission.NotGranted if permission.canRequest == false -> LocationPrompt.Denied
            else -> LocationPrompt.Rationale
        }
    }

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(
            state = mapState,
            // la mappa arriva sotto la barra di stato; scala, bussola e attribuzione restano visibili,
            // e in basso lasciano spazio al pulsante SOS
            viewportInsets = PaddingValues(
                top = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding(),
                bottom = 96.dp,
            ),
            // Overlay di default di MapLibre senza il logo (la licenza BSD non lo richiede), stesso posizionamento.
            // L'attribuzione (la "i") resta: OpenStreetMap (ODbL) e OpenFreeMap la chiedono visibile sulla mappa.
            overlay = {
                val insets = LocalViewportInsets.current
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(insets)
                        .consumeWindowInsets(insets)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(MapOverlay.Spacing),
                ) {
                    DisappearingScaleBar(
                        metersPerDp = { mapState.viewport?.metersPerDpAtTarget ?: 0.0 },
                        zoom = { mapState.cameraPosition.zoom },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    DisappearingCompassButton(modifier = Modifier.align(Alignment.TopEnd))
                    // In basso a sinistra, lontano dall'SOS, e nei colori del tema: leggibile su ogni mappa
                    AttributionButton(mapState, Modifier.align(Alignment.BottomStart))
                }
            },
        )
        // In alto al centro: "Sentieri vicini" e, sotto, lo stato dei sentieri (carico, errore, zoom).
        // Margini laterali per non coprire scala e bussola.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = 12.dp, start = 72.dp, end = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapPill(stringResource(Res.string.trails_nearby_button), emphasized = true) {
                    withLocation(LocationGoal.Nearby, viewModel::onNearbyRequested)
                }
                if (showHazards) {
                    MapPill(stringResource(Res.string.hazards_nearby_button), emphasized = true) {
                        withLocation(LocationGoal.NearbyHazards, hazardViewModel::onNearbyRequested)
                    }
                }
            }
            // un avviso alla volta: prima quello dei sentieri, che dice già se manca la rete
            val hazardsFailed = showHazards && hazardUi.failed && !ui.zoomTooLow
            val status = when {
                ui.failed != null -> stringResource(Res.string.trails_load_failed)
                ui.zoomTooLow -> stringResource(Res.string.trails_zoom_in_hint)
                hazardsFailed -> stringResource(Res.string.hazards_load_failed)
                // l'operatore deve sapere che sulla mappa non ci sono tutte le allerte (la scheda Allerte sì)
                onOpenAlert != null && alertsPartial -> stringResource(Res.string.rescue_map_partial)
                ui.truncated -> stringResource(Res.string.trails_too_many)
                ui.loading -> stringResource(Res.string.trails_loading)
                else -> null
            }
            if (status != null) {
                val onRetry = when {
                    ui.failed != null -> {
                        {
                            viewModel.retry()
                            hazardViewModel.retry()
                        }
                    }
                    hazardsFailed -> hazardViewModel::retry
                    else -> null
                }
                MapPill(status, onClick = onRetry)
            }
        }
        // Controlli grandi a destra, a metà altezza: lontani dall'SOS (in basso) e raggiungibili col pollice
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapButton(Res.string.map_type_button, Glyph.Layers) { choosingType = true }
            Spacer(Modifier.size(4.dp))
            MapButton(Res.string.map_zoom_in, Glyph.Plus) {
                scope.launch { mapState.animateCamera(CameraUpdate(zoom = mapState.cameraPosition.zoom + 1)) }
            }
            MapButton(Res.string.map_zoom_out, Glyph.Minus) {
                scope.launch { mapState.animateCamera(CameraUpdate(zoom = mapState.cameraPosition.zoom - 1)) }
            }
            MapButton(Res.string.map_my_location, Glyph.Locate) {
                withLocation(LocationGoal.Center) { centerOnFix = true }
            }
            // solo da loggati (null per l'ospite): il form parte dal punto che si sta guardando
            if (onReportHazard != null) {
                Spacer(Modifier.size(4.dp))
                MapButton(Res.string.report_open, Glyph.Hazard) {
                    onReportHazard(mapState.cameraPosition.target.toGeoPoint(), max(mapState.cameraPosition.zoom, ReportZoom))
                }
            }
        }
        // POI toccato: nome e tipo in una card in basso a sinistra (l'SOS sta a destra)
        ui.pois.firstOrNull { it.id == selectedPoiId }?.let { poi ->
            PoiCard(
                poi = poi,
                onOpenTrail = poi.trailId?.let { trailId -> { onOpenTrail(trailId) } },
                onClose = { selectedPoiId = null },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom))
                    .padding(start = 16.dp, end = 136.dp, bottom = 16.dp),
            )
        }
        // Pericolo toccato (o scelto da "Pericoli vicini"): stessa posizione della card dei POI, mai insieme
        hazardUi.selected?.let { hazard ->
            HazardCard(
                hazard = hazard,
                onOpenTrail = hazard.trailId?.let { trailId -> { onOpenTrail(trailId) } },
                onClose = hazardViewModel::clearSelection,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom))
                    .padding(start = 16.dp, end = 136.dp, bottom = 16.dp),
            )
        }
    }

    if (choosingType) {
        MapTypeSheet(
            current = mapType ?: MapType.STREETS,
            onSelect = { type ->
                choosingType = false
                scope.launch { mapSettings.setType(type) }
            },
            onDismiss = { choosingType = false },
        )
    }

    if (ui.nearby != NearbyTrails.Hidden) {
        NearbySheet(
            nearby = ui.nearby,
            onOpenTrail = { id ->
                viewModel.closeNearby()
                onOpenTrail(id)
            },
            onRetry = { withLocation(LocationGoal.Nearby, viewModel::onNearbyRequested) },
            onDismiss = viewModel::closeNearby,
        )
    }

    if (hazardUi.nearby != NearbyHazards.Hidden) {
        NearbyHazardsSheet(
            nearby = hazardUi.nearby,
            // si chiude il foglio e la mappa va sul pericolo, con la sua card
            onSelect = { hazard ->
                hazardViewModel.closeNearby()
                selectedPoiId = null
                hazardViewModel.select(hazard)
                scope.launch {
                    mapState.animateCamera(
                        CameraUpdate(target = hazard.location.toGeoPoint().toPosition(), zoom = max(mapState.cameraPosition.zoom, HazardZoom)),
                    )
                }
            },
            onRetry = { withLocation(LocationGoal.NearbyHazards, hazardViewModel::onNearbyRequested) },
            onDismiss = hazardViewModel::closeNearby,
        )
    }

    when (prompt) {
        LocationPrompt.Rationale -> LocationDialog(
            title = Res.string.location_rationale_title,
            body = Res.string.location_rationale_body,
            confirm = Res.string.location_rationale_allow,
            onConfirm = {
                // se il permesso viene negato nel dialogo di sistema, l'attesa del fix finisce dopo FixWait
                when (goal) {
                    LocationGoal.Center -> centerOnFix = true
                    LocationGoal.Nearby -> viewModel.onNearbyRequested()
                    LocationGoal.NearbyHazards -> hazardViewModel.onNearbyRequested()
                }
                location.requestPermission()
            },
            onDismiss = { prompt = LocationPrompt.None },
        )
        LocationPrompt.Denied -> LocationDialog(
            title = Res.string.location_denied_title,
            body = Res.string.location_denied_body,
            confirm = Res.string.location_open_settings,
            onConfirm = { settings.openApplicationSettings() },
            onDismiss = { prompt = LocationPrompt.None },
        )
        LocationPrompt.None -> Unit
    }
}

// L'ultimo fix, solo se è di adesso (MaxFixAge). Lo usa anche il form della segnalazione (M4.1)
internal fun LocationState.recentFix(): GeoPoint? {
    val fix = lastLocation ?: return null
    val age = lastLocationMeasurementMark?.elapsedNow() ?: return null
    return if (age <= MaxFixAge) fix.position.toGeoPoint() else null
}

// Capsula sopra la mappa, nei colori delle card: un'azione (emphasized, nel verde del tema) o un avviso
@Composable
private fun MapPill(text: String, emphasized: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val container = if (emphasized) colors.primary else colors.surfaceContainerLowest
    val content = if (emphasized) colors.onPrimary else colors.onSurface
    val border = if (emphasized) null else BorderStroke(1.dp, colors.outlineVariant)
    // gli avvisi si annunciano da soli (carico, errore), le azioni no
    val modifier = if (emphasized) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    val label: @Composable () -> Unit = {
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content, maxLines = 2)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = CircleShape, color = container, border = border, shadowElevation = 2.dp, modifier = modifier) { label() }
    } else {
        Surface(shape = CircleShape, color = container, border = border, shadowElevation = 2.dp, modifier = modifier) { label() }
    }
}

@Composable
private fun PoiCard(poi: PoiResponse, onOpenTrail: (() -> Unit)?, onClose: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 3.dp,
        modifier = modifier.widthIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // decorativa: il tipo è scritto accanto
            Icon(poi.type.icon(), contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(poi.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(poi.type.label()), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (onOpenTrail != null) {
                    TextButton(onClick = onOpenTrail, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.poi_open_trail), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.poi_close))
            }
        }
    }
}

// Foglio "Sentieri vicini": mentre cerca la posizione e carica, poi la lista ordinata per distanza (dal server)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbySheet(nearby: NearbyTrails, onOpenTrail: (String) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(Res.string.trails_nearby_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
            )
            Spacer(Modifier.size(12.dp))
            when (nearby) {
                NearbyTrails.WaitingForFix, NearbyTrails.Loading -> Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(if (nearby == NearbyTrails.WaitingForFix) Res.string.trails_nearby_waiting else Res.string.trails_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is NearbyTrails.Failed -> Column(Modifier.padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(nearby.message.resolve(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.trail_retry), fontWeight = FontWeight.SemiBold)
                    }
                }
                is NearbyTrails.Loaded -> if (nearby.trails.isEmpty()) {
                    Text(
                        stringResource(Res.string.trails_nearby_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(nearby.trails, key = { it.id }) { trail ->
                            TrailRow(trail, onClick = { onOpenTrail(trail.id) })
                            HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                NearbyTrails.Hidden -> Unit
            }
        }
    }
}

// Riga di un sentiero: nome, difficoltà e lunghezza, stato con il suo simbolo (non solo il colore)
@Composable
private fun TrailRow(trail: TrailSummaryResponse, onClick: () -> Unit) {
    val difficulty = TrailDifficulty.of(trail.difficulty.value)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(trail.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(difficulty.label) + " · " + stringResource(Res.string.trail_length_km, formatKm(trail.lengthM)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        TrailStateBadge(TrailState.of(trail.status.value))
    }
}

@Composable
private fun LocationDialog(title: StringResource, body: StringResource, confirm: StringResource, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.auth_cancel)) } },
    )
}
