package it.unical.geosentinel_mobile.hazard.ui

import it.unical.geosentinel_mobile.trail.ui.formatKm
import kotlin.math.roundToLong

/**
 * Distanza per la lista "Pericoli vicini" (§2: distanze in metri): "350 m" sotto il chilometro, arrotondata alla decina,
 * poi "2,1 km" con un decimale e la virgola. Il "a" davanti sta nella stringa (`hazards_distance`).
 */
fun distanceLabel(meters: Double): String {
    val tens = (meters / 10).roundToLong().coerceAtLeast(0) * 10
    return if (tens < 1000) "$tens m" else "${formatKm(meters)} km"
}
