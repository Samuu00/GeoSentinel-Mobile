package it.unical.geosentinel_mobile.trail.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.location.toPositions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.unical.geosentinel_mobile.core.ui.theme.InkDark
import it.unical.geosentinel_mobile.core.ui.theme.InkLight
import it.unical.geosentinel_mobile.core.ui.theme.MarkerDanger
import it.unical.geosentinel_mobile.core.ui.theme.MarkerEquipment
import it.unical.geosentinel_mobile.core.ui.theme.MarkerInfo
import it.unical.geosentinel_mobile.core.ui.theme.MarkerOther
import it.unical.geosentinel_mobile.core.ui.theme.MarkerShelter
import it.unical.geosentinel_mobile.core.ui.theme.MarkerViewpoint
import it.unical.geosentinel_mobile.core.ui.theme.MarkerWater
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.value.SymbolAnchor
import it.unical.geosentinel_mobile.core.ui.theme.TrailClosed
import it.unical.geosentinel_mobile.core.ui.theme.TrailOpen
import it.unical.geosentinel_mobile.core.ui.theme.TrailWarning
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.exponential
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.FloatValue
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

// Layer dei sentieri e dei POI sopra la mappa base. I colori dello stato sono fissi (Color.kt) e uguali su tutti gli
// stili; un bordo chiaro sotto ogni linea la stacca anche dal satellite. Un sentiero chiuso è anche tratteggiato:
// si riconosce senza distinguere i colori (§2).

private const val IdProperty = "id"

/** Le linee dei sentieri, una sorgente per stato: in MapLibre il tratteggio non può dipendere da un dato. */
@Composable
@MaplibreComposable
fun TrailLines(trails: List<TrailSummaryResponse>, casing: Color, unknownColor: Color, onTrailClick: (String) -> Unit) {
    val byState = remember(trails) { trails.groupBy { TrailState.of(it.status.value) } }
    val onClick = remember(onTrailClick) { idClickHandler(onTrailClick) }
    TrailState.entries.forEach { state ->
        val features = remember(byState, state) { byState[state].orEmpty().mapNotNull { it.toFeature() } }
        val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
        PathLayers("sentieri-${state.name.lowercase()}", source, state.lineColor(unknownColor), casing, state == TrailState.CLOSED, onClick, TrailMapViewModel.MinTrailZoom)
    }
}

/** Il percorso completo di un solo sentiero (dettaglio). */
@Composable
@MaplibreComposable
fun TrailPath(positions: List<Position>, state: TrailState, casing: Color, unknownColor: Color) {
    if (positions.size < 2) return
    val source = rememberGeoJsonSource(GeoJsonData.Features(LineString(positions)))
    PathLayers("percorso", source, state.lineColor(unknownColor), casing, state == TrailState.CLOSED, onClick = null)
}

private fun TrailState.lineColor(unknown: Color): Color = when (this) {
    TrailState.OPEN -> TrailOpen
    TrailState.WARNING -> TrailWarning
    TrailState.CLOSED -> TrailClosed
    TrailState.UNKNOWN -> unknown
}

// Spessore che cresce con lo zoom: da lontano linee sottili, così i sentieri vicini restano distinti (prima 4 dp sempre)
private val LineWidth = interpolate(exponential(1.4f), zoom(), 7 to const(1.dp), 11 to const(2.dp), 14 to const(3.5.dp), 17 to const(5.dp))
// Il bordo chiaro sotto la linea: sempre un po' più largo, per staccarla dalla mappa
private val CasingWidth = interpolate(exponential(1.4f), zoom(), 7 to const(2.5.dp), 11 to const(3.5.dp), 14 to const(6.dp), 17 to const(8.dp))

/**
 * Comparsa graduale: un livello con `minZoom = from` è spento sotto [from] e da lì diventa pieno in [FadeZoom] livelli.
 * Così sentieri e simboli compaiono e spariscono in dissolvenza mentre si zooma, non di colpo.
 */
internal fun fadeInFrom(from: Double): Expression<FloatValue> = interpolate(linear(), zoom(), from to const(0f), from + FadeZoom to const(1f))
internal const val FadeZoom = 0.6

@Composable
@MaplibreComposable
private fun PathLayers(id: String, source: GeoJsonSource, color: Color, casing: Color, dashed: Boolean, onClick: FeaturesClickHandler?, minZoom: Double? = null) {
    // senza soglia (dettaglio del sentiero) sempre pieno
    val opacity = minZoom?.let(::fadeInFrom) ?: const(1f)
    val from = minZoom?.toFloat() ?: 0f
    LineLayer(
        id = "$id-bordo",
        source = source,
        minZoom = from,
        color = const(casing),
        width = CasingWidth,
        opacity = opacity,
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
    LineLayer(
        id = id,
        source = source,
        minZoom = from,
        color = const(color),
        width = LineWidth,
        opacity = opacity,
        cap = const(if (dashed) LineCap.Butt else LineCap.Round),
        join = const(LineJoin.Round),
        // trattini lunghi due volte lo spessore, spazi di una volta e mezza
        dasharray = if (dashed) const(listOf(2, 1.5)) else null,
        onClick = onClick,
        // la linea è sottile: il dito la prende anche un po' più in là
        hitPadding = 16.dp,
    )
}

/**
 * I POI come le categorie di Apple Maps (11/2026): bollino pieno nel colore del tipo, simbolo bianco, anello bianco e
 * un'ombra leggera; da vicino il nome sotto. Il simbolo dice il tipo anche senza colori. Cliccabili.
 * [label] e [halo]: testo dei nomi e il suo alone (scuro su chiaro di giorno, il contrario di notte).
 */
@Composable
@MaplibreComposable
fun PoiMarkers(pois: List<PoiResponse>, label: Color, halo: Color, onPoiClick: (String) -> Unit) {
    val features = remember(pois) {
        pois.map { poi ->
            Feature(
                geometry = Point(poi.location.toGeoPoint().toPosition()),
                properties = buildJsonObject {
                    put(IdProperty, poi.id)
                    put("type", poi.type.value)
                    put(NameProperty, poi.name)
                },
            )
        }
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val onClick = remember(onPoiClick) { idClickHandler(onPoiClick) }
    val badges = PoiResponse.Type.entries.associateWith { type -> rememberBadge(type.icon(), type.markerColor()) }
    val from = TrailMapViewModel.MinPoiZoom
    SymbolLayer(
        id = "poi",
        source = source,
        minZoom = from.toFloat(),
        iconImage = switch(
            feature["type"],
            *PoiResponse.Type.entries.filter { it != PoiResponse.Type.unknown_default_open_api }
                .map { type -> case(type.value, image(badges.getValue(type), PoiBadge)) }.toTypedArray(),
            fallback = image(badges.getValue(PoiResponse.Type.unknown_default_open_api), PoiBadge),
        ),
        // più piccoli da lontano, pieni da vicino
        iconSize = interpolate(linear(), zoom(), from to const(0.7f), 14 to const(1f)),
        iconOpacity = fadeInFrom(from),
        // Collisioni: un POI che coprirebbe un altro simbolo (o un rifugio, che ha la precedenza) si nasconde, con una
        // dissolvenza, e torna avvicinandosi. Il nome è facoltativo: se non c'è posto resta il bollino
        iconAllowOverlap = const(false),
        iconIgnorePlacement = const(false),
        onClick = onClick,
        hitPadding = 6.dp,
        textField = format(span(feature[NameProperty].asString())),
        textFont = MapFont,
        textSize = const(11.sp),
        textColor = const(label),
        textHaloColor = const(halo),
        textHaloWidth = const(1.5.dp),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = textOffset(0.em, 1.4.em),
        textMaxWidth = const(8.em),
        textOptional = const(true),
        // i nomi solo da vicino, in dissolvenza
        textOpacity = fadeInFrom(PoiLabelZoom),
    )
}

private val PoiBadge = DpSize(30.dp, 30.dp)
private const val NameProperty = "name"
private const val PoiLabelZoom = 13.0

/** Testo e alone dei nomi sopra la mappa: dipendono dalla mappa (satellite e notte scuri), non dal tema. */
internal fun mapLabelInk(mapIsDark: Boolean): Color = if (mapIsDark) InkDark else InkLight
internal fun mapLabelHalo(mapIsDark: Boolean): Color = if (mapIsDark) Color.Black.copy(alpha = 0.75f) else Color.White

/** Il carattere dei nomi sulla mappa: quello che il server dei glifi di OpenFreeMap ha (lo stesso dello stile). */
internal val MapFont = const(listOf("Noto Sans Regular"))

/**
 * Un "bollino" da mappa nello stile di Apple Maps: tondo pieno nel colore della categoria, anello bianco, simbolo
 * bianco e un'ombra morbida sotto, in un'immagine sola (così il bollino intero partecipa alle collisioni). Lo usano
 * anche i rifugi (RefugeMapLayers).
 */
@Composable
internal fun rememberBadge(icon: ImageVector, fill: Color, glyphFraction: Float = 0.56f): Painter {
    val glyph = rememberVectorPainter(icon)
    return remember(glyph, fill, glyphFraction) { BadgePainter(glyph, fill, glyphFraction) }
}

private class BadgePainter(private val glyph: Painter, private val fill: Color, private val glyphFraction: Float) : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        val ring = 2.dp.toPx()
        val shadow = 1.5.dp.toPx()
        // il tondo lascia spazio all'ombra, che scende di poco
        val radius = size.minDimension / 2 - shadow
        val center = Offset(size.width / 2, size.height / 2 - shadow / 2)
        drawCircle(Color.Black.copy(alpha = 0.22f), radius + shadow * 0.4f, center.copy(y = center.y + shadow))
        drawCircle(Color.White, radius, center)
        drawCircle(fill, radius - ring, center)
        val glyphSize = radius * 2 * glyphFraction
        translate(center.x - glyphSize / 2, center.y - glyphSize / 2) {
            with(glyph) { draw(Size(glyphSize, glyphSize), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
}

/** Il blu della posizione di Apple Maps: lo riconosce chiunque come "sei qui". Colore della mappa, non del tema. */
internal val LocationBlue = Color(0xFF0A84FF)

/** Il pallino "sei qui" come su iPhone: blu pieno, anello bianco spesso e ombra morbida. */
@Composable
internal fun rememberLocationDot(): Painter = remember { LocationDotPainter() }

private class LocationDotPainter : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        val shadow = 2.dp.toPx()
        val radius = size.minDimension / 2 - shadow
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius + shadow * 0.5f, c.copy(y = c.y + shadow * 0.6f))
        drawCircle(Color.White, radius, c)
        drawCircle(LocationBlue, radius * 0.68f, c)
    }
}

/** Il colore della categoria di un POI sulla mappa (Color.kt, Marker*). */
internal fun PoiResponse.Type.markerColor(): Color = when (this) {
    PoiResponse.Type.SHELTER -> MarkerShelter
    PoiResponse.Type.WATER_SOURCE -> MarkerWater
    PoiResponse.Type.VIEWPOINT -> MarkerViewpoint
    PoiResponse.Type.DANGER_ZONE -> MarkerDanger
    PoiResponse.Type.EQUIPMENT_POINT -> MarkerEquipment
    PoiResponse.Type.INFORMATION -> MarkerInfo
    PoiResponse.Type.unknown_default_open_api -> MarkerOther
}

/** Il simbolo di un tipo di POI, uguale sulla mappa e nella lista del dettaglio. */
fun PoiResponse.Type.icon(): ImageVector = when (this) {
    PoiResponse.Type.SHELTER -> Icons.Filled.Home
    PoiResponse.Type.WATER_SOURCE -> WaterDrop
    PoiResponse.Type.VIEWPOINT -> Peak
    PoiResponse.Type.DANGER_ZONE -> Icons.Filled.Warning
    PoiResponse.Type.EQUIPMENT_POINT -> Icons.Filled.Build
    PoiResponse.Type.INFORMATION -> Icons.Filled.Info
    PoiResponse.Type.unknown_default_open_api -> Icons.Filled.Place
}

// Panorama: due cime (material-icons-core non ha né le montagne né il binocolo)
private val Peak: ImageVector by lazy {
    ImageVector.Builder("Peak", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(2f, 19.5f)
            lineTo(9f, 7f)
            lineTo(12.6f, 13.4f)
            lineTo(15f, 10f)
            lineTo(22f, 19.5f)
            close()
        }
    }.build()
}

// Goccia per le sorgenti: material-icons-core non ce l'ha
private val WaterDrop: ImageVector by lazy {
    ImageVector.Builder("WaterDrop", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2.5f)
            curveTo(12f, 2.5f, 5.5f, 9.8f, 5.5f, 14.5f)
            curveTo(5.5f, 18.1f, 8.4f, 21f, 12f, 21f)
            curveTo(15.6f, 21f, 18.5f, 18.1f, 18.5f, 14.5f)
            curveTo(18.5f, 9.8f, 12f, 2.5f, 12f, 2.5f)
            close()
        }
    }.build()
}

private fun TrailSummaryResponse.toFeature(): Feature<LineString, JsonObject>? {
    val positions = path.toPositions()
    if (positions.size < 2) return null
    return Feature(geometry = LineString(positions), properties = buildJsonObject { put(IdProperty, id) })
}

// Il primo elemento toccato che ha un id: si apre quello, e il tocco non arriva alla mappa sotto.
// Lo usano anche i pericoli (hazard/ui/HazardMapLayers.kt), con la stessa proprietà "id"
internal fun idClickHandler(onClick: (String) -> Unit): FeaturesClickHandler = { features ->
    val id = features.firstNotNullOfOrNull { it.properties?.get(IdProperty)?.jsonPrimitive?.contentOrNull }
    if (id != null) {
        onClick(id)
        ClickResult.Consume
    } else {
        ClickResult.Pass
    }
}
