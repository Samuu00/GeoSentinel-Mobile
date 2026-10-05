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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.location.toPositions
import it.unical.geosentinel_mobile.core.ui.theme.TrailClosed
import it.unical.geosentinel_mobile.core.ui.theme.TrailOpen
import it.unical.geosentinel_mobile.core.ui.theme.TrailWarning
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
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
        PathLayers("sentieri-${state.name.lowercase()}", source, state.lineColor(unknownColor), casing, state == TrailState.CLOSED, onClick)
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

@Composable
@MaplibreComposable
private fun PathLayers(id: String, source: GeoJsonSource, color: Color, casing: Color, dashed: Boolean, onClick: FeaturesClickHandler?) {
    LineLayer(
        id = "$id-bordo",
        source = source,
        color = const(casing),
        width = const(7.dp),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
    LineLayer(
        id = id,
        source = source,
        color = const(color),
        width = const(4.dp),
        cap = const(if (dashed) LineCap.Butt else LineCap.Round),
        join = const(LineJoin.Round),
        // trattini lunghi due volte lo spessore, spazi di una volta e mezza
        dasharray = if (dashed) const(listOf(2, 1.5)) else null,
        onClick = onClick,
        // la linea è sottile: il dito la prende anche un po' più in là
        hitPadding = 16.dp,
    )
}

/** I POI: un tondo da card con il simbolo del tipo, cliccabili. Il simbolo dice il tipo anche senza colori. */
@Composable
@MaplibreComposable
fun PoiMarkers(pois: List<PoiResponse>, background: Color, outline: Color, symbol: Color, onPoiClick: (String) -> Unit) {
    val features = remember(pois) {
        pois.map { poi ->
            Feature(
                geometry = Point(poi.location.toGeoPoint().toPosition()),
                properties = buildJsonObject {
                    put(IdProperty, poi.id)
                    put("type", poi.type.value)
                },
            )
        }
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val onClick = remember(onPoiClick) { idClickHandler(onPoiClick) }
    CircleLayer(
        id = "poi-sfondo",
        source = source,
        color = const(background),
        radius = const(14.dp),
        strokeColor = const(outline),
        strokeWidth = const(1.5.dp),
        onClick = onClick,
        hitPadding = 8.dp,
    )
    val size = DpSize(18.dp, 18.dp)
    val shelter = rememberVectorPainter(Icons.Filled.Home)
    val water = rememberVectorPainter(WaterDrop)
    val viewpoint = rememberVectorPainter(Icons.Filled.Star)
    val danger = rememberVectorPainter(Icons.Filled.Warning)
    val equipment = rememberVectorPainter(Icons.Filled.Build)
    val info = rememberVectorPainter(Icons.Filled.Info)
    val other = rememberVectorPainter(Icons.Filled.Place)
    SymbolLayer(
        id = "poi-simbolo",
        source = source,
        // SDF: le icone si colorano qui con il colore del tema, qualunque sia quello del painter
        iconImage = switch(
            feature["type"],
            case(PoiResponse.Type.SHELTER.value, image(shelter, size, drawAsSdf = true)),
            case(PoiResponse.Type.WATER_SOURCE.value, image(water, size, drawAsSdf = true)),
            case(PoiResponse.Type.VIEWPOINT.value, image(viewpoint, size, drawAsSdf = true)),
            case(PoiResponse.Type.DANGER_ZONE.value, image(danger, size, drawAsSdf = true)),
            case(PoiResponse.Type.EQUIPMENT_POINT.value, image(equipment, size, drawAsSdf = true)),
            case(PoiResponse.Type.INFORMATION.value, image(info, size, drawAsSdf = true)),
            fallback = image(other, size, drawAsSdf = true),
        ),
        iconColor = const(symbol),
        // tutti visibili e sopra le etichette della mappa base: sono pochi (al massimo 50)
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

/** Il simbolo di un tipo di POI, uguale sulla mappa e nella lista del dettaglio. */
fun PoiResponse.Type.icon(): ImageVector = when (this) {
    PoiResponse.Type.SHELTER -> Icons.Filled.Home
    PoiResponse.Type.WATER_SOURCE -> WaterDrop
    PoiResponse.Type.VIEWPOINT -> Icons.Filled.Star
    PoiResponse.Type.DANGER_ZONE -> Icons.Filled.Warning
    PoiResponse.Type.EQUIPMENT_POINT -> Icons.Filled.Build
    PoiResponse.Type.INFORMATION -> Icons.Filled.Info
    PoiResponse.Type.unknown_default_open_api -> Icons.Filled.Place
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
