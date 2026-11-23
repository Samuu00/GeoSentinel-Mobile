// Livelli MapLibre disegnati sopra la mappa base: linee dei sentieri, percorso da A a B, simboli dei POI, pallino della
// posizione e funzioni di supporto (dissolvenze, bollini, gestione dei tocchi) condivise con rifugi e pericoli. I
// colori dello stato sono fissi (Color.kt) e uguali su tutti gli stili di mappa; un sentiero chiuso è anche
// tratteggiato, così si riconosce senza distinguere i colori.
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
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.location.toPositions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.unical.geosentinel_mobile.core.ui.theme.MapLabelInkDark
import it.unical.geosentinel_mobile.core.ui.theme.MapLabelInkLight
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
import org.maplibre.compose.expressions.value.DpValue
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

/** Nome della proprietà GeoJSON che contiene l'id dell'elemento, letta al tocco. */
private const val IdProperty = "id"

/**
 * Disegna le linee dei sentieri sulla mappa, con una sorgente GeoJSON per ogni stato: in MapLibre il tratteggio non può
 * dipendere da una proprietà dell'elemento, quindi i sentieri chiusi devono stare in un livello a parte. Le linee sono
 * discrete (sottili e senza bordo chiaro), così un percorso evidenziato risalta sopra di loro. [unknownColor] è il
 * colore per uno stato sconosciuto; il tocco su una linea chiama [onTrailClick] con l'id del sentiero.
 */
@Composable
@MaplibreComposable
fun TrailLines(trails: List<TrailSummaryResponse>, unknownColor: Color, onTrailClick: (String) -> Unit) {
    val byState = remember(trails) { trails.groupBy { TrailState.of(it.status.value) } }
    val onClick = remember(onTrailClick) { idClickHandler(onTrailClick) }
    TrailState.entries.forEach { state ->
        val features = remember(byState, state) { byState[state].orEmpty().mapNotNull { it.toFeature() } }
        val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
        PathLayers("sentieri-${state.name.lowercase()}", source, state.lineColor(unknownColor), casing = null, state == TrailState.CLOSED, onClick, TrailMapViewModel.MinTrailZoom, width = QuietLineWidth)
    }
}

/**
 * Disegna il percorso completo di un solo sentiero, nella mappa della pagina di dettaglio, nel colore del suo stato e
 * con il bordo [casing] che lo stacca dalla mappa.
 */
@Composable
@MaplibreComposable
fun TrailPath(positions: List<Position>, state: TrailState, casing: Color, unknownColor: Color) {
    if (positions.size < 2) return
    val source = rememberGeoJsonSource(GeoJsonData.Features(LineString(positions)))
    PathLayers("percorso", source, state.lineColor(unknownColor), casing, state == TrailState.CLOSED, onClick = null)
}

/**
 * Disegna il percorso da A a B evidenziato, come nelle app di navigazione: linea spessa con il bordo chiaro, sopra i
 * sentieri discreti della mappa. [color] è il colore `tertiary` del tema (il pino, usato per l'azione principale), non un
 * colore di stato.
 */
@Composable
@MaplibreComposable
fun RouteLine(positions: List<Position>, color: Color, casing: Color) {
    if (positions.size < 2) return
    val source = rememberGeoJsonSource(GeoJsonData.Features(LineString(positions)))
    PathLayers("percorso-ab", source, color, casing, dashed = false, onClick = null)
}

/**
 * Disegna i tratti fuori dai sentieri (dalla partenza al primo sentiero e dall'ultimo sentiero all'arrivo) come linee
 * tratteggiate, alla maniera del tratto a piedi nelle app di navigazione.
 */
@Composable
@MaplibreComposable
fun RouteConnectors(segments: List<Pair<Position, Position>>, color: Color) {
    val features = remember(segments) {
        segments.map { (from, to) -> Feature(geometry = LineString(listOf(from, to)), properties = buildJsonObject {}) }
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    PathLayers("percorso-fuori", source, color, casing = null, dashed = true, onClick = null)
}

/** Disegna il segnaposto dell'arrivo, sempre visibile (collisioni disattivate) perché è il punto scelto dall'utente. */
@Composable
@MaplibreComposable
fun RouteDestination(at: Position) {
    val source = rememberGeoJsonSource(GeoJsonData.Features(Point(at)))
    val pin = rememberBadge(AppIcons.MapPin, TrailOpen)
    SymbolLayer(
        id = "percorso-arrivo",
        source = source,
        iconImage = image(pin, PoiBadge),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

/** Colore della linea di un sentiero in base allo stato; [unknown] per uno stato sconosciuto. */
private fun TrailState.lineColor(unknown: Color): Color = when (this) {
    TrailState.OPEN -> TrailOpen
    TrailState.WARNING -> TrailWarning
    TrailState.CLOSED -> TrailClosed
    TrailState.UNKNOWN -> unknown
}

/** Spessore delle linee in funzione dello zoom: sottili da lontano, così i sentieri vicini restano distinguibili. */
private val LineWidth = interpolate(exponential(1.4f), zoom(), 7 to const(1.dp), 11 to const(2.dp), 14 to const(3.5.dp), 17 to const(5.dp))
/** Spessore dei sentieri della mappa principale, più sottili del percorso evidenziato. */
private val QuietLineWidth = interpolate(exponential(1.4f), zoom(), 9 to const(1.dp), 12 to const(1.5.dp), 14 to const(2.5.dp), 17 to const(3.5.dp))
/** Spessore del bordo chiaro sotto la linea, sempre un po' più largo della linea per staccarla dalla mappa. */
private val CasingWidth = interpolate(exponential(1.4f), zoom(), 7 to const(2.5.dp), 11 to const(3.5.dp), 14 to const(6.dp), 17 to const(8.dp))

/**
 * Espressione di opacità per una comparsa graduale: un livello con `minZoom = from` è invisibile sotto [from] e diventa
 * pieno nei successivi [FadeZoom] livelli di zoom. Così sentieri e simboli compaiono e scompaiono in dissolvenza mentre
 * si zooma, invece che di colpo.
 */
internal fun fadeInFrom(from: Double): Expression<FloatValue> = interpolate(linear(), zoom(), from to const(0f), from + FadeZoom to const(1f))
/** Ampiezza della dissolvenza, in livelli di zoom. */
internal const val FadeZoom = 0.6

/**
 * Disegna una linea come uno o due livelli MapLibre: il bordo opzionale [casing] sotto e la linea vera sopra.
 *
 * @param id prefisso degli id dei livelli.
 * @param dashed true per una linea tratteggiata (sentieri chiusi, tratti fuori dai sentieri).
 * @param onClick gestore del tocco, oppure null se la linea non è toccabile.
 * @param minZoom soglia di zoom da cui il livello compare in dissolvenza; null per una linea sempre visibile.
 * @param width spessore in funzione dello zoom.
 */
@Composable
@MaplibreComposable
private fun PathLayers(
    id: String,
    source: GeoJsonSource,
    color: Color,
    casing: Color?,
    dashed: Boolean,
    onClick: FeaturesClickHandler?,
    minZoom: Double? = null,
    width: Expression<DpValue> = LineWidth,
) {
    // Senza soglia (come nel dettaglio del sentiero) la linea è sempre a piena opacità.
    val opacity = minZoom?.let(::fadeInFrom) ?: const(1f)
    val from = minZoom?.toFloat() ?: 0f
    if (casing != null) {
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
    }
    LineLayer(
        id = id,
        source = source,
        minZoom = from,
        color = const(color),
        width = width,
        opacity = opacity,
        cap = const(if (dashed) LineCap.Butt else LineCap.Round),
        join = const(LineJoin.Round),
        // Trattini lunghi due volte lo spessore della linea, spazi lunghi una volta e mezza.
        dasharray = if (dashed) const(listOf(2, 1.5)) else null,
        onClick = onClick,
        // La linea è sottile: l'area di tocco si allarga di 16 dp, così il dito la prende anche se non la centra.
        hitPadding = 16.dp,
    )
}

/**
 * Disegna i POI sulla mappa nello stile delle categorie di Apple Maps: bollino pieno nel colore del tipo, simbolo
 * bianco, anello bianco e un'ombra leggera; da vicino compare il nome sotto. Il simbolo identifica il tipo anche senza
 * distinguere i colori. Il tocco chiama [onPoiClick] con l'id del POI. [label] e [halo] sono il colore del testo dei
 * nomi e del suo alone (scuro su chiaro di giorno, il contrario sulle mappe scure).
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
        // Bollini più piccoli da lontano, a grandezza piena da vicino.
        iconSize = interpolate(linear(), zoom(), from to const(0.7f), 14 to const(1f)),
        iconOpacity = fadeInFrom(from),
        // Collisioni attive: un POI che coprirebbe un altro simbolo (o un rifugio, che ha la precedenza) si nasconde in
        // dissolvenza e ricompare avvicinandosi. Il nome è facoltativo: se non c'è spazio resta solo il bollino.
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
        // I nomi compaiono solo da vicino, in dissolvenza.
        textOpacity = fadeInFrom(PoiLabelZoom),
    )
}

/** Dimensione dell'immagine di un bollino sulla mappa. */
private val PoiBadge = DpSize(30.dp, 30.dp)

/** Nome della proprietà GeoJSON con il nome del POI. */
private const val NameProperty = "name"

/** Zoom da cui compaiono i nomi dei POI. */
private const val PoiLabelZoom = 13.0

/**
 * Colore del testo dei nomi sopra la mappa. Dipende dalla mappa (satellite e mappa notturna sono scuri), non dal tema
 * dell'app.
 */
internal fun mapLabelInk(mapIsDark: Boolean): Color = if (mapIsDark) MapLabelInkDark else MapLabelInkLight
/** Colore dell'alone dei nomi sopra la mappa, con lo stesso criterio di mapLabelInk. */
internal fun mapLabelHalo(mapIsDark: Boolean): Color = if (mapIsDark) Color.Black.copy(alpha = 0.75f) else Color.White

/** Carattere dei nomi sulla mappa: deve essere uno di quelli offerti dal server dei glifi di OpenFreeMap, come nello stile. */
internal val MapFont = const(listOf("Noto Sans Regular"))

/**
 * Crea un "bollino" da mappa nello stile di Apple Maps: cerchio pieno nel colore [fill] della categoria, anello bianco,
 * simbolo [icon] bianco e un'ombra morbida sotto, tutto in un'unica immagine (così l'intero bollino partecipa alle
 * collisioni). [glyphFraction] è la frazione del diametro occupata dal simbolo. È usato anche per i rifugi
 * (RefugeMapLayers).
 */
@Composable
internal fun rememberBadge(icon: ImageVector, fill: Color, glyphFraction: Float = 0.56f): Painter {
    val glyph = rememberVectorPainter(icon)
    return remember(glyph, fill, glyphFraction) { BadgePainter(glyph, fill, glyphFraction) }
}

/** Painter che disegna il bollino descritto in rememberBadge. */
private class BadgePainter(private val glyph: Painter, private val fill: Color, private val glyphFraction: Float) : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified

    /** Disegna ombra, anello bianco, cerchio colorato e simbolo bianco al centro. */
    override fun DrawScope.onDraw() {
        val ring = 2.dp.toPx()
        val shadow = 1.5.dp.toPx()
        // Il cerchio è un po' più piccolo dell'immagine per lasciare spazio all'ombra, spostata leggermente in basso.
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

/** Blu della posizione dell'utente, lo stesso di Apple Maps, riconoscibile come "sei qui". È un colore della mappa, non del tema. */
internal val LocationBlue = Color(0xFF0A84FF)

/** Crea l'immagine del pallino "sei qui" come su iPhone: blu pieno, anello bianco spesso e ombra morbida. */
@Composable
internal fun rememberLocationDot(): Painter = remember { LocationDotPainter() }

/** Painter che disegna il pallino della posizione. */
private class LocationDotPainter : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified

    /** Disegna ombra, cerchio bianco e pallino blu al centro. */
    override fun DrawScope.onDraw() {
        val shadow = 2.dp.toPx()
        val radius = size.minDimension / 2 - shadow
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius + shadow * 0.5f, c.copy(y = c.y + shadow * 0.6f))
        drawCircle(Color.White, radius, c)
        drawCircle(LocationBlue, radius * 0.68f, c)
    }
}

/** Colore della categoria di un POI sulla mappa, dai colori Marker* di Color.kt. */
internal fun PoiResponse.Type.markerColor(): Color = when (this) {
    PoiResponse.Type.SHELTER -> MarkerShelter
    PoiResponse.Type.WATER_SOURCE -> MarkerWater
    PoiResponse.Type.VIEWPOINT -> MarkerViewpoint
    PoiResponse.Type.DANGER_ZONE -> MarkerDanger
    PoiResponse.Type.EQUIPMENT_POINT -> MarkerEquipment
    PoiResponse.Type.INFORMATION -> MarkerInfo
    PoiResponse.Type.unknown_default_open_api -> MarkerOther
}

/** Simbolo di un tipo di POI, uguale sulla mappa e nella lista del dettaglio. */
fun PoiResponse.Type.icon(): ImageVector = when (this) {
    PoiResponse.Type.SHELTER -> Icons.Filled.Home
    PoiResponse.Type.WATER_SOURCE -> WaterDrop
    PoiResponse.Type.VIEWPOINT -> Peak
    PoiResponse.Type.DANGER_ZONE -> Icons.Filled.Warning
    PoiResponse.Type.EQUIPMENT_POINT -> Icons.Filled.Build
    PoiResponse.Type.INFORMATION -> Icons.Filled.Info
    PoiResponse.Type.unknown_default_open_api -> Icons.Filled.Place
}

/** Icona del punto panoramico: due cime. Disegnata a mano perché material-icons-core non ha né montagne né binocolo. */
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

/** Icona a goccia per le sorgenti d'acqua, disegnata a mano perché material-icons-core non la include. */
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

/** Converte un sentiero in feature GeoJSON con il suo id; null se il percorso ha meno di due punti. */
private fun TrailSummaryResponse.toFeature(): Feature<LineString, JsonObject>? {
    val positions = path.toPositions()
    if (positions.size < 2) return null
    return Feature(geometry = LineString(positions), properties = buildJsonObject { put(IdProperty, id) })
}

/**
 * Crea un gestore dei tocchi per un livello: prende il primo elemento toccato che ha un id, chiama [onClick] e consuma
 * il tocco, che quindi non arriva alla mappa sotto; se nessun elemento ha un id il tocco passa oltre. È usato anche dai
 * pericoli (hazard/ui/HazardMapLayers.kt), che usano la stessa proprietà "id".
 */
internal fun idClickHandler(onClick: (String) -> Unit): FeaturesClickHandler = { features ->
    val id = features.firstNotNullOfOrNull { it.properties?.get(IdProperty)?.jsonPrimitive?.contentOrNull }
    if (id != null) {
        onClick(id)
        ClickResult.Consume
    } else {
        ClickResult.Pass
    }
}
