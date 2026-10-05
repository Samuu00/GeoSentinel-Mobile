package it.unical.geosentinel_mobile.refuge.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.trail.ui.idClickHandler
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

/**
 * I rifugi sulla mappa (M5.1): tondo pieno nel verde del tema con la casa in negativo, più grande dei POI. Così non si
 * confonde con il POI "bivacco" (casa nel tondo chiaro). Il tocco apre il dettaglio.
 */
@Composable
@MaplibreComposable
fun RefugeMarkers(refuges: List<RefugeResponse>, fill: Color, outline: Color, symbol: Color, onRefugeClick: (String) -> Unit) {
    val features = remember(refuges) {
        refuges.map { refuge ->
            Feature(
                geometry = Point(refuge.location.toGeoPoint().toPosition()),
                properties = buildJsonObject { put("id", refuge.id) },
            )
        }
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val onClick = remember(onRefugeClick) { idClickHandler(onRefugeClick) }
    CircleLayer(
        id = "rifugi-sfondo",
        source = source,
        color = const(fill),
        radius = const(17.dp),
        strokeColor = const(outline),
        strokeWidth = const(2.dp),
        onClick = onClick,
        hitPadding = 8.dp,
    )
    SymbolLayer(
        id = "rifugi-simbolo",
        source = source,
        iconImage = image(rememberVectorPainter(Icons.Filled.Home), DpSize(20.dp, 20.dp), drawAsSdf = true),
        iconColor = const(symbol),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}
