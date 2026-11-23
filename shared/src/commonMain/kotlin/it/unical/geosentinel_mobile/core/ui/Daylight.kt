// Calcolo di alba, tramonto e fase del cielo per le testate delle schermate.
// Il cielo disegnato in alto segue l'ora reale (giorno, luce dorata, notte) e l'app indica quanta luce resta
// prima del tramonto, un'informazione utile a chi è in montagna. Le formule sono l'approssimazione della NOAA
// dell'equazione dell'alba: alle nostre latitudini l'errore è di un paio di minuti, sufficiente per un'indicazione
// come "2 h 11 min di luce" ma non per la navigazione.
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

/** Le tre condizioni di luce con cui viene disegnato il cielo nelle testate. */
enum class SkyPhase {
    /** Pieno giorno. */
    DAY,
    /** Luce dorata: poco dopo l'alba e prima del tramonto. */
    GOLDEN,
    /** Notte: da mezz'ora dopo il tramonto a mezz'ora prima dell'alba. */
    NIGHT }

/**
 * Alba e tramonto di un giorno. Valgono null entrambi quando il sole resta tutto il giorno sopra o sotto
 * l'orizzonte, cosa che accade solo vicino ai poli.
 */
data class SunTimes(val sunrise: Instant?, val sunset: Instant?)

/**
 * Lo stato del cielo in un certo istante: la fase e gli eventi utili da mostrare.
 *
 * @property sunset il tramonto di oggi.
 * @property nextSunrise la prossima alba: quella di oggi se non è ancora sorto il sole, altrimenti quella di domani.
 */
data class Daylight(
    val phase: SkyPhase,
    val sunset: Instant?,
    val nextSunrise: Instant?,
    // Luce che resta fino al tramonto; null quando il sole non è sopra l'orizzonte.
    val remaining: Duration?,
)

// Giorno giuliano dell'epoca astronomica J2000 (1 gennaio 2000, mezzogiorno UTC).
private const val J2000 = 2451545.0
// Giorno giuliano dell'epoca Unix (1 gennaio 1970, mezzanotte UTC).
private const val UnixEpochJulian = 2440587.5
// Millisecondi in un giorno.
private const val MillisPerDay = 86_400_000.0
/** Da gradi a radianti. */
private fun rad(deg: Double) = deg * PI / 180.0
/** Da radianti a gradi. */
private fun deg(rad: Double) = rad * 180.0 / PI

/**
 * Calcola alba e tramonto del giorno UTC che contiene [day], nel punto [lat], [lon] (gradi, longitudine positiva
 * verso est).
 *
 * I passaggi sono quelli dell'equazione dell'alba: dal giorno giuliano si ricavano l'anomalia media del sole,
 * l'equazione del centro, la longitudine eclittica, il mezzogiorno solare (transito) e la declinazione. L'angolo
 * orario dell'alba usa -0,833 gradi di altezza, che tengono conto della rifrazione e del raggio del disco solare.
 * Se l'angolo non esiste (coseno fuori dall'intervallo da -1 a 1) il sole non sorge o non tramonta.
 */
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
    /** Converte un giorno giuliano in un `Instant`. */
    fun instant(julian: Double) = Instant.fromEpochMilliseconds(((julian - UnixEpochJulian) * MillisPerDay).roundToLong())
    return SunTimes(instant(transit - hourAngle / 360.0), instant(transit + hourAngle / 360.0))
}

// Durata della luce dorata: la prima ora dopo l'alba e l'ultima ora e mezza prima del tramonto.
private val GoldenAfterSunrise = 60.minutes
private val GoldenBeforeSunset = 90.minutes
// Crepuscolo: per mezz'ora prima dell'alba e dopo il tramonto il cielo resta "dorato" e non ancora notte.
private val Twilight = 30.minutes

/**
 * Restituisce lo stato del cielo nell'istante [now] nel punto [lat], [lon].
 * Vicino ai poli, quando alba o tramonto non esistono, si considera giorno senza eventi da mostrare.
 */
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
