package it.unical.geosentinel_mobile.trail.ui

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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.location_denied_body
import geosentinel_mobile.shared.generated.resources.location_denied_title
import geosentinel_mobile.shared.generated.resources.location_open_settings
import geosentinel_mobile.shared.generated.resources.location_rationale_allow
import geosentinel_mobile.shared.generated.resources.location_rationale_body
import geosentinel_mobile.shared.generated.resources.location_rationale_title
import geosentinel_mobile.shared.generated.resources.map_my_location
import geosentinel_mobile.shared.generated.resources.map_type_button
import geosentinel_mobile.shared.generated.resources.map_type_satellite
import geosentinel_mobile.shared.generated.resources.map_type_streets
import geosentinel_mobile.shared.generated.resources.map_type_title
import geosentinel_mobile.shared.generated.resources.map_zoom_in
import geosentinel_mobile.shared.generated.resources.map_zoom_out
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.ui.theme.LocalStatusBarOverDark
import it.unical.geosentinel_mobile.trail.data.MapSettings
import it.unical.geosentinel_mobile.trail.data.MapType
import kotlin.math.max
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.location.rememberSystemSettingsLauncher
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.AttributionStyle
import org.maplibre.compose.overlay.DisappearingCompassButton
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.overlay.LocalViewportInsets
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.style.BaseStyle

// Senza posizione la mappa parte dalla Sila, dove stanno i sentieri del seed
private val DefaultArea = GeoPoint(lat = 39.33, lon = 16.55)
private const val DefaultZoom = 9.5
private const val MyLocationZoom = 14.0

// Stili della mappa in composeResources/files/map, generati da tools/map-styles (tutti gratuiti e senza chiave):
// Stradale = OpenFreeMap ricolorato con una palette tipo Google Maps (chiaro e scuro), Satellite = Sentinel-2 di EOX
// con strade ed etichette sopra. Le attribuzioni stanno nelle sorgenti di ogni stile.
// (Niente mappe raster PNG come OpenTopoMap: il motore di maplibre-compose 0.19 su Android non le disegna in modo
// affidabile; la Topografica è stata tolta il 03/10.)
private suspend fun loadStyle(type: MapType, dark: Boolean): BaseStyle {
    val file = when (type) {
        MapType.STREETS -> if (dark) "streets_dark" else "streets_light"
        MapType.SATELLITE -> "satellite"
    }
    return BaseStyle.Json(Res.readBytes("files/map/$file.json").decodeToString())
}

// Cosa mostra il dialogo della posizione
private enum class LocationPrompt { None, Rationale, Denied }

@Composable
fun MapScreen() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Provider di default di MapLibre (LocationManager / CLLocationManager). Non chiede il permesso da solo:
    // se c'è già, il pallino compare subito; se non c'è, la mappa funziona lo stesso dall'area di default.
    val location = rememberLocationState()
    val mapSettings = koinInject<MapSettings>()
    val mapType by mapSettings.type.collectAsState(initial = MapType.STREETS)
    // lo stile si legge dal file (pochi ms): fino ad allora la mappa è vuota
    val style by produceState<BaseStyle>(BaseStyle.Empty, mapType, dark) { value = loadStyle(mapType, dark) }
    var choosingType by rememberSaveable { mutableStateOf(false) }
    // La mappa sta sotto la barra di stato: icone chiare su satellite e stradale scura, scure sulla stradale chiara.
    // Lasciando la mappa tornano a seguire il tema.
    val statusBarOverDark = LocalStatusBarOverDark.current
    val mapIsDark = mapType == MapType.SATELLITE || dark
    DisposableEffect(mapIsDark) {
        statusBarOverDark.value = mapIsDark
        onDispose { statusBarOverDark.value = null }
    }
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = DefaultArea.toPosition(), zoom = DefaultZoom),
    ) {
        LocationIndicatorLayer(id = "posizione", locationState = location)
    }
    val settings = rememberSystemSettingsLauncher()
    val scope = rememberCoroutineScope()
    var prompt by rememberSaveable { mutableStateOf(LocationPrompt.None) }
    // "La mia posizione" toccato: si centra appena arriva un fix (subito, se c'è già)
    var centerOnFix by remember { mutableStateOf(false) }

    LaunchedEffect(centerOnFix, location.lastLocation) {
        val fix = location.lastLocation ?: return@LaunchedEffect
        if (!centerOnFix) return@LaunchedEffect
        centerOnFix = false
        mapState.animateCamera(CameraUpdate(target = fix.position, zoom = max(mapState.cameraPosition.zoom, MyLocationZoom)))
    }
    // Permesso negato nel dialogo di sistema: non si aspetta più un fix
    LaunchedEffect(location.permission) {
        if (location.permission is LocationPermission.NotGranted) centerOnFix = false
    }

    fun onMyLocation() {
        prompt = when (val permission = location.permission) {
            is LocationPermission.Granted -> {
                centerOnFix = true
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
                    AttributionButton(Modifier.align(Alignment.BottomStart))
                }
            },
        )
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
            MapButton(Res.string.map_my_location, Glyph.Locate, onClick = ::onMyLocation)
        }
    }

    if (choosingType) {
        MapTypeSheet(
            current = mapType,
            onSelect = { type ->
                choosingType = false
                scope.launch { mapSettings.setType(type) }
            },
            onDismiss = { choosingType = false },
        )
    }

    when (prompt) {
        // Prima del dialogo di sistema si spiega perché: il permesso si chiede solo quando serve (§M3.1)
        LocationPrompt.Rationale -> LocationDialog(
            title = Res.string.location_rationale_title,
            body = Res.string.location_rationale_body,
            confirm = Res.string.location_rationale_allow,
            onConfirm = {
                centerOnFix = true
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

// ---- Controlli della mappa ----

// Attribuzione di OpenStreetMap e OpenFreeMap (obbligatoria): una "i" piccola su un tondo da card, che si apre in
// un pannello con i crediti. Il default di MapLibre è nero fisso, quasi invisibile sulla mappa scura.
@Composable
private fun AttributionButton(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val panel = AttributionStyle(
        containerColor = colors.surfaceContainerLowest.copy(alpha = 0.94f),
        contentColor = colors.onSurfaceVariant,
        textStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
        border = BorderStroke(1.dp, colors.outlineVariant),
    )
    ExpandingAttributionButton(
        modifier = modifier,
        contentAlignment = Alignment.BottomStart,
        toggleButton = { onClick ->
            // bersaglio da 48 dp, disegno da 28: si nota senza ingombrare
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(colors.surfaceContainerLowest.copy(alpha = 0.94f), CircleShape)
                        .border(1.dp, colors.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AttributionDefaults.icon(),
                        contentDescription = AttributionDefaults.contentDescription(),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        expandedStyle = panel,
        // chiuso: niente pannello, resta solo il tondo
        collapsedStyle = panel.copy(containerColor = Color.Transparent, border = null),
    )
}

private enum class Glyph { Layers, Plus, Minus, Locate }

// Tondo da 56 dp come le card (tono e filo di bordo): si legge sopra qualsiasi zona della mappa
@Composable
private fun MapButton(description: StringResource, glyph: Glyph, onClick: () -> Unit) {
    val label = stringResource(description)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier.size(56.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { GlyphIcon(glyph, MaterialTheme.colorScheme.onSurface) }
    }
}

// Simboli disegnati con lo stesso tratto: material-icons-core non ha né il "meno" né il mirino della posizione
@Composable
private fun GlyphIcon(glyph: Glyph, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val c = center
        val arm = size.minDimension * 0.36f
        when (glyph) {
            Glyph.Plus -> {
                drawLine(tint, c.copy(x = c.x - arm), c.copy(x = c.x + arm), stroke, StrokeCap.Round)
                drawLine(tint, c.copy(y = c.y - arm), c.copy(y = c.y + arm), stroke, StrokeCap.Round)
            }
            Glyph.Minus -> drawLine(tint, c.copy(x = c.x - arm), c.copy(x = c.x + arm), stroke, StrokeCap.Round)
            Glyph.Layers -> {
                // livelli: un rombo pieno di contorno e, sotto, il bordo di quello successivo
                val w = size.width * 0.42f
                val h = size.height * 0.22f
                val top = c.y - h * 0.8f
                val sheet = Path().apply {
                    moveTo(c.x, top - h)
                    lineTo(c.x + w, top)
                    lineTo(c.x, top + h)
                    lineTo(c.x - w, top)
                    close()
                }
                drawPath(sheet, tint, style = Stroke(stroke, join = StrokeJoin.Round))
                val below = Path().apply {
                    moveTo(c.x - w, top + h * 1.6f)
                    lineTo(c.x, top + h * 2.6f)
                    lineTo(c.x + w, top + h * 1.6f)
                }
                drawPath(below, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Glyph.Locate -> {
                // mirino: cerchio, quattro tacche e il punto al centro
                val r = size.minDimension * 0.30f
                drawCircle(tint, r, c, style = Stroke(stroke))
                drawCircle(tint, r * 0.35f, c)
                val outer = size.minDimension * 0.48f
                drawLine(tint, c.copy(y = c.y - outer), c.copy(y = c.y - r), stroke, StrokeCap.Round)
                drawLine(tint, c.copy(y = c.y + r), c.copy(y = c.y + outer), stroke, StrokeCap.Round)
                drawLine(tint, c.copy(x = c.x - outer), c.copy(x = c.x - r), stroke, StrokeCap.Round)
                drawLine(tint, c.copy(x = c.x + r), c.copy(x = c.x + outer), stroke, StrokeCap.Round)
            }
        }
    }
}

// ---- Tipo di mappa ----

// Foglio dal basso con le mappe, come in Google Maps: riquadri d'anteprima con il nome sotto, la scelta evidenziata
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapTypeSheet(current: MapType, onSelect: (MapType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(
                stringResource(Res.string.map_type_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(16.dp))
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapType.entries.forEach { type ->
                    MapTypeTile(type, selected = type == current, onClick = { onSelect(type) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MapTypeTile(type: MapType, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MapPreview(
            type,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(type.label()),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.primary else colors.onSurface,
        )
    }
}

private fun MapType.label(): StringResource = when (this) {
    MapType.STREETS -> Res.string.map_type_streets
    MapType.SATELLITE -> Res.string.map_type_satellite
}

// Anteprime disegnate: riprendono i colori degli stili (sono campioni della mappa, non colori dell'interfaccia,
// per questo non vengono dal tema). Decorative: il nome è scritto sotto.
@Composable
private fun MapPreview(type: MapType, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        when (type) {
            MapType.STREETS -> {
                drawRect(Color(0xFFF2F2EF))
                drawOval(Color(0xFFCDEBC5), topLeft = Offset(w * 0.05f, h * 0.08f), size = Size(w * 0.5f, h * 0.38f))
                drawCircle(Color(0xFFAADAFF), radius = w * 0.32f, center = Offset(w * 1.02f, h * 1.02f))
                drawLine(Color(0xFFD4D4D4), Offset(0f, h * 0.62f), Offset(w, h * 0.5f), w * 0.09f)
                drawLine(Color.White, Offset(0f, h * 0.62f), Offset(w, h * 0.5f), w * 0.06f)
                drawLine(Color(0xFFF2C45A), Offset(w * 0.62f, 0f), Offset(w * 0.38f, h), w * 0.1f)
                drawLine(Color(0xFFFDE293), Offset(w * 0.62f, 0f), Offset(w * 0.38f, h), w * 0.07f)
            }
            MapType.SATELLITE -> {
                drawRect(Color(0xFF3E4A2E))
                drawOval(Color(0xFF2C3A22), topLeft = Offset(-w * 0.1f, h * 0.05f), size = Size(w * 0.7f, h * 0.55f))
                drawOval(Color(0xFF6B6A4C), topLeft = Offset(w * 0.45f, h * 0.45f), size = Size(w * 0.65f, h * 0.5f))
                drawOval(Color(0xFF526140), topLeft = Offset(w * 0.5f, -h * 0.1f), size = Size(w * 0.6f, h * 0.45f))
                drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, h * 0.7f), Offset(w, h * 0.35f), w * 0.035f)
            }
        }
    }
}
