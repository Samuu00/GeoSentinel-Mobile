// Schermata principale della mappa. Mette insieme la mappa MapLibre con i livelli di sentieri, POI, rifugi, pericoli e
// allerte, i controlli sopra la mappa, la ricerca, il foglio "Vicino a te", le card degli elementi toccati e il percorso
// da A a B. Gestisce anche la posizione dell'utente e la richiesta del permesso solo quando serve.
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
import it.unical.geosentinel_mobile.core.location.toBoundingBox
import org.maplibre.compose.util.DpPadding
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.distanceTo
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.SegmentedControl
import it.unical.geosentinel_mobile.core.ui.TonalPillButton
import geosentinel_mobile.shared.generated.resources.route_directions
import geosentinel_mobile.shared.generated.resources.route_dropped_pin
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.interaction.ClickResult
import androidx.compose.runtime.rememberUpdatedState
import it.unical.geosentinel_mobile.core.location.toPositions
import it.unical.geosentinel_mobile.core.ui.islandShadow
import androidx.compose.foundation.shape.RoundedCornerShape
import it.unical.geosentinel_mobile.core.ui.theme.StatusBarOverDark
import it.unical.geosentinel_mobile.core.ui.theme.MapMutedDark
import it.unical.geosentinel_mobile.core.ui.theme.MapMutedLight
import it.unical.geosentinel_mobile.core.ui.theme.MapMarkerFillDark
import it.unical.geosentinel_mobile.core.ui.theme.MapMarkerFillLight
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
import it.unical.geosentinel_mobile.core.ui.AppIcons

/**
 * Punto di partenza della mappa quando la posizione non è disponibile: la Sila, dove si trovano i sentieri del parco.
 * Anche la lista dei rifugi parte da qui.
 */
internal val DefaultArea = GeoPoint(lat = 39.33, lon = 16.55)

/** Zoom iniziale della mappa. */
private const val DefaultZoom = 9.5

/** Zoom minimo dopo "La mia posizione". */
private const val MyLocationZoom = 14.0

/** Spostamento dell'utente, in metri, oltre il quale il riquadro dei 50 km si ricentra su di lui. */
private const val ReAnchorMeters = 5_000.0

/** Zoom minimo da cui parte la mappa del form di segnalazione: abbastanza vicino per posizionare il segnaposto. */
private const val ReportZoom = 13.0

/**
 * Carica lo stile di mappa per [type] (in versione scura se [dark]) da composeResources/files/map. Gli stili sono
 * generati dallo script in tools/map-styles e usano solo fonti gratuite e senza chiave: la Stradale è OpenFreeMap
 * ricolorato (chiaro e scuro), la Satellite usa Sentinel-2 di EOX con strade ed etichette sopra. Le attribuzioni sono
 * dichiarate nelle sorgenti di ogni stile. Non si usano mappe raster PNG come OpenTopoMap perché il motore di
 * maplibre-compose su Android non le disegna in modo affidabile.
 *
 * Il file (centinaia di KB) si legge fuori dal main thread. Se manca o non si legge si restituisce una mappa vuota
 * invece di andare in crash.
 */
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

/** Restituisce il tipo di mappa scelto dall'utente, oppure null finché la preferenza salvata non è stata letta. */
@Composable
internal fun rememberSavedMapType(): MapType? = koinInject<MapSettings>().type.collectAsState(initial = null).value

/**
 * Restituisce lo stile della mappa per [mapType] nel tema attuale (chiaro o scuro). Resta vuoto finché il tipo non è
 * noto, così non si carica la stradale per poi sostituirla subito con il satellite. È usato dalla mappa principale e
 * dalle mappe delle altre schermate.
 */
@Composable
internal fun rememberMapStyle(mapType: MapType?): BaseStyle {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Lo stile si legge dal file in pochi millisecondi: fino ad allora la mappa resta vuota.
    val style by produceState<BaseStyle>(BaseStyle.Empty, mapType, dark) {
        value = mapType?.let { loadStyle(it, dark) } ?: BaseStyle.Empty
    }
    return style
}

/** Colore del bordo sotto le linee (chiaro sulle mappe chiare, scuro su quelle scure), che le stacca dalla mappa. */
internal fun trailCasing(mapIsDark: Boolean): Color = if (mapIsDark) Color.Black.copy(alpha = 0.55f) else Color.White

/** Tempo massimo di attesa di una posizione dopo "La mia posizione" o "Vicino a te" (posizione spenta, nessun segnale). */
private val FixWait = 15.seconds

/**
 * Età massima di una posizione per le ricerche "vicine": su Android la prima posizione disponibile è l'ultima nota, che
 * può risalire anche a giorni prima.
 */
private val MaxFixAge = 2.minutes

/** Dialogo della posizione da mostrare: nessuno, la spiegazione prima della richiesta, o il permesso negato. */
private enum class LocationPrompt { None, Rationale, Denied }

/** Azione da eseguire quando arriva il permesso di localizzazione. */
private enum class LocationGoal { Center, NearMe, Nearby, NearbyHazards, Route }

/** Le due schede del foglio "Vicino a te". */
private enum class NearTab(val label: StringResource) { Trails(Res.string.near_me_trails), Hazards(Res.string.near_me_hazards) }

/** Spazio riservato sotto i controlli per il pulsante SOS (72 dp) e il suo margine. */
private val SosClearance = 104.dp

/** Spazio occupato dalla barra di ricerca in alto. */
private val TopBarSpace = 64.dp

/** Spazio occupato dal foglio "Vicino a te" aperto. */
private val PeekSpace = 236.dp

/** Spazio occupato dal foglio "Vicino a te" chiuso (solo la capsula). */
private val PeekClosedSpace = 76.dp

/** Zoom minimo dopo il tocco su un pericolo vicino: il triangolo si distingue in mezzo ai sentieri. */
private const val HazardZoom = 15.0

/**
 * Schermata della mappa principale.
 *
 * @param onOpenTrail apre il dettaglio di un sentiero.
 * @param onReportHazard apre il form di segnalazione dal centro e dallo zoom della mappa; null per l'ospite.
 * @param showHazards mostra i pericoli e "Pericoli vicini", solo per l'utente loggato; al logout spariscono.
 * @param onCreatePoi apre il form di un nuovo POI dal punto inquadrato; non null solo per il gestore del parco.
 * @param onOpenRefuge apre un rifugio; i rifugi compaiono sulla mappa solo se non è null, quindi non per l'ospite
 * (le rotte dei rifugi richiedono il login).
 * @param onOpenAlert apre un'allerta SOS; non null solo per gli operatori del soccorso, che vedono le allerte aperte.
 * @param bottomBar altezza della barra in basso, che galleggia sopra la mappa: la mappa ci passa sotto, i controlli no.
 * @param onOpenProfile apre il Profilo dal tondo dell'utente in alto a destra.
 * @param avatarInitial iniziale del nome dell'utente, se presente.
 * @param avatarMediaId id della foto profilo, se presente.
 * @param pendingRoute destinazione di "Indicazioni" chieste da un'altra schermata (un rifugio o un sentiero, tramite
 * LocalDirections in App): la mappa avvia il percorso e lo segnala con [onPendingRouteTaken].
 */
@Composable
fun MapScreen(
    onOpenTrail: (String) -> Unit,
    onReportHazard: ((center: GeoPoint, zoom: Double) -> Unit)?,
    showHazards: Boolean,
    onCreatePoi: ((center: GeoPoint, zoom: Double) -> Unit)?,
    onOpenRefuge: ((String) -> Unit)?,
    onOpenAlert: ((String) -> Unit)?,
    bottomBar: Dp = 0.dp,
    onOpenProfile: () -> Unit = {},
    avatarInitial: Char? = null,
    avatarMediaId: String? = null,
    pendingRoute: Place? = null,
    onPendingRouteTaken: () -> Unit = {},
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = MaterialTheme.colorScheme
    // Provider di posizione predefinito di MapLibre (LocationManager su Android, CLLocationManager su iOS). Non chiede
    // il permesso da solo: se c'è già il pallino compare subito, altrimenti la mappa funziona lo stesso partendo
    // dall'area predefinita.
    val location = rememberLocationState()
    val mapSettings = koinInject<MapSettings>()
    val mapType = rememberSavedMapType()
    val style = rememberMapStyle(mapType)
    val viewModel = koinViewModel<TrailMapViewModel>()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val hazardViewModel = koinViewModel<HazardMapViewModel>()
    val hazardUi by hazardViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(showHazards) { hazardViewModel.setEnabled(showHazards) }
    // A ogni ritorno sulla mappa (per esempio dopo una segnalazione) le proprie segnalazioni in verifica si rileggono.
    LaunchedEffect(Unit) { hazardViewModel.refreshMine() }
    val refugeViewModel = koinViewModel<RefugeMapViewModel>()
    val refuges by refugeViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(onOpenRefuge != null) { refugeViewModel.setEnabled(onOpenRefuge != null) }
    val alertViewModel = koinViewModel<RescueMapViewModel>()
    val alerts by alertViewModel.state.collectAsStateWithLifecycle()
    val alertsPartial by alertViewModel.partial.collectAsStateWithLifecycle()
    LaunchedEffect(onOpenAlert != null) { alertViewModel.setEnabled(onOpenAlert != null) }
    // Per gli operatori le allerte si rileggono come nella scheda Allerte: subito a ogni ritorno sulla mappa (dalla
    // schermata SOS, da un dettaglio, dal background), poi a intervalli regolari finché la schermata è visibile, e a ogni
    // nuovo SOS ricevuto via push. Il proprio SOS non arriva come push, quindi si rilegge quando cambia il suo stato
    // (lanciato, annullato, chiuso).
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
    var searching by rememberSaveable { mutableStateOf(false) }
    // Foglio "Vicino a te" aperto o chiuso, come l'ha lasciato l'utente (salvato in DataStore).
    val nearMeOpen by mapSettings.nearMeOpen.collectAsState(initial = true)
    val peekSpace = if (nearMeOpen) PeekSpace else PeekClosedSpace
    // POI selezionato. Si usa remember e non rememberSaveable di proposito: tornando sulla mappa da un'altra pagina o
    // scheda la card del POI deve essere chiusa.
    var selectedPoiId by remember { mutableStateOf<String?>(null) }
    // Lo stesso vale per la card del pericolo, la cui selezione vive nel ViewModel: si azzera quando si lascia la mappa.
    DisposableEffect(Unit) { onDispose { hazardViewModel.clearSelection() } }
    // La mappa passa sotto la barra di stato: le icone di sistema sono chiare sul satellite e sulla stradale scura,
    // scure sulla stradale chiara. Lasciando la mappa tornano a seguire il tema.
    val mapIsDark = mapType == MapType.SATELLITE || dark
    StatusBarOverDark(mapIsDark)
    val casing = trailCasing(mapIsDark)
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = DefaultArea.toPosition(), zoom = DefaultZoom),
    ) {
        TrailLines(ui.trails, unknownColor = if (dark) MapMutedDark else MapMutedLight, onTrailClick = onOpenTrail)
        // L'ordine di dichiarazione decide l'ordine di disegno. Il percorso da A a B sta sopra i sentieri e sotto i
        // simboli.
        val plan = ui.route
        (plan?.result as? RouteResult.Found)?.let { found ->
            val positions = remember(found.path) { found.path.toPositions() }
            val destination = plan.destination.point.toPosition()
            if (positions.isNotEmpty()) {
                RouteConnectors(
                    remember(found, destination) { listOf(found.from.toPosition() to positions.first(), positions.last() to destination) },
                    color = colors.tertiary,
                )
            }
            RouteLine(positions, color = colors.tertiary, casing = casing)
            RouteDestination(destination)
        }
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
        // I pericoli stanno sopra sentieri, POI e rifugi: un pericolo non deve restare nascosto.
        HazardMarkers(hazardUi.shown, halo = casing, otherColor = if (dark) MapMutedDark else MapMutedLight) { id ->
            selectedPoiId = null
            hazardViewModel.select(id)
        }
        // Le allerte stanno sopra i pericoli: per l'operatore sono l'informazione più importante della mappa.
        if (onOpenAlert != null) {
            SosAlertMarkers(alerts, light = if (dark) MapMarkerFillDark else MapMarkerFillLight, onAlertClick = onOpenAlert)
        }
        // Il pallino della posizione sta sopra tutto. Come su iPhone: pallino blu con l'anello bianco, cerchio della
        // precisione azzurro tenue e cono della direzione.
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
    // true dopo il tocco su "La mia posizione": la mappa si centra appena arriva una posizione (subito, se c'è già).
    var centerOnFix by remember { mutableStateOf(false) }

    // Centro della mappa quando è ferma, usato da "Vicino a te". Si salva qui perché leggere mapState.cameraPosition
    // durante la composizione la farebbe ripetere a ogni fotogramma del trascinamento, rallentando la mappa.
    var idleCenter by remember { mutableStateOf(DefaultArea) }
    // Centro del riquadro dei 50 km: la posizione dell'utente, solo se il permesso è già stato dato (qui non si
    // chiede). Si sposta solo dopo ReAnchorMeters, altrimenti a ogni nuova posizione i ViewModel ricaricherebbero.
    var anchor by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(location.lastLocation, location.permission) {
        if (location.permission !is LocationPermission.Granted) return@LaunchedEffect
        val fix = location.lastLocation?.position?.toGeoPoint() ?: return@LaunchedEffect
        if (anchor?.let { it.distanceTo(fix) < ReAnchorMeters } != true) anchor = fix
    }
    // A mappa ferma (o quando si sposta il centro dei 50 km) si comunica la vista a tutti i ViewModel, che aspettano un
    // breve debounce e saltano le aree già caricate. Sentieri, POI, rifugi e pericoli si limitano ai 50 km dall'utente
    // (o dal centro della mappa senza posizione); le allerte no, perché l'operatore deve vederle in tutto il parco.
    LaunchedEffect(mapState) {
        snapshotFlow { (if (mapState.isCameraMoving) null else mapState.viewport)?.let { it to anchor } }.filterNotNull()
            .collect { (viewport, near) ->
                val bounds = viewport.visibleBounds
                val view = GeoBounds.of(bounds.west, bounds.south, bounds.east, bounds.north)
                val zoom = viewport.cameraPosition.zoom
                idleCenter = viewport.cameraPosition.target.toGeoPoint()
                val limit = GeoBounds.around(near ?: idleCenter, TrailMapViewModel.NearRadiusMeters)
                viewModel.onCameraIdle(view, zoom, limit)
                hazardViewModel.onCameraIdle(view, zoom, limit)
                refugeViewModel.onCameraIdle(view, zoom, limit)
                alertViewModel.onCameraIdle(view, zoom)
            }
    }

    LaunchedEffect(centerOnFix, location.lastLocation) {
        val fix = location.lastLocation ?: return@LaunchedEffect
        if (!centerOnFix) return@LaunchedEffect
        // L'animazione parte nello scope della schermata: centerOnFix = false cambia la chiave di questo effetto, che
        // altrimenti la annullerebbe.
        scope.launch {
            mapState.animateCamera(CameraUpdate(target = fix.position, zoom = max(mapState.cameraPosition.zoom, MyLocationZoom)))
        }
        centerOnFix = false
    }
    // Si smette di aspettare la posizione dopo FixWait (anche se il permesso è stato negato nel dialogo di sistema) o
    // appena l'utente sposta la mappa da sé: la camera non deve saltare altrove dopo.
    LaunchedEffect(centerOnFix) {
        if (!centerOnFix) return@LaunchedEffect
        delay(FixWait)
        centerOnFix = false
    }
    LaunchedEffect(mapState.isCameraMoving, mapState.cameraMoveReason) {
        if (mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.GESTURE) centerOnFix = false
    }

    // "Sentieri vicini" richiede una posizione recente, non l'ultima posizione nota.
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
    // "Pericoli vicini" usa la stessa attesa.
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

    // Percorso da A a B: la partenza è la posizione dell'utente oppure il centro della mappa.
    val route = ui.route
    val waitingForRouteOrigin = route?.result == RouteResult.WaitingForOrigin
    LaunchedEffect(waitingForRouteOrigin, route?.origin, location.lastLocation) {
        if (!waitingForRouteOrigin) return@LaunchedEffect
        when (route?.origin) {
            RouteOrigin.MapCenter -> viewModel.onRouteOrigin(mapState.cameraPosition.target.toGeoPoint())
            // Qui basta l'ultima posizione nota: aspettarne una nuova rallenterebbe il calcolo del percorso.
            else -> location.lastLocation?.position?.toGeoPoint()?.let(viewModel::onRouteOrigin)
        }
    }
    // L'attesa della posizione parte solo quando il permesso è concesso: altrimenti scadrebbe mentre l'utente legge i
    // dialoghi, mostrando "posizione non disponibile".
    LaunchedEffect(waitingForRouteOrigin, route?.origin, location.permission) {
        if (!waitingForRouteOrigin || route?.origin != RouteOrigin.MyLocation) return@LaunchedEffect
        if (location.permission !is LocationPermission.Granted) return@LaunchedEffect
        delay(FixWait)
        viewModel.onRouteNoFix()
    }
    // Percorso trovato: la mappa lo inquadra tutto, lasciando spazio alla ricerca in alto e al pannello in basso.
    val foundPath = (route?.result as? RouteResult.Found)?.path
    LaunchedEffect(foundPath) {
        val bounds = foundPath?.let { GeoBounds.around(it.toPositions()) } ?: return@LaunchedEffect
        mapState.fitCameraToBounds(bounds.toBoundingBox(), fitPadding = DpPadding(left = 48.dp, top = 120.dp, right = 48.dp, bottom = bottomBar + 200.dp))
    }

    // Scheda scelta nel foglio "Vicino a te"; resta l'ultima scelta.
    var nearTab by rememberSaveable { mutableStateOf(NearTab.Trails) }

    // Apre "Vicino a te": le ricerche di sentieri e pericoli partono insieme, così cambiare scheda è immediato.
    fun openNearMe() {
        viewModel.onNearbyRequested()
        if (showHazards) hazardViewModel.onNearbyRequested()
    }
    // Chiude il foglio "Vicino a te" in entrambi i ViewModel.
    fun closeNearMe() {
        viewModel.closeNearby()
        hazardViewModel.closeNearby()
    }

    // Esegue [action] se il permesso di localizzazione c'è già; altrimenti ricorda l'obiettivo [target] e mostra prima un
    // dialogo che spiega perché serve, così il permesso si chiede solo quando l'utente ne ha bisogno.
    fun withLocation(target: LocationGoal, action: () -> Unit) {
        goal = target
        prompt = when (val permission = location.permission) {
            is LocationPermission.Granted -> {
                action()
                LocationPrompt.None
            }
            // Con canRequest false il permesso è negato in modo definitivo: il sistema non mostra più il dialogo.
            is LocationPermission.NotGranted if permission.canRequest == false -> LocationPrompt.Denied
            else -> LocationPrompt.Rationale
        }
    }

    // Avvia il percorso verso [place], chiudendo le card aperte.
    fun directionsTo(place: Place) {
        selectedPoiId = null
        hazardViewModel.clearSelection()
        viewModel.routeTo(place)
        // Senza il permesso si spiega e lo si chiede; nel frattempo si può scegliere come partenza il centro della mappa.
        withLocation(LocationGoal.Route) {}
    }
    LaunchedEffect(pendingRoute) {
        val place = pendingRoute ?: return@LaunchedEffect
        onPendingRouteTaken()
        directionsTo(place)
    }

    Box(Modifier.fillMaxSize()) {
        val pinLabel = stringResource(Res.string.route_dropped_pin)
        val onLongPress by rememberUpdatedState { at: GeoPoint -> directionsTo(Place(pinLabel, at)) }
        // Una pressione prolungata su un punto qualsiasi calcola il percorso fin lì, come in Google Maps.
        val interactions = remember {
            MapInteractions {
                callbacks {
                    longClick {
                        onEvent { event ->
                            event.position?.let { onLongPress(it.toGeoPoint()) }
                            ClickResult.Consume
                        }
                    }
                }
            }
        }
        MaplibreMap(
            state = mapState,
            interactions = interactions,
            // La mappa si estende sotto la barra di stato e sotto la barra in basso; i margini della viewport fanno sì
            // che scala, bussola e attribuzione restino visibili sopra la barra e sopra il pulsante SOS.
            viewportInsets = PaddingValues(
                // Sotto la barra di ricerca in alto e sopra il foglio "Vicino a te" in basso.
                top = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding() + TopBarSpace,
                bottom = bottomBar + peekSpace,
            ),
            // Riproduce l'overlay predefinito di MapLibre, con lo stesso posizionamento ma senza il logo, che la licenza
            // BSD non richiede. L'attribuzione (la "i") invece resta, perché OpenStreetMap (ODbL) e OpenFreeMap la
            // chiedono visibile sulla mappa.
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
                    // Bussola a sinistra sotto la scala, perché in alto a destra ci sono i comandi della vista.
                    DisappearingCompassButton(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp))
                    // Attribuzione in basso a sinistra, lontana dall'SOS, nei colori del tema per essere leggibile su
                    // ogni mappa.
                    AttributionButton(mapState, Modifier.align(Alignment.BottomStart))
                }
            },
        )
        // In alto al centro, sotto la barra di ricerca, una capsula con lo stato del caricamento (in corso, errore, zoom
        // troppo basso). I margini laterali evitano di coprire scala e bussola.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = TopBarSpace + 12.dp, start = 72.dp, end = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Si mostra un avviso alla volta, in ordine di priorità: per primo quello dei sentieri, che dice già se
            // manca la rete.
            val hazardsFailed = showHazards && hazardUi.failed && !ui.zoomTooLow
            val status = when {
                ui.failed != null -> stringResource(Res.string.trails_load_failed)
                ui.zoomTooLow -> stringResource(Res.string.trails_zoom_in_hint)
                hazardsFailed -> stringResource(Res.string.hazards_load_failed)
                // L'operatore deve sapere che sulla mappa non ci sono tutte le allerte (nella scheda Allerte sì).
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
        // Comandi della vista in un unico gruppo in alto a destra, come in Apple Maps: tipo di mappa, posizione, segnala
        // un pericolo (da loggati) e nuovo punto (solo per il gestore). Non ci sono pulsanti di zoom: si zooma con il
        // pizzico o con il doppio tocco, e la mappa resta libera. In orizzontale (o con il testo grande) il gruppo scorre
        // invece di finire sotto il pulsante SOS.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Top))
                .padding(end = 16.dp, top = TopBarSpace + 8.dp, bottom = bottomBar + peekSpace)
                .verticalScroll(rememberScrollState())
                .padding(start = 8.dp, bottom = 8.dp),
        ) {
            MapButtonGroup(
                listOfNotNull(
                    MapAction(Res.string.map_type_button, Glyph.Layers) { choosingType = true },
                    MapAction(Res.string.map_my_location, Glyph.Locate) { withLocation(LocationGoal.Center) { centerOnFix = true } },
                    // Segnala un pericolo nel punto inquadrato (solo da loggati).
                    onReportHazard?.let { report ->
                        MapAction(Res.string.report_open, Glyph.Hazard) {
                            report(mapState.cameraPosition.target.toGeoPoint(), max(mapState.cameraPosition.zoom, ReportZoom))
                        }
                    },
                    // Nuovo POI, solo per il gestore del parco: il form parte dal punto inquadrato.
                    onCreatePoi?.let { create ->
                        MapAction(Res.string.poi_create_title, Glyph.AddPoint) {
                            create(mapState.cameraPosition.target.toGeoPoint(), max(mapState.cameraPosition.zoom, ReportZoom))
                        }
                    },
                ),
            )
        }
        // In alto la capsula della ricerca e il tondo del Profilo.
        MapTopBar(
            onSearch = { searching = true },
            onProfile = onOpenProfile,
            initial = avatarInitial,
            avatarMediaId = avatarMediaId,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // In basso, uno solo tra: il pannello del percorso, se è attivo, oppure il foglio "Vicino a te" con i sentieri
        // dell'area. Entrambi si nascondono quando c'è la card di un elemento toccato, che occupa lo stesso spazio.
        if (route != null && selectedPoiId == null && hazardUi.selected == null) {
            RoutePanel(
                plan = route,
                onOrigin = viewModel::setRouteOrigin,
                onClose = viewModel::closeRoute,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomBar + 8.dp),
            )
        } else if (route == null && selectedPoiId == null && hazardUi.selected == null) {
            NearMePeek(
                trails = ui.trails,
                center = idleCenter,
                zoomTooLow = ui.zoomTooLow,
                expanded = nearMeOpen,
                onToggle = { scope.launch { mapSettings.setNearMeOpen(!nearMeOpen) } },
                onOpenTrail = onOpenTrail,
                onOpenNearMe = { withLocation(LocationGoal.NearMe, ::openNearMe) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomBar + 8.dp)
                    .widthIn(max = 560.dp),
            )
        }
        // POI toccato: card in basso a sinistra (a destra c'è il pulsante SOS).
        ui.pois.firstOrNull { it.id == selectedPoiId }?.let { poi ->
            PoiCard(
                poi = poi,
                onDirections = { directionsTo(Place(poi.name, poi.location.toGeoPoint())) },
                onClose = { selectedPoiId = null },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    .padding(start = 16.dp, end = 136.dp, bottom = bottomBar + 16.dp),
            )
        }
        // Pericolo toccato (o scelto da "Pericoli vicini"): stessa posizione della card dei POI, che non compaiono mai
        // insieme.
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

    if (searching) {
        MapSearchSheet(
            trails = ui.trails,
            refuges = if (onOpenRefuge != null) refuges else emptyList(),
            pois = ui.pois,
            onPick = { hit ->
                searching = false
                when (hit) {
                    is MapSearchHit.Trail -> onOpenTrail(hit.trail.id)
                    is MapSearchHit.Refuge -> onOpenRefuge?.invoke(hit.refuge.id)
                    is MapSearchHit.Poi -> {
                        hazardViewModel.clearSelection()
                        selectedPoiId = hit.poi.id
                        scope.launch {
                            mapState.animateCamera(CameraUpdate(target = hit.poi.location.toGeoPoint().toPosition(), zoom = max(mapState.cameraPosition.zoom, HazardZoom)))
                        }
                    }
                }
            },
            onDismiss = { searching = false },
        )
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
                    // Si chiude il foglio e la mappa si sposta sul pericolo, mostrandone la card.
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
                // Si prepara l'azione e poi si chiede il permesso: se viene negato nel dialogo di sistema, l'attesa
                // della posizione finisce comunque dopo FixWait.
                when (goal) {
                    LocationGoal.Center -> centerOnFix = true
                    LocationGoal.NearMe -> openNearMe()
                    LocationGoal.Nearby -> viewModel.onNearbyRequested()
                    LocationGoal.NearbyHazards -> hazardViewModel.onNearbyRequested()
                    // Fa ripartire l'attesa della posizione, che nel frattempo potrebbe essere scaduta.
                    LocationGoal.Route -> viewModel.setRouteOrigin(RouteOrigin.MyLocation)
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

/**
 * Restituisce l'ultima posizione solo se è recente (non più vecchia di MaxFixAge), altrimenti null. È usata anche dal
 * form della segnalazione.
 */
internal fun LocationState.recentFix(): GeoPoint? {
    val fix = lastLocation ?: return null
    val age = lastLocationMeasurementMark?.elapsedNow() ?: return null
    return if (age <= MaxFixAge) fix.position.toGeoPoint() else null
}


/**
 * Capsula sopra la mappa nei colori delle card: un'azione ([emphasized], nel verde del tema) oppure un avviso. Se
 * [onClick] non è null la capsula è toccabile (per esempio per riprovare dopo un errore).
 */
@Composable
private fun MapPill(text: String, emphasized: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val container = if (emphasized) colors.primary else colors.surfaceContainerLowest
    val content = if (emphasized) colors.onPrimary else colors.onSurface
    val border = if (emphasized) null else BorderStroke(1.dp, colors.outlineVariant)
    // Gli avvisi (caricamento, errore) sono live region e vengono annunciati da soli; le azioni no.
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

/**
 * Card di un POI toccato, come la scheda di un luogo in Apple Maps: bollino della categoria (lo stesso della mappa),
 * nome grande, tipo, la X grigia in alto a destra e sotto l'azione "Indicazioni" a capsula. È un'isola con angoli
 * arrotondati e ombra.
 */
@Composable
private fun PoiCard(poi: PoiResponse, onDirections: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        modifier = modifier.widthIn(max = 420.dp).islandShadow(MaterialTheme.shapes.large).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icona decorativa: il tipo è scritto accanto.
                Box(Modifier.size(40.dp).background(poi.type.markerColor(), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(poi.type.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(poi.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(poi.type.label()), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                // Pulsante di chiusura nello stile di iOS: cerchio grigio da 30 dp dentro un'area toccabile da 48 dp.
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(30.dp).background(colors.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Close, contentDescription = stringResource(Res.string.poi_close), tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            // C'è solo "Indicazioni": il sentiero del POI si apre toccandolo direttamente sulla mappa.
            TonalPillButton(stringResource(Res.string.route_directions), onClick = onDirections)
        }
    }
}

/**
 * Foglio completo "Vicino a te": il titolo, il selettore Sentieri | Pericoli (solo da loggati, perché i pericoli
 * richiedono il login) e sotto il contenuto della scheda scelta, fornito da [trails] o [hazards].
 */
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
                // Lo stesso selettore a capsula usato nelle schede Rifugi e Gestione.
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

/**
 * Scheda "Sentieri" di "Vicino a te": un indicatore mentre attende la posizione o carica, un errore con "Riprova", poi
 * la lista dei sentieri ordinata per distanza dal server.
 */
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

/** Riga di un sentiero: nome, difficoltà e lunghezza, e lo stato con il suo simbolo (non solo con il colore). */
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

/** Dialogo legato alla posizione (spiegazione prima della richiesta o permesso negato): [onConfirm] esegue l'azione. */
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
