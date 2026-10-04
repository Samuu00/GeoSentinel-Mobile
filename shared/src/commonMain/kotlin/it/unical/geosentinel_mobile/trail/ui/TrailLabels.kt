package it.unical.geosentinel_mobile.trail.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.hazard_fallen_tree
import geosentinel_mobile.shared.generated.resources.hazard_flooding
import geosentinel_mobile.shared.generated.resources.hazard_landslide
import geosentinel_mobile.shared.generated.resources.hazard_other
import geosentinel_mobile.shared.generated.resources.hazard_severity_critical
import geosentinel_mobile.shared.generated.resources.hazard_severity_high
import geosentinel_mobile.shared.generated.resources.hazard_severity_low
import geosentinel_mobile.shared.generated.resources.hazard_severity_medium
import geosentinel_mobile.shared.generated.resources.hazard_severity_unknown
import geosentinel_mobile.shared.generated.resources.hazard_snow_ice
import geosentinel_mobile.shared.generated.resources.hazard_trail_erosion
import geosentinel_mobile.shared.generated.resources.hazard_wildlife
import geosentinel_mobile.shared.generated.resources.poi_danger_zone
import geosentinel_mobile.shared.generated.resources.poi_equipment_point
import geosentinel_mobile.shared.generated.resources.poi_information
import geosentinel_mobile.shared.generated.resources.poi_shelter
import geosentinel_mobile.shared.generated.resources.poi_unknown
import geosentinel_mobile.shared.generated.resources.poi_viewpoint
import geosentinel_mobile.shared.generated.resources.poi_water_source
import geosentinel_mobile.shared.generated.resources.trail_difficulty_difficult
import geosentinel_mobile.shared.generated.resources.trail_difficulty_easy
import geosentinel_mobile.shared.generated.resources.trail_difficulty_expert
import geosentinel_mobile.shared.generated.resources.trail_difficulty_moderate
import geosentinel_mobile.shared.generated.resources.trail_difficulty_unknown
import geosentinel_mobile.shared.generated.resources.trail_status_closed
import geosentinel_mobile.shared.generated.resources.trail_status_open
import geosentinel_mobile.shared.generated.resources.trail_status_unknown
import geosentinel_mobile.shared.generated.resources.trail_status_warning
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import it.unical.geosentinel_mobile.core.ui.theme.TrailClosed
import it.unical.geosentinel_mobile.core.ui.theme.TrailOpen
import it.unical.geosentinel_mobile.core.ui.theme.TrailWarning
import org.jetbrains.compose.resources.StringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Instant

// Etichette di montagna in italiano (§2). Riepilogo e dettaglio del client generato hanno enum diversi per lo stesso
// campo: si passa dal valore testuale, e un valore nuovo del server (unknown_default_open_api) diventa UNKNOWN.

/** Stato del sentiero. Si riconosce anche senza il colore: etichetta nel dettaglio, linea tratteggiata se chiuso. */
enum class TrailState(val label: StringResource) {
    OPEN(Res.string.trail_status_open),
    WARNING(Res.string.trail_status_warning),
    CLOSED(Res.string.trail_status_closed),
    UNKNOWN(Res.string.trail_status_unknown);

    companion object {
        fun of(value: String): TrailState = entries.firstOrNull { it != UNKNOWN && it.name == value } ?: UNKNOWN
    }
}

// Colori fissi dello stato (Color.kt): verde, ambra, rosso (il rosso dei sentieri chiusi, §2).
// Senza uno stato noto, il colore del testo secondario.
@Composable
fun TrailState.color(): Color = when (this) {
    TrailState.OPEN -> TrailOpen
    TrailState.WARNING -> TrailWarning
    TrailState.CLOSED -> TrailClosed
    TrailState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Difficoltà con le quattro parole del backend (Facile, Media, Difficile, Per esperti). Niente scala CAI: la sigla
 * (T, E, EE, EEA) la assegna chi gestisce il sentiero e non si ricava da queste quattro.
 */
enum class TrailDifficulty(val label: StringResource) {
    EASY(Res.string.trail_difficulty_easy),
    MODERATE(Res.string.trail_difficulty_moderate),
    DIFFICULT(Res.string.trail_difficulty_difficult),
    EXPERT(Res.string.trail_difficulty_expert),
    UNKNOWN(Res.string.trail_difficulty_unknown);

    companion object {
        fun of(value: String): TrailDifficulty = entries.firstOrNull { it != UNKNOWN && it.name == value } ?: UNKNOWN
    }
}

/** "Aggiornato alle 14:05" (oggi) o "Aggiornato il 03/10 alle 14:05": data e ora già nel formato italiano. */
data class UpdatedAt(val today: Boolean, val date: String, val time: String)

fun updatedAt(at: Instant, now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): UpdatedAt {
    val local = at.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date == local.date
    fun two(n: Int) = n.toString().padStart(2, '0')
    return UpdatedAt(today, date = "${two(local.day)}/${two(local.month.ordinal + 1)}", time = "${two(local.hour)}:${two(local.minute)}")
}

/** Lunghezza in km con un decimale e la virgola italiana ("4,2"). */
fun formatKm(meters: Double): String {
    val tenths = (meters / 100).roundToLong().coerceAtLeast(0)
    return "${tenths / 10},${tenths % 10}"
}

fun PoiResponse.Type.label(): StringResource = when (this) {
    PoiResponse.Type.SHELTER -> Res.string.poi_shelter
    PoiResponse.Type.WATER_SOURCE -> Res.string.poi_water_source
    PoiResponse.Type.VIEWPOINT -> Res.string.poi_viewpoint
    PoiResponse.Type.DANGER_ZONE -> Res.string.poi_danger_zone
    PoiResponse.Type.EQUIPMENT_POINT -> Res.string.poi_equipment_point
    PoiResponse.Type.INFORMATION -> Res.string.poi_information
    PoiResponse.Type.unknown_default_open_api -> Res.string.poi_unknown
}

fun HazardResponse.Type.label(): StringResource = when (this) {
    HazardResponse.Type.LANDSLIDE -> Res.string.hazard_landslide
    HazardResponse.Type.FALLEN_TREE -> Res.string.hazard_fallen_tree
    HazardResponse.Type.FLOODING -> Res.string.hazard_flooding
    HazardResponse.Type.TRAIL_EROSION -> Res.string.hazard_trail_erosion
    HazardResponse.Type.SNOW_ICE -> Res.string.hazard_snow_ice
    HazardResponse.Type.WILDLIFE -> Res.string.hazard_wildlife
    HazardResponse.Type.OTHER, HazardResponse.Type.unknown_default_open_api -> Res.string.hazard_other
}

fun HazardResponse.Severity.label(): StringResource = when (this) {
    HazardResponse.Severity.LOW -> Res.string.hazard_severity_low
    HazardResponse.Severity.MEDIUM -> Res.string.hazard_severity_medium
    HazardResponse.Severity.HIGH -> Res.string.hazard_severity_high
    HazardResponse.Severity.CRITICAL -> Res.string.hazard_severity_critical
    HazardResponse.Severity.unknown_default_open_api -> Res.string.hazard_severity_unknown
}

// Il rosso solo per CRITICAL (§2); HIGH in ambra, il resto nel colore del testo secondario
@Composable
fun HazardResponse.Severity.color(): Color = when (this) {
    HazardResponse.Severity.CRITICAL -> EmergencyRed
    HazardResponse.Severity.HIGH -> TrailWarning
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
