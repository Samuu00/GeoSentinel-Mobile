package it.unical.geosentinel_mobile.rescue.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import it.unical.geosentinel_mobile.trail.ui.idClickHandler
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

/**
 * Le allerte aperte sulla mappa dell'operatore (M7.2): tondo rosso (il rosso è dell'SOS, §2) con la campanella della
 * scheda Allerte. Un'allerta `ACTIVE` (nessuna squadra) è piena e più grande, una `DISPATCHED` ha il tondo chiaro e il
 * bordo rosso. Il tocco apre il dettaglio, che dice lo stato a parole.
 */
@Composable
@MaplibreComposable
fun SosAlertMarkers(alerts: List<SosAlertResponse>, light: Color, onAlertClick: (String) -> Unit) {
    val features = remember(alerts) {
        alerts.map { alert ->
            Feature(
                geometry = Point(alert.location.toGeoPoint().toPosition()),
                properties = buildJsonObject {
                    put("id", alert.id)
                    put("status", alert.status.value)
                },
            )
        }
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val onClick = remember(onAlertClick) { idClickHandler(onAlertClick) }
    CircleLayer(
        id = "allerte-sfondo",
        source = source,
        color = switch(feature["status"], case(SosAlertResponse.Status.ACTIVE.value, const(EmergencyRed)), fallback = const(light)),
        radius = switch(feature["status"], case(SosAlertResponse.Status.ACTIVE.value, const(19.dp)), fallback = const(15.dp)),
        strokeColor = switch(feature["status"], case(SosAlertResponse.Status.ACTIVE.value, const(light)), fallback = const(EmergencyRed)),
        strokeWidth = const(3.dp),
        onClick = onClick,
        hitPadding = 8.dp,
    )
    SymbolLayer(
        id = "allerte-simbolo",
        source = source,
        iconImage = image(rememberVectorPainter(Icons.Filled.Notifications), DpSize(20.dp, 20.dp), drawAsSdf = true),
        iconColor = switch(feature["status"], case(SosAlertResponse.Status.ACTIVE.value, const(light)), fallback = const(EmergencyRed)),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}
