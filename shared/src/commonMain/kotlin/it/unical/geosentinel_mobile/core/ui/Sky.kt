package it.unical.geosentinel_mobile.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.sky_duration_hm
import geosentinel_mobile.shared.generated.resources.sky_duration_m
import geosentinel_mobile.shared.generated.resources.sky_sunrise
import geosentinel_mobile.shared.generated.resources.sky_sunset_light
import geosentinel_mobile.shared.generated.resources.sky_sunset_short
import geosentinel_mobile.shared.generated.resources.sky_sunrise_short
import it.unical.geosentinel_mobile.trail.ui.DefaultArea
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.time.Clock
import kotlin.time.Instant

// Il cielo vivo (redesign "Luce di quota", l'elemento firma dell'app): le testate delle pagine mostrano il cielo
// dell'ora vera sopra le creste della Calabria (giorno, luce dorata, notte) e dicono quanta luce resta, che in montagna
// è un'informazione di sicurezza. Il contenuto sale sopra le creste come un foglio. Il cielo è decorativo: il testo
// sopra ha sempre il suo colore verificato ([SkyPalette.content], almeno 5:1 sulla fascia del testo).

/** Colori di un cielo. [top]/[upper] fanno la fascia del testo, [horizon] il bagliore dietro le creste. */
@Immutable
internal class SkyPalette(
    val top: Color,
    val upper: Color,
    val horizon: Color,
    val farRidge: Color,
    val nearRidge: Color,
    val forest: Color,
    // testo e icone sopra il cielo
    val content: Color,
    val chip: Color,
    // le icone della barra di stato vanno chiare
    val dark: Boolean,
    val stars: Boolean = false,
)

internal fun skyPalette(phase: SkyPhase): SkyPalette = when (phase) {
    // inchiostro sul celeste: 9:1 in cima, 10:1 sulla fascia del testo
    SkyPhase.DAY -> SkyPalette(
        top = Color(0xFF8EBFE3), upper = Color(0xFFA9D0EA), horizon = Color(0xFFDCEBF2),
        farRidge = Color(0xFFA9C0C8), nearRidge = Color(0xFF7E9AA0), forest = Color(0xFF1F4436),
        content = Color(0xFF0E1B16), chip = Color.White.copy(alpha = 0.6f), dark = false,
    )
    // bianco sul blu della sera: 7,3:1 in cima, 5,2:1 in fondo alla fascia del testo
    SkyPhase.GOLDEN -> SkyPalette(
        top = Color(0xFF3E4F88), upper = Color(0xFF5E6AA0), horizon = Color(0xFFF0BE96),
        farRidge = Color(0xFF9C8DB0), nearRidge = Color(0xFF6A6888), forest = Color(0xFF22352F),
        content = Color.White, chip = Color.White.copy(alpha = 0.18f), dark = true,
    )
    SkyPhase.NIGHT -> SkyPalette(
        top = Color(0xFF071120), upper = Color(0xFF13293F), horizon = Color(0xFF1C3549),
        farRidge = Color(0xFF1C3145), nearRidge = Color(0xFF13232F), forest = Color(0xFF0A1511),
        content = Color.White, chip = Color.White.copy(alpha = 0.12f), dark = true, stars = true,
    )
}

/**
 * La luce di adesso nel parco (il centro dell'area di default: niente permesso di posizione per un cielo).
 * ponytail: calcolata quando la pagina entra in composizione, non a ogni minuto; una pagina lasciata aperta per ore
 * mostra la luce di quando è stata aperta. Se serve, un ticker a minuto qui.
 */
@Composable
internal fun rememberDaylight(): Daylight = remember { daylightAt(Clock.System.now(), DefaultArea.lat, DefaultArea.lon) }

private fun Instant.clock(): String {
    val time = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${time.hour}:${time.minute.toString().padStart(2, '0')}"
}

/** "Tramonto 17:02 · 2 h 11 min di luce" di giorno, "Alba alle 6:58" dopo il tramonto. null se il sole non tramonta. */
@Composable
internal fun daylightLabel(daylight: Daylight): String? {
    val remaining = daylight.remaining
    val sunset = daylight.sunset
    val sunrise = daylight.nextSunrise
    return when {
        remaining != null && sunset != null -> {
            val minutes = remaining.inWholeMinutes.toInt()
            val duration = if (minutes >= 60) {
                stringResource(Res.string.sky_duration_hm, minutes / 60, minutes % 60)
            } else {
                stringResource(Res.string.sky_duration_m, minutes)
            }
            stringResource(Res.string.sky_sunset_light, sunset.clock(), duration)
        }
        sunrise != null -> stringResource(Res.string.sky_sunrise, sunrise.clock())
        else -> null
    }
}

/** "Tramonto 17:02" di giorno, "Alba 6:58" di notte: la versione corta per i chip sopra la mappa. */
@Composable
internal fun daylightShortLabel(daylight: Daylight): String? {
    val sunset = daylight.sunset
    val sunrise = daylight.nextSunrise
    return when {
        daylight.remaining != null && sunset != null -> stringResource(Res.string.sky_sunset_short, sunset.clock())
        sunrise != null -> stringResource(Res.string.sky_sunrise_short, sunrise.clock())
        else -> null
    }
}

/** Il chip della luce: capsula velata sopra il cielo, icona del tramonto (o della luna) e il testo. */
@Composable
internal fun DaylightChip(daylight: Daylight, palette: SkyPalette, modifier: Modifier = Modifier) {
    val label = daylightLabel(daylight) ?: return
    Surface(color = palette.chip, contentColor = palette.content, shape = CircleShape, modifier = modifier) {
        Row(
            Modifier.heightIn(min = 32.dp).padding(start = 12.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (daylight.remaining != null) AppIcons.Sunset else AppIcons.Moon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// Quanto il foglio del contenuto copre il fondo del cielo, con i suoi angoli tondi
internal val SheetOverlap = 28.dp

// Fascia delle creste in fondo al cielo
internal val RidgeBand = 132.dp

/**
 * Il cielo con le creste, disegnato dietro a un elemento alto quanto la testata. [parallax]: di quanti px la testata
 * è salita scorrendo (le creste scendono più piano, gli strati lontani più lenti). [rise]: 0..1, le creste che salgono
 * all'ingresso. [sheet]: il colore del foglio che copre il fondo con gli angoli tondi (null = niente foglio).
 */
internal fun Modifier.skyBackdrop(
    palette: SkyPalette,
    parallax: () -> Float = { 0f },
    rise: () -> Float = { 0f },
    sheet: Color? = null,
): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val band = RidgeBand.toPx().coerceAtMost(h * 0.7f)
    val bandTop = h - band
    val step = 3.dp.toPx()
    val far = ridge(w, h, step) { t -> bandTop + band * (0.42f - 0.40f * peaks(t, 2.6f, 0.9f)) }
    val near = ridge(w, h, step) { t -> bandTop + band * (0.66f - 0.26f * peaks(t, 1.8f, 2.1f)) }
    val corner = SheetOverlap.toPx()
    val forestBase = h - (if (sheet != null) corner else 0f) + 6.dp.toPx()
    val forest = forest(w, h, forestBase, 1.dp.toPx())
    val sky = Brush.verticalGradient(
        0f to palette.top,
        (bandTop / h).coerceIn(0.05f, 0.95f) to palette.upper,
        1f to palette.horizon,
        endY = h,
    )
    val stars = if (palette.stars) stars(w, bandTop) else emptyList()
    val lip = Path().apply { addRoundRect(RoundRect(0f, h - corner, w, h + corner, CornerRadius(corner))) }
    onDrawBehind {
        val lift = parallax().coerceAtLeast(0f)
        val riseBy = rise() * 28.dp.toPx()
        drawRect(sky)
        stars.forEach { (p, r) -> drawCircle(Color.White.copy(alpha = 0.8f), r, p) }
        clipRect(0f, 0f, w, h) {
            translate(top = lift * 0.45f + riseBy * 1.3f) { drawPath(far, palette.farRidge) }
            translate(top = lift * 0.25f + riseBy) { drawPath(near, palette.nearRidge) }
            translate(top = lift * 0.1f + riseBy * 0.6f) { drawPath(forest, palette.forest) }
        }
        if (sheet != null) drawPath(lip, sheet)
    }
}

/**
 * Testata delle pagine (redesign "Luce di quota"): il cielo dell'ora, il chip della luce, il titolo grande in Archivo
 * e, sotto le creste, l'inizio del foglio su cui sta il contenuto. [below] (segmenti, link) sta già sul foglio.
 * [topBar]: spazio in alto per la barra con Indietro e le azioni (che disegna chi la usa).
 */
@Composable
internal fun SkyHeader(
    title: String,
    modifier: Modifier = Modifier,
    intro: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    topBar: Boolean = false,
    daylight: Daylight = rememberDaylight(),
) {
    val palette = skyPalette(daylight.phase)
    val sheet = MaterialTheme.colorScheme.background
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    var placedTop by remember { mutableFloatStateOf(Float.NaN) }
    var firstTop by remember { mutableFloatStateOf(Float.NaN) }
    val still = rememberReduceMotion()
    val rise = remember { Animatable(if (still) 0f else 1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow)) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .fillMaxWidth()
                .onPlaced {
                    val y = it.positionInRoot().y
                    if (firstTop.isNaN()) firstTop = y
                    placedTop = y
                }
                .skyBackdrop(
                    palette,
                    // quanto è salita la testata da dove è comparsa
                    parallax = { if (placedTop.isNaN()) 0f else firstTop - placedTop },
                    rise = { rise.value },
                    sheet = sheet,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompositionLocalProvider(LocalContentColor provides palette.content) {
                Column(
                    Modifier
                        .widthIn(max = PageMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = topInset + if (topBar) 56.dp else 12.dp)
                        .graphicsLayer {
                            alpha = 1f - rise.value
                            translationY = rise.value * 12.dp.toPx()
                        },
                ) {
                    DaylightChip(daylight, palette)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.displaySmall,
                            color = palette.content,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        if (trailing != null) {
                            Spacer(Modifier.width(12.dp))
                            trailing()
                        }
                    }
                    if (intro != null) {
                        Text(
                            intro,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.content,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            // la fascia delle creste, che finisce sotto il foglio
            Spacer(Modifier.height(RidgeBand))
        }
        if (below != null) {
            Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                below()
            }
        }
    }
}

/**
 * Striscia sotto la barra di stato, nel colore della cima del cielo: compare ([show]) quando la testata è scorsa via,
 * così il contenuto non passa sotto l'ora e le icone, che restano quelle del cielo.
 */
@Composable
internal fun SkyStatusBarScrim(show: Boolean, modifier: Modifier = Modifier) {
    val top = skyPalette(rememberDaylight().phase).top
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (show) 1f else 0f)
    Spacer(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.safeDrawing)
            .graphicsLayer { this.alpha = alpha }
            .background(top),
    )
}

/** Un cielo pieno senza testo (foto mancanti, sfondi di marca): solo cielo e creste dell'ora. */
@Composable
internal fun SkyPicture(modifier: Modifier = Modifier, phase: SkyPhase = rememberDaylight().phase, sheet: Color? = null) {
    Box(modifier.skyBackdrop(skyPalette(phase), sheet = sheet))
}

// Profilo chiuso in basso: [top] dà la y in px per ogni t tra 0 e 1. Si chiude ben sotto il fondo (parallasse).
private fun ridge(w: Float, h: Float, step: Float, top: (Float) -> Float): Path = Path().apply {
    moveTo(-step, h + 40 * step)
    var x = -step
    while (x <= w + step) {
        lineTo(x, top(x / w))
        x += step
    }
    lineTo(w + step, h + 40 * step)
    close()
}

// Creste "ridged": 1 - |sin| ha punte verso l'alto, come le cime. Tre ottave, valore tra 0 e 1.
private fun peaks(t: Float, freq: Float, phase: Float): Float {
    fun r(f: Float, p: Float) = 1f - abs(sin(PI.toFloat() * f * t + p))
    return 0.62f * r(freq, phase) + 0.28f * r(freq * 2.3f, phase * 1.7f + 1f) + 0.10f * r(freq * 5.9f, phase + 2.3f)
}

// Fila di abeti lungo [base], a boschetti; [dp] = 1 dp in px. La massa sotto le chiome scende oltre il fondo.
private fun forest(w: Float, h: Float, base: Float, dp: Float): Path = Path().apply {
    fun y(x: Float) = base - 6 * dp * sin(2 * PI.toFloat() * (x / w * 1.4f + 0.3f))
    moveTo(-8 * dp, h + 200 * dp)
    var x = -8 * dp
    while (x <= w + 8 * dp) {
        lineTo(x, y(x))
        x += 4 * dp
    }
    lineTo(w + 8 * dp, h + 200 * dp)
    close()
    var i = 0
    x = -4 * dp
    while (x < w + 8 * dp) {
        val grove = 0.65f + 0.35f * sin(x / w * 11f + 0.8f)
        val tall = (12f + 18f * noise(i) * grove) * dp
        val half = tall * 0.26f
        val baseY = y(x)
        moveTo(x, baseY - tall)
        lineTo(x + half, baseY + 2 * dp)
        lineTo(x - half, baseY + 2 * dp)
        close()
        x += (5f + 6f * noise(i + 97)) * dp
        i++
    }
}

// Stelle ripetibili nel cielo sopra le creste
private fun stars(w: Float, maxY: Float): List<Pair<Offset, Float>> =
    List(26) { i -> Offset(noise(i * 3 + 1) * w, noise(i * 7 + 5) * maxY * 0.9f) to (0.6f + 1.1f * noise(i + 41)) }

// Pseudo-casuale ripetibile tra 0 e 1
private fun noise(i: Int): Float {
    val s = sin(i * 12.9898f) * 43758.547f
    return s - floor(s)
}
