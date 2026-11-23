// Etichette e colori condivisi per sentieri, POI e pericoli: traducono i valori del server in testi italiani di
// montagna e in colori. Il client generato ha enum diversi per lo stesso campo nel riassunto e nel dettaglio di un
// sentiero, quindi la conversione passa dal valore testuale; un valore nuovo del server (unknown_default_open_api)
// diventa UNKNOWN invece di far andare in crash l'app.
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

/**
 * Stato di un sentiero con la relativa etichetta. Lo stato si riconosce anche senza il colore: c'è l'etichetta nel
 * dettaglio e la linea è tratteggiata quando il sentiero è chiuso.
 */
enum class TrailState(val label: StringResource) {
    OPEN(Res.string.trail_status_open),
    WARNING(Res.string.trail_status_warning),
    CLOSED(Res.string.trail_status_closed),
    UNKNOWN(Res.string.trail_status_unknown);

    companion object {
        /** Converte il valore testuale del server nello stato; un valore sconosciuto diventa UNKNOWN. */
        fun of(value: String): TrailState = entries.firstOrNull { it != UNKNOWN && it.name == value } ?: UNKNOWN
    }
}

/**
 * Colore dello stato, preso dai colori fissi in Color.kt: verde per aperto, ambra per attenzione e rosso per chiuso
 * (il rosso è riservato ai sentieri chiusi). Per uno stato sconosciuto si usa il colore del testo secondario.
 */
@Composable
fun TrailState.color(): Color = when (this) {
    TrailState.OPEN -> TrailOpen
    TrailState.WARNING -> TrailWarning
    TrailState.CLOSED -> TrailClosed
    TrailState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Difficoltà di un sentiero con i quattro livelli del backend (Facile, Media, Difficile, Per esperti). Non si usa la
 * scala CAI: la sigla (T, E, EE, EEA) la assegna chi gestisce il sentiero e non si può ricavare da questi quattro livelli.
 */
enum class TrailDifficulty(val label: StringResource) {
    EASY(Res.string.trail_difficulty_easy),
    MODERATE(Res.string.trail_difficulty_moderate),
    DIFFICULT(Res.string.trail_difficulty_difficult),
    EXPERT(Res.string.trail_difficulty_expert),
    UNKNOWN(Res.string.trail_difficulty_unknown);

    companion object {
        /** Converte il valore testuale del server nella difficoltà; un valore sconosciuto diventa UNKNOWN. */
        fun of(value: String): TrailDifficulty = entries.firstOrNull { it != UNKNOWN && it.name == value } ?: UNKNOWN
    }
}

/**
 * Dati per il testo "aggiornato": "Aggiornato alle hh:mm" se [today] è true, altrimenti "Aggiornato il gg/mm alle hh:mm".
 * [date] e [time] sono già nel formato italiano.
 */
data class UpdatedAt(val today: Boolean, val date: String, val time: String)

/** Calcola data e ora di [at] nel fuso [zone] e se cade nello stesso giorno di [now]. */
fun updatedAt(at: Instant, now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): UpdatedAt {
    val local = at.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date == local.date
    // Formatta un numero su due cifre, con lo zero davanti.
    fun two(n: Int) = n.toString().padStart(2, '0')
    return UpdatedAt(today, date = "${two(local.day)}/${two(local.month.ordinal + 1)}", time = "${two(local.hour)}:${two(local.minute)}")
}

/** Formatta una lunghezza in metri come chilometri con un decimale e la virgola italiana (per esempio "4,2"). */
fun formatKm(meters: Double): String {
    val tenths = (meters / 100).roundToLong().coerceAtLeast(0)
    return "${tenths / 10},${tenths % 10}"
}

/** Etichetta del tipo di un punto di interesse. */
fun PoiResponse.Type.label(): StringResource = when (this) {
    PoiResponse.Type.SHELTER -> Res.string.poi_shelter
    PoiResponse.Type.WATER_SOURCE -> Res.string.poi_water_source
    PoiResponse.Type.VIEWPOINT -> Res.string.poi_viewpoint
    PoiResponse.Type.DANGER_ZONE -> Res.string.poi_danger_zone
    PoiResponse.Type.EQUIPMENT_POINT -> Res.string.poi_equipment_point
    PoiResponse.Type.INFORMATION -> Res.string.poi_information
    PoiResponse.Type.unknown_default_open_api -> Res.string.poi_unknown
}

/** Etichetta del tipo di un pericolo. */
fun HazardResponse.Type.label(): StringResource = when (this) {
    HazardResponse.Type.LANDSLIDE -> Res.string.hazard_landslide
    HazardResponse.Type.FALLEN_TREE -> Res.string.hazard_fallen_tree
    HazardResponse.Type.FLOODING -> Res.string.hazard_flooding
    HazardResponse.Type.TRAIL_EROSION -> Res.string.hazard_trail_erosion
    HazardResponse.Type.SNOW_ICE -> Res.string.hazard_snow_ice
    HazardResponse.Type.WILDLIFE -> Res.string.hazard_wildlife
    HazardResponse.Type.OTHER, HazardResponse.Type.unknown_default_open_api -> Res.string.hazard_other
}

/** Etichetta della gravità di un pericolo. */
fun HazardResponse.Severity.label(): StringResource = when (this) {
    HazardResponse.Severity.LOW -> Res.string.hazard_severity_low
    HazardResponse.Severity.MEDIUM -> Res.string.hazard_severity_medium
    HazardResponse.Severity.HIGH -> Res.string.hazard_severity_high
    HazardResponse.Severity.CRITICAL -> Res.string.hazard_severity_critical
    HazardResponse.Severity.unknown_default_open_api -> Res.string.hazard_severity_unknown
}

/** Colore della gravità: rosso solo per CRITICAL, ambra per HIGH, il colore del testo secondario per le altre. */
@Composable
fun HazardResponse.Severity.color(): Color = when (this) {
    HazardResponse.Severity.CRITICAL -> EmergencyRed
    HazardResponse.Severity.HIGH -> TrailWarning
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
