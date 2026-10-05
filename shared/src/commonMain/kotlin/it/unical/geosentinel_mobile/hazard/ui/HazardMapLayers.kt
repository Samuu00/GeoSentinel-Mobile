package it.unical.geosentinel_mobile.hazard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import it.unical.geosentinel_mobile.core.ui.theme.TrailWarning
import it.unical.geosentinel_mobile.trail.ui.idClickHandler
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

/**
 * I pericoli attivi sulla mappa (M4.4): un triangolo grande nel colore della gravità, come `Severity.color()` della lista
 * (rosso solo per `CRITICAL`, §2), con un bordo chiaro o scuro che lo stacca da ogni mappa. Il colore non è l'unico
 * segno: la card e la lista dicono la gravità a parole.
 */
@Composable
@MaplibreComposable
fun HazardMarkers(hazards: List<HazardResponse>, halo: Color, otherColor: Color, onHazardClick: (String) -> Unit) {
    val features = remember(hazards) {
        hazards.map { hazard ->
            Feature(
                geometry = Point(hazard.location.toGeoPoint().toPosition()),
                properties = buildJsonObject {
                    put("id", hazard.id)
                    put("severity", hazard.severity.value)
                },
            )
        }
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val onClick = remember(onHazardClick) { idClickHandler(onHazardClick) }
    val triangle = rememberVectorPainter(Icons.Filled.Warning)
    SymbolLayer(
        id = "pericoli",
        source = source,
        // SDF: il colore si sceglie qui, per gravità
        iconImage = image(triangle, DpSize(30.dp, 30.dp), drawAsSdf = true),
        iconColor = switch(
            feature["severity"],
            case(HazardResponse.Severity.CRITICAL.value, const(EmergencyRed)),
            case(HazardResponse.Severity.HIGH.value, const(TrailWarning)),
            fallback = const(otherColor),
        ),
        iconHaloColor = const(halo),
        iconHaloWidth = const(2.dp),
        // tutti visibili: sono al massimo 50, e un pericolo nascosto da un'etichetta non va bene
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        onClick = onClick,
    )
}
