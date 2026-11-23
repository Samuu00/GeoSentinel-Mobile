// Funzioni di formattazione condivise dalle schermate dei pericoli.
package it.unical.geosentinel_mobile.hazard.ui

import it.unical.geosentinel_mobile.trail.ui.formatKm
import kotlin.math.roundToLong

/**
 * Formatta la distanza di un pericolo per la lista "Pericoli vicini". Sotto il chilometro restituisce i metri
 * arrotondati alla decina ("350 m"), oltre i chilometri con un decimale e la virgola ("2,1 km"). La preposizione "a"
 * davanti non è qui ma nella stringa `hazards_distance`.
 */
fun distanceLabel(meters: Double): String {
    val tens = (meters / 10).roundToLong().coerceAtLeast(0) * 10
    return if (tens < 1000) "$tens m" else "${formatKm(meters)} km"
}
