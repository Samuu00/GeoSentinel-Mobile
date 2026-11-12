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
import androidx.compose.ui.geometry.Rect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import it.unical.geosentinel_mobile.core.ui.islandShadow
import androidx.compose.foundation.shape.RoundedCornerShape
import it.unical.geosentinel_mobile.core.ui.theme.LocalStatusBarOverDark
import it.unical.geosentinel_mobile.trail.data.MapSettings
import it.unical.geosentinel_mobile.trail.data.MapType
import kotlin.math.max
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
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

// Controlli disegnati sopra la mappa (attribuzione, pulsanti tondi, tipo di mappa): staccati da MapScreen.kt

// ---- Controlli della mappa ----

// Attribuzione delle mappe (obbligatoria: OpenStreetMap ODbL, OpenFreeMap, EOX CC BY), nei colori del tema. Come
// chiedono le linee guida di OSM parte aperta e si chiude al primo gesto o dopo qualche secondo; poi resta la "i" per
// riaprirla. Quella di MapLibre si chiude solo al primo gesto: sull'anteprima ferma del dettaglio restava aperta.
@Composable
// [startExpanded] false sulle mappe ferme (testata del rifugio): la "i" basta, il pannello aperto copriva la foto
internal fun AttributionButton(mapState: MapState, modifier: Modifier, alignEnd: Boolean = false, startExpanded: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    LaunchedEffect(mapState) {
        snapshotFlow { mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.GESTURE }.first { it }
        expanded = false
    }
    val attributions = mapState.style.attributions()
    // i secondi contano da quando c'è qualcosa da leggere: finché lo stile non è caricato le attribuzioni sono vuote
    val shown = attributions.isNotEmpty()
    LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        delay(AttributionOpenFor)
        expanded = false
    }
    if (!shown) return
    val toggle: @Composable () -> Unit = {
        // bersaglio da 48 dp, disegno da 28: si nota senza ingombrare
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { expanded = !expanded },
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
    }
    val panel: @Composable () -> Unit = {
        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerLowest.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, colors.outlineVariant),
                // un trascinamento sul pannello non sposta la mappa sotto
                modifier = Modifier.pointerInput(Unit) {},
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    AttributionDefaults.content(attributions, MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant))
                }
            }
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (alignEnd) {
            Box(Modifier.weight(1f, fill = false)) { panel() }
            toggle()
        } else {
            toggle()
            Box(Modifier.weight(1f, fill = false)) { panel() }
        }
    }
}

// Quanto resta aperta l'attribuzione se nessuno tocca la mappa
private val AttributionOpenFor = 5.seconds

internal enum class Glyph { Layers, Locate, Hazard, AddPoint, NearMe }

// Controlli sulla mappa: un livello che galleggia sopra il contenuto (§2). Tono dei menu, anello chiaro e ombra: si
// staccano anche dal bosco scuro della mappa notturna, dove il tono delle card spariva
@Composable
internal fun mapControlColor() = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
internal fun mapControlBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

internal val MapControlElevation = 6.dp

/** Un comando di [MapButtonGroup]. */
internal class MapAction(val description: StringResource, val glyph: Glyph, val onClick: () -> Unit)

/**
 * Comandi della vista raccolti in una capsula sola, separati da un filo (come i controlli di Apple Maps in alto a
 * destra): un oggetto solo per l'occhio invece di tanti tondi.
 */
@Composable
internal fun MapButtonGroup(actions: List<MapAction>) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = mapControlColor(),
        border = mapControlBorder(),
        modifier = Modifier.width(52.dp).islandShadow(RoundedCornerShape(26.dp), MapControlElevation),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            actions.forEachIndexed { index, action ->
                if (index > 0) HorizontalDivider(Modifier.width(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val label = stringResource(action.description)
                Box(
                    Modifier.size(52.dp).clickable(role = Role.Button, onClick = action.onClick).semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(action.glyph, MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

// Simboli disegnati con lo stesso tratto: material-icons-core non ha né il "meno" né il mirino della posizione
@Composable
internal fun GlyphIcon(glyph: Glyph, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val c = center
        when (glyph) {
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
            Glyph.Hazard -> {
                // triangolo di pericolo con il punto esclamativo, come sui cartelli
                val half = size.width * 0.42f
                val top = c.y - size.height * 0.38f
                val bottom = c.y + size.height * 0.34f
                val triangle = Path().apply {
                    moveTo(c.x, top)
                    lineTo(c.x + half, bottom)
                    lineTo(c.x - half, bottom)
                    close()
                }
                drawPath(triangle, tint, style = Stroke(stroke, join = StrokeJoin.Round))
                drawLine(tint, c.copy(y = c.y - size.height * 0.12f), c.copy(y = c.y + size.height * 0.10f), stroke, StrokeCap.Round)
                drawCircle(tint, stroke * 0.65f, c.copy(y = c.y + size.height * 0.22f))
            }
            Glyph.AddPoint -> {
                // segnaposto con il "+" dentro: un punto nuovo sulla mappa (M8.1)
                val r = size.minDimension * 0.30f
                val head = c.copy(y = c.y - size.height * 0.10f)
                val tip = c.copy(y = c.y + size.height * 0.46f)
                val pin = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(head.x - r * 0.87f, head.y + r * 0.5f)
                    arcTo(Rect(head, r), 150f, 240f, false)
                    close()
                }
                drawPath(pin, tint, style = Stroke(stroke, join = StrokeJoin.Round))
                val plus = r * 0.5f
                drawLine(tint, head.copy(x = head.x - plus), head.copy(x = head.x + plus), stroke, StrokeCap.Round)
                drawLine(tint, head.copy(y = head.y - plus), head.copy(y = head.y + plus), stroke, StrokeCap.Round)
            }
            Glyph.NearMe -> {
                // radar: il punto (tu) e due onde intorno, la più lontana più tenue
                drawCircle(tint, size.minDimension * 0.09f, c)
                drawCircle(tint, size.minDimension * 0.24f, c, style = Stroke(stroke))
                drawCircle(tint.copy(alpha = 0.5f), size.minDimension * 0.42f, c, style = Stroke(stroke))
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
internal fun MapTypeSheet(current: MapType, onSelect: (MapType) -> Unit, onDismiss: () -> Unit) {
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
                // i colori di build_map_styles.py (LIGHT)
                drawRect(Color(0xFFF6F3EC))
                drawOval(Color(0xFFC8E0B2), topLeft = Offset(w * 0.05f, h * 0.08f), size = Size(w * 0.5f, h * 0.38f))
                drawCircle(Color(0xFFA2D1F2), radius = w * 0.32f, center = Offset(w * 1.02f, h * 1.02f))
                drawLine(Color(0xFFDCD6C9), Offset(0f, h * 0.62f), Offset(w, h * 0.5f), w * 0.09f)
                drawLine(Color.White, Offset(0f, h * 0.62f), Offset(w, h * 0.5f), w * 0.06f)
                drawLine(Color(0xFFE2B250), Offset(w * 0.62f, 0f), Offset(w * 0.38f, h), w * 0.1f)
                drawLine(Color(0xFFF8D37A), Offset(w * 0.62f, 0f), Offset(w * 0.38f, h), w * 0.07f)
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
