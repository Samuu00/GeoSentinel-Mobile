package it.unical.geosentinel_mobile.refuge.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.ui.theme.MarkerRefuge
import it.unical.geosentinel_mobile.trail.ui.fadeInFrom
import it.unical.geosentinel_mobile.trail.ui.idClickHandler
import it.unical.geosentinel_mobile.trail.ui.rememberBadge
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

// Da qui in su il nome del rifugio sotto il bollino
private const val LabelZoom = 11.0

/**
 * I rifugi sulla mappa (M5.1, rifatti a 11/2026 come i luoghi di Apple Maps): bollino verde abete più grande dei POI,
 * con la casa bianca, anello bianco e ombra; da vicino il nome sotto, in grassetto. Non si confonde con il POI
 * "bivacco" (casa sul legno). Il tocco apre il dettaglio. [label] e [halo] come in PoiMarkers.
 */
@Composable
@MaplibreComposable
fun RefugeMarkers(refuges: List<RefugeResponse>, label: Color, halo: Color, onRefugeClick: (String) -> Unit) {
    val features = remember(refuges) {
        refuges.map { refuge ->
            Feature(
                geometry = Point(refuge.location.toGeoPoint().toPosition()),
                properties = buildJsonObject {
                    put("id", refuge.id)
                    put("name", refuge.name)
                },
            )
        }
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val onClick = remember(onRefugeClick) { idClickHandler(onRefugeClick) }
    val badge = rememberBadge(Icons.Filled.Home, MarkerRefuge, glyphFraction = 0.52f)
    val from = RefugeMapViewModel.MinZoom
    SymbolLayer(
        id = "rifugi",
        source = source,
        minZoom = from.toFloat(),
        iconImage = image(badge, DpSize(38.dp, 38.dp)),
        iconSize = interpolate(linear(), zoom(), from to const(0.65f), 13 to const(1f)),
        iconOpacity = fadeInFrom(from),
        // Collisioni come per i POI; disegnati dopo, i rifugi hanno la precedenza e nascondono i POI sotto
        iconAllowOverlap = const(false),
        iconIgnorePlacement = const(false),
        onClick = onClick,
        hitPadding = 6.dp,
        // il grassetto dello stesso server di glifi dello stile (OpenFreeMap)
        textField = format(span(feature["name"].asString())),
        textFont = const(listOf("Noto Sans Bold")),
        textSize = const(12.sp),
        textColor = const(label),
        textHaloColor = const(halo),
        textHaloWidth = const(1.5.dp),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = textOffset(0.em, 1.6.em),
        textMaxWidth = const(9.em),
        textOptional = const(true),
        textOpacity = fadeInFrom(LabelZoom),
    )
}
