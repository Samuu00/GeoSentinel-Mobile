package it.unical.geosentinel_mobile.core.ui

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// La luce che resta (redesign "Luce di quota"): il cielo delle testate segue l'ora vera e dice quando tramonta.
// Equazione dell'alba (approssimazione NOAA, errore di un paio di minuti alle nostre latitudini): basta per un cielo
// e per "2 h 11 min di luce", non per la navigazione.

/** Le tre luci del cielo nelle testate. */
enum class SkyPhase { DAY, GOLDEN, NIGHT }

/** Alba e tramonto di un giorno (null = sole sempre sopra o sempre sotto l'orizzonte, vicino ai poli). */
data class SunTimes(val sunrise: Instant?, val sunset: Instant?)

/** Com'è il cielo adesso: la fase e il prossimo evento utile (tramonto di giorno, alba di notte). */
data class Daylight(
    val phase: SkyPhase,
    val sunset: Instant?,
    val nextSunrise: Instant?,
    // luce che resta fino al tramonto; null fuori dal giorno
    val remaining: Duration?,
)

private const val J2000 = 2451545.0
private const val UnixEpochJulian = 2440587.5
private const val MillisPerDay = 86_400_000.0
private fun rad(deg: Double) = deg * PI / 180.0
private fun deg(rad: Double) = rad * 180.0 / PI

/** Alba e tramonto del giorno UTC che contiene [day], in [lat]/[lon] (gradi, est positivo). */
fun sunTimes(day: Instant, lat: Double, lon: Double): SunTimes {
    val julianDay = floor(day.toEpochMilliseconds() / MillisPerDay) + UnixEpochJulian + 0.5
    val n = (julianDay - J2000 + 0.0008).roundToLong().toDouble()
    val meanSolarNoon = n - lon / 360.0
    val anomaly = (357.5291 + 0.98560028 * meanSolarNoon).mod(360.0)
    val center = 1.9148 * sin(rad(anomaly)) + 0.02 * sin(rad(2 * anomaly)) + 0.0003 * sin(rad(3 * anomaly))
    val longitude = (anomaly + center + 180.0 + 102.9372).mod(360.0)
    val transit = J2000 + meanSolarNoon + 0.0053 * sin(rad(anomaly)) - 0.0069 * sin(rad(2 * longitude))
    val declination = asin(sin(rad(longitude)) * sin(rad(23.4397)))
    val cosHourAngle = (sin(rad(-0.833)) - sin(rad(lat)) * sin(declination)) / (cos(rad(lat)) * cos(declination))
    if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return SunTimes(null, null)
    val hourAngle = deg(acos(cosHourAngle))
    fun instant(julian: Double) = Instant.fromEpochMilliseconds(((julian - UnixEpochJulian) * MillisPerDay).roundToLong())
    return SunTimes(instant(transit - hourAngle / 360.0), instant(transit + hourAngle / 360.0))
}

// Luce dorata: la prima ora dopo l'alba e l'ultima ora e mezza prima del tramonto, più il crepuscolo
private val GoldenAfterSunrise = 60.minutes
private val GoldenBeforeSunset = 90.minutes
private val Twilight = 30.minutes

/** La luce in [now] a [lat]/[lon]. */
fun daylightAt(now: Instant, lat: Double, lon: Double): Daylight {
    val today = sunTimes(now, lat, lon)
    val sunrise = today.sunrise
    val sunset = today.sunset
    if (sunrise == null || sunset == null) return Daylight(SkyPhase.DAY, null, null, null)
    val phase = when {
        now < sunrise - Twilight || now > sunset + Twilight -> SkyPhase.NIGHT
        now < sunrise + GoldenAfterSunrise || now > sunset - GoldenBeforeSunset -> SkyPhase.GOLDEN
        else -> SkyPhase.DAY
    }
    val nextSunrise = if (now < sunrise) sunrise else sunTimes(now + 1.days, lat, lon).sunrise
    val remaining = if (now in sunrise..sunset) sunset - now else null
    return Daylight(phase, sunset, nextSunrise, remaining)
}
