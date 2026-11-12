package it.unical.geosentinel_mobile.trail.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.hazards_load_failed
import geosentinel_mobile.shared.generated.resources.rescue_map_partial
import geosentinel_mobile.shared.generated.resources.location_denied_body
import geosentinel_mobile.shared.generated.resources.location_denied_title
import geosentinel_mobile.shared.generated.resources.location_open_settings
import geosentinel_mobile.shared.generated.resources.location_rationale_allow
import geosentinel_mobile.shared.generated.resources.location_rationale_body
import geosentinel_mobile.shared.generated.resources.location_rationale_title
import geosentinel_mobile.shared.generated.resources.map_my_location
import geosentinel_mobile.shared.generated.resources.near_me_hazards
import geosentinel_mobile.shared.generated.resources.near_me_title
import geosentinel_mobile.shared.generated.resources.near_me_trails
import geosentinel_mobile.shared.generated.resources.poi_create_title
import geosentinel_mobile.shared.generated.resources.report_open
import geosentinel_mobile.shared.generated.resources.map_type_button
import geosentinel_mobile.shared.generated.resources.map_type_satellite
import geosentinel_mobile.shared.generated.resources.map_type_streets
import geosentinel_mobile.shared.generated.resources.map_type_title
import geosentinel_mobile.shared.generated.resources.poi_close
import geosentinel_mobile.shared.generated.resources.poi_open_trail
import geosentinel_mobile.shared.generated.resources.trail_length_km
import geosentinel_mobile.shared.generated.resources.trail_retry
import geosentinel_mobile.shared.generated.resources.trails_load_failed
import geosentinel_mobile.shared.generated.resources.trails_loading
import geosentinel_mobile.shared.generated.resources.trails_nearby_empty
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
import it.unical.geosentinel_mobile.core.ui.SegmentedControl
import it.unical.geosentinel_mobile.core.ui.TonalPillButton
import it.unical.geosentinel_mobile.core.ui.islandShadow
import androidx.compose.foundation.shape.RoundedCornerShape
import it.unical.geosentinel_mobile.core.ui.theme.LocalStatusBarOverDark
import it.unical.geosentinel_mobile.core.push.PushEvent
import it.unical.geosentinel_mobile.core.push.PushManager
import it.unical.geosentinel_mobile.hazard.ui.HazardCard
import it.unical.geosentinel_mobile.hazard.ui.HazardMapViewModel
import it.unical.geosentinel_mobile.hazard.ui.HazardMarkers
import it.unical.geosentinel_mobile.hazard.ui.NearbyHazards
import it.unical.geosentinel_mobile.hazard.ui.NearbyHazardsList
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
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import androidx.compose.ui.unit.DpSize
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
private enum class LocationGoal { Center, NearMe, Nearby, NearbyHazards }

// Le due schede del foglio "Vicino a te"
private enum class NearTab(val label: StringResource) { Trails(Res.string.near_me_trails), Hazards(Res.string.near_me_hazards) }

// Spazio sotto i controlli per il pulsante SOS (72 dp) e il suo margine
private val SosClearance = 104.dp

// Il tocco su "Pericoli vicini" porta almeno a questo zoom: il triangolo si vede in mezzo ai sentieri
private const val HazardZoom = 15.0

/**
 * [showHazards]: pericoli sulla mappa e "Pericoli vicini", solo da loggati (M4.4). Al logout spariscono.
 * [onOpenRefuge]: i rifugi sulla mappa (M5.1), null per l'ospite (le rotte dei rifugi richiedono il login).
 * [onOpenAlert]: le allerte SOS aperte (M7.2), solo per gli operatori; null per tutti gli altri.
 * [bottomBar]: quanto occupa la barra in basso, che galleggia sopra la mappa: la mappa ci passa sotto, i controlli no.
 */
@Composable
fun MapScreen(
    onOpenTrail: (String) -> Unit,
    onReportHazard: ((center: GeoPoint, zoom: Double) -> Unit)?,
    showHazards: Boolean,
    // solo per il gestore del parco (M8.1): nuovo POI dal punto che si sta guardando
    onCreatePoi: ((center: GeoPoint, zoom: Double) -> Unit)?,
    onOpenRefuge: ((String) -> Unit)?,
    onOpenAlert: ((String) -> Unit)?,
    bottomBar: Dp = 0.dp,
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
    // a ogni ritorno sulla mappa (es. dopo una segnalazione) le proprie in verifica si rileggono subito
    LaunchedEffect(Unit) { hazardViewModel.refreshMine() }
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
    // remember, non rememberSaveable: tornando sulla mappa da un'altra pagina o scheda la card del POI è chiusa
    var selectedPoiId by remember { mutableStateOf<String?>(null) }
    // lo stesso per la card del pericolo, la cui selezione vive nel ViewModel
    DisposableEffect(Unit) { onDispose { hazardViewModel.clearSelection() } }
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
            label = mapLabelInk(mapIsDark),
            halo = mapLabelHalo(mapIsDark),
            onPoiClick = {
                selectedPoiId = it
                hazardViewModel.clearSelection()
            },
        )
        if (onOpenRefuge != null) {
            RefugeMarkers(refuges, label = mapLabelInk(mapIsDark), halo = mapLabelHalo(mapIsDark), onRefugeClick = onOpenRefuge)
        }
        // sopra sentieri, POI e rifugi: un pericolo non deve restare nascosto
        HazardMarkers(hazardUi.shown, halo = casing, otherColor = colors.onSurfaceVariant) { id ->
            selectedPoiId = null
            hazardViewModel.select(id)
        }
        // le allerte sopra i pericoli: per l'operatore sono la cosa più importante della mappa
        if (onOpenAlert != null) {
            SosAlertMarkers(alerts, light = colors.surfaceContainerLowest, onAlertClick = onOpenAlert)
        }
        // il pallino sopra tutto
        // come su iPhone: pallino blu con l'anello bianco, cerchio della precisione azzurro tenue, cono della direzione
        LocationIndicatorLayer(
            id = "posizione",
            locationState = location,
            topImage = image(rememberLocationDot(), DpSize(24.dp, 24.dp)),
            shadowImage = null,
            bearingAccuracyColor = const(LocationBlue.copy(alpha = 0.25f)),
            accuracyRadiusColor = LocationBlue.copy(alpha = 0.12f),
            accuracyRadiusBorderColor = LocationBlue.copy(alpha = 0.35f),
        )
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

    // "Vicino a te": le due ricerche partono insieme, così cambiare scheda è immediato; resta l'ultima scheda scelta
    var nearTab by rememberSaveable { mutableStateOf(NearTab.Trails) }
    fun openNearMe() {
        viewModel.onNearbyRequested()
        if (showHazards) hazardViewModel.onNearbyRequested()
    }
    fun closeNearMe() {
        viewModel.closeNearby()
        hazardViewModel.closeNearby()
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
            // la mappa arriva sotto la barra di stato e sotto la barra in basso; scala, bussola e attribuzione restano
            // visibili sopra la barra e sopra il pulsante SOS
            viewportInsets = PaddingValues(
                top = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding(),
                bottom = bottomBar + 96.dp,
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
                    // a sinistra sotto la scala: in alto a destra ora ci sono i comandi della vista
                    DisappearingCompassButton(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp))
                    // In basso a sinistra, lontano dall'SOS, e nei colori del tema: leggibile su ogni mappa
                    AttributionButton(mapState, Modifier.align(Alignment.BottomStart))
                }
            },
        )
        // In alto al centro solo lo stato dei sentieri (carico, errore, zoom): "Vicino a te" è un tondo tra i controlli.
        // Margini laterali per non coprire scala e bussola.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = 12.dp, start = 72.dp, end = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
        // Redesign 11/2026 (seconda passata), sullo schema di Apple Maps:
        // - in alto a destra i comandi della vista in capsule raggruppate: tipo di mappa e posizione, poi lo zoom
        //   (resta: con i guanti il pizzico non sempre riesce);
        // - in basso a destra, sopra l'SOS, le azioni (segnala, nuovo punto): nella zona del pollice;
        // - in basso a sinistra la barra "Vicino a te", larga come la ricerca di Mappe.
        // In orizzontale (o con il testo grande) i gruppi scorrono invece di finire sotto l'SOS
        // Terza passata (11/2026), come Apple Maps: un solo gruppo di comandi della vista in alto a destra (tipo di mappa,
        // posizione e, per il gestore, il punto nuovo). Niente più +/−: si zooma con il pizzico o il doppio tocco, e la
        // mappa resta libera. In orizzontale (o con il testo grande) il gruppo scorre invece di finire sotto l'SOS
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Top))
                .padding(end = 16.dp, top = 8.dp, bottom = bottomBar + SosClearance)
                .verticalScroll(rememberScrollState())
                .padding(start = 8.dp, bottom = 8.dp),
        ) {
            MapButtonGroup(
                listOfNotNull(
                    MapAction(Res.string.map_type_button, Glyph.Layers) { choosingType = true },
                    MapAction(Res.string.map_my_location, Glyph.Locate) { withLocation(LocationGoal.Center) { centerOnFix = true } },
                    // solo per il gestore del parco (M8.1): il form parte dal punto che si sta guardando
                    onCreatePoi?.let { create ->
                        MapAction(Res.string.poi_create_title, Glyph.AddPoint) {
                            create(mapState.cameraPosition.target.toGeoPoint(), max(mapState.cameraPosition.zoom, ReportZoom))
                        }
                    },
                ),
            )
        }
        // sentieri e pericoli intorno a te: la barra in basso a sinistra apre il foglio con due schede; dentro, a destra,
        // "Segnala un pericolo" (solo da loggati). Nascosta quando c'è la card di un punto toccato, che sta nello stesso posto
        if (selectedPoiId == null && hazardUi.selected == null) {
            NearMeBar(
                onClick = { withLocation(LocationGoal.NearMe, ::openNearMe) },
                onReport = onReportHazard?.let { report ->
                    { report(mapState.cameraPosition.target.toGeoPoint(), max(mapState.cameraPosition.zoom, ReportZoom)) }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    .padding(start = 16.dp, end = 136.dp, bottom = bottomBar + 16.dp),
            )
        }
        // POI toccato: nome e tipo in una card in basso a sinistra (l'SOS sta a destra)
        ui.pois.firstOrNull { it.id == selectedPoiId }?.let { poi ->
            PoiCard(
                poi = poi,
                onOpenTrail = poi.trailId?.let { trailId -> { onOpenTrail(trailId) } },
                onClose = { selectedPoiId = null },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    .padding(start = 16.dp, end = 136.dp, bottom = bottomBar + 16.dp),
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
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    .padding(start = 16.dp, end = 136.dp, bottom = bottomBar + 16.dp),
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

    if (ui.nearby != NearbyTrails.Hidden || hazardUi.nearby != NearbyHazards.Hidden) {
        NearMeSheet(
            tab = nearTab,
            onTabChange = { nearTab = it },
            showHazards = showHazards,
            onDismiss = ::closeNearMe,
            trails = {
                NearbyTrailsList(
                    nearby = ui.nearby,
                    onOpenTrail = { id ->
                        closeNearMe()
                        onOpenTrail(id)
                    },
                    onRetry = { withLocation(LocationGoal.Nearby, viewModel::onNearbyRequested) },
                )
            },
            hazards = {
                NearbyHazardsList(
                    nearby = hazardUi.nearby,
                    // si chiude il foglio e la mappa va sul pericolo, con la sua card
                    onSelect = { hazard ->
                        closeNearMe()
                        selectedPoiId = null
                        hazardViewModel.select(hazard)
                        scope.launch {
                            mapState.animateCamera(
                                CameraUpdate(target = hazard.location.toGeoPoint().toPosition(), zoom = max(mapState.cameraPosition.zoom, HazardZoom)),
                            )
                        }
                    },
                    onRetry = { withLocation(LocationGoal.NearbyHazards, hazardViewModel::onNearbyRequested) },
                )
            },
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
                    LocationGoal.NearMe -> openNearMe()
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

// "Vicino a te" come la barra di ricerca di Apple Maps: una capsula larga in basso con il simbolo del radar e una riga
// che dice cosa trova. Isola come gli altri controlli
// [onReport]: "Segnala un pericolo" in fondo alla barra, un tondo verde pieno (l'unica azione piena della mappa, oltre
// all'SOS); null per l'ospite. Due bersagli separati: la barra apre il foglio, il tondo il form.
@Composable
private fun NearMeBar(onClick: () -> Unit, onReport: (() -> Unit)?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = mapControlColor(),
        border = mapControlBorder(),
        modifier = modifier.fillMaxWidth().widthIn(max = 420.dp).islandShadow(CircleShape, MapControlElevation),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(start = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.NearMe, colors.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                // una riga sola, come la ricerca di Apple Maps
                Text(
                    stringResource(Res.string.near_me_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (onReport != null) {
                val label = stringResource(Res.string.report_open)
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable(role = Role.Button, onClick = onReport)
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.Hazard, colors.onPrimary) }
            }
        }
    }
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
    // la scheda di un luogo come in Apple Maps (11/2026): bollino della categoria (lo stesso della mappa), nome grande,
    // tipo, la X grigia in alto a destra e l'azione a capsula sotto. Isola con angoli da 28 e ombra tinta
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        modifier = modifier.widthIn(max = 420.dp).islandShadow(MaterialTheme.shapes.large).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // decorativa: il tipo è scritto accanto
                Box(Modifier.size(40.dp).background(poi.type.markerColor(), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(poi.type.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(poi.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(poi.type.label()), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                // la X di iOS: tondo grigio da 30 dentro un bersaglio da 48
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(30.dp).background(colors.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.poi_close), tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (onOpenTrail != null) {
                Spacer(Modifier.size(12.dp))
                TonalPillButton(stringResource(Res.string.poi_open_trail), onClick = onOpenTrail, modifier = Modifier.padding(end = 12.dp))
            }
        }
    }
}

// Foglio "Vicino a te": il titolo, il selettore Sentieri | Pericoli (solo da loggati: i pericoli richiedono il login) e
// sotto la scheda scelta. Un foglio solo al posto di due capsule fisse sopra la mappa
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearMeSheet(
    tab: NearTab,
    onTabChange: (NearTab) -> Unit,
    showHazards: Boolean,
    onDismiss: () -> Unit,
    trails: @Composable () -> Unit,
    hazards: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(Res.string.near_me_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
            )
            if (showHazards) {
                // lo stesso selettore a capsula della scheda Rifugi e di Gestione (redesign 11/2026)
                SegmentedControl(
                    options = NearTab.entries.map { stringResource(it.label) },
                    selected = NearTab.entries.indexOf(tab),
                    onSelect = { onTabChange(NearTab.entries[it]) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                Spacer(Modifier.size(12.dp))
            }
            if (showHazards && tab == NearTab.Hazards) hazards() else trails()
        }
    }
}

// Scheda "Sentieri" di "Vicino a te": mentre cerca la posizione e carica, poi la lista ordinata per distanza (dal server)
@Composable
private fun NearbyTrailsList(nearby: NearbyTrails, onOpenTrail: (String) -> Unit, onRetry: () -> Unit) {
    Column {
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

// Riga di un sentiero: nome, difficoltà e lunghezza, stato con il suo simbolo (non solo il colore)
@Composable
internal fun TrailRow(trail: TrailSummaryResponse, onClick: () -> Unit) {
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
