// Il cielo disegnato nelle testate delle pagine, elemento caratteristico dell'interfaccia.
// Le testate mostrano il cielo dell'ora reale (giorno, luce dorata, notte) sopra un profilo di creste montuose e
// dicono quanta luce resta prima del tramonto, un'informazione di sicurezza per chi è in montagna. Il contenuto
// della pagina sale sopra le creste come un foglio. Tutto è disegnato in codice con i Path di Compose, senza immagini.
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

/**
 * I colori di un cielo. Il cielo è decorativo, ma il testo sopra deve restare leggibile: per ogni fase [content] è
 * scelto con un contrasto di almeno 5:1 sulla fascia del testo.
 *
 * @property top colore in cima al cielo; insieme a [upper] forma la fascia su cui sta il testo.
 * @property horizon il bagliore all'orizzonte, dietro le creste.
 * @property farRidge creste lontane.
 * @property nearRidge creste vicine.
 * @property forest la fila di abeti in primo piano.
 * @property chip fondo velato del chip della luce.
 * @property stars vero se vanno disegnate le stelle.
 */
@Immutable
internal class SkyPalette(
    val top: Color,
    val upper: Color,
    val horizon: Color,
    val farRidge: Color,
    val nearRidge: Color,
    val forest: Color,
    // Colore di testo e icone sopra il cielo.
    val content: Color,
    val chip: Color,
    // Vero se il cielo è scuro e le icone della barra di stato vanno quindi chiare.
    val dark: Boolean,
    val stars: Boolean = false,
)

/** Restituisce i colori del cielo per la fase indicata. */
internal fun skyPalette(phase: SkyPhase): SkyPalette = when (phase) {
    // Testo scuro sul celeste: contrasto 9:1 in cima e 10:1 sulla fascia del testo.
    SkyPhase.DAY -> SkyPalette(
        top = Color(0xFF8EBFE3), upper = Color(0xFFA9D0EA), horizon = Color(0xFFDCEBF2),
        farRidge = Color(0xFFA9C0C8), nearRidge = Color(0xFF7E9AA0), forest = Color(0xFF1F4436),
        content = Color(0xFF0E1B16), chip = Color.White.copy(alpha = 0.6f), dark = false,
    )
    // Testo bianco sul blu della sera: 7,3:1 in cima e 5,2:1 in fondo alla fascia del testo.
    SkyPhase.GOLDEN -> SkyPalette(
        top = Color(0xFF3E4F88), upper = Color(0xFF5E6AA0), horizon = Color(0xFFF0BE96),
        farRidge = Color(0xFF9C8DB0), nearRidge = Color(0xFF6A6888), forest = Color(0xFF22352F),
        content = Color.White, chip = Color.White.copy(alpha = 0.18f), dark = true,
    )
    // Notte: testo bianco su blu molto scuro, con le stelle.
    SkyPhase.NIGHT -> SkyPalette(
        top = Color(0xFF071120), upper = Color(0xFF13293F), horizon = Color(0xFF1C3549),
        farRidge = Color(0xFF1C3145), nearRidge = Color(0xFF13232F), forest = Color(0xFF0A1511),
        content = Color.White, chip = Color.White.copy(alpha = 0.12f), dark = true, stars = true,
    )
}

/**
 * La luce attuale nel parco, calcolata nel centro dell'area predefinita: per disegnare un cielo non vale la pena
 * chiedere il permesso di posizione.
 *
 * Limite noto: il valore si calcola quando la pagina entra nella composizione e non si aggiorna ogni minuto, quindi
 * una pagina lasciata aperta per ore mostra la luce del momento in cui è stata aperta. Se servisse, qui andrebbe un
 * aggiornamento periodico.
 */
@Composable
internal fun rememberDaylight(): Daylight = remember { daylightAt(Clock.System.now(), DefaultArea.lat, DefaultArea.lon) }

/** L'ora locale del dispositivo nel formato "17:02" (ore senza zero iniziale, minuti a due cifre). */
private fun Instant.clock(): String {
    val time = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${time.hour}:${time.minute.toString().padStart(2, '0')}"
}

/**
 * Il testo del chip della luce: di giorno l'ora del tramonto e la luce che resta (per esempio
 * "Tramonto 17:02 · 2 h 11 min di luce"), dopo il tramonto l'ora dell'alba ("Alba alle 6:58").
 * Restituisce null se non ci sono alba o tramonto da mostrare.
 */
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

/** Versione corta del testo per i chip sopra la mappa: "Tramonto 17:02" di giorno, "Alba 6:58" di notte; null se non c'è. */
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

/**
 * Il chip della luce: una capsula velata sopra il cielo con l'icona del tramonto (o della luna, dopo il tramonto)
 * e il testo di [daylightLabel]. Se non c'è testo il chip non viene mostrato.
 */
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

// Di quanto il foglio del contenuto, con i suoi angoli arrotondati, copre il fondo del cielo.
internal val SheetOverlap = 28.dp

// Altezza della fascia delle creste in fondo al cielo.
internal val RidgeBand = 132.dp

/**
 * Disegna il cielo con le creste dietro l'elemento (in genere alto quanto la testata).
 *
 * I tracciati si calcolano una volta per dimensione (`drawWithCache`); a ogni frame si applicano solo gli
 * spostamenti, letti da funzioni per non provocare una nuova composizione.
 *
 * @param parallax di quanti pixel la testata è salita scorrendo: le creste scendono più lentamente del contenuto,
 *   e gli strati più lontani ancora più piano, per dare profondità.
 * @param rise valore da 0 a 1 per l'animazione d'ingresso, in cui le creste salgono dal basso (1 = ancora abbassate).
 * @param sheet il colore del foglio che copre il fondo con gli angoli arrotondati; null se non c'è il foglio.
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
 * La testata delle pagine principali: il cielo dell'ora, il chip della luce, il titolo grande in Archivo e, sotto
 * le creste, l'inizio del foglio su cui sta il contenuto. All'ingresso le creste salgono con un'animazione a molla
 * (assente con "Riduci movimento"); scorrendo, gli strati si muovono con un effetto di parallasse.
 *
 * @param intro una riga facoltativa sotto il titolo.
 * @param trailing un elemento facoltativo a destra del titolo.
 * @param below contenuto (segmenti, link) che sta già sul foglio, sotto le creste.
 * @param topBar se vero lascia spazio in alto per la barra con Indietro e le azioni, che disegna chi usa la testata.
 * @param daylight la luce da rappresentare; di solito quella attuale.
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
                    // Quanto è salita la testata rispetto alla posizione in cui è comparsa.
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
            // Spazio per la fascia delle creste, che termina sotto il foglio.
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
 * Striscia alta quanto la barra di stato, nel colore della cima del cielo. Compare in dissolvenza quando [show] è
 * vero, cioè quando la testata è scorsa via: così il contenuto non passa sotto l'ora e le icone di sistema, il cui
 * colore resta quello scelto per il cielo.
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

/**
 * Solo il cielo e le creste dell'ora, senza testo: si usa al posto delle foto mancanti e come illustrazione.
 * [phase] permette di scegliere una fase diversa da quella attuale; [sheet] come in [skyBackdrop].
 */
@Composable
internal fun SkyPicture(modifier: Modifier = Modifier, phase: SkyPhase = rememberDaylight().phase, sheet: Color? = null) {
    Box(modifier.skyBackdrop(skyPalette(phase), sheet = sheet))
}

/**
 * Costruisce il profilo di una cresta come poligono chiuso: per ogni posizione orizzontale, campionata ogni [step]
 * pixel, [top] restituisce l'altezza (y in pixel) in funzione di t, la posizione normalizzata tra 0 e 1. Il poligono
 * si chiude molto sotto il fondo, così spostando la cresta verso il basso con la parallasse non compare un vuoto.
 */
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

/**
 * Funzione che dà la forma alle creste, con valori tra 0 e 1. La funzione 1 - |sin| ha punte verso l'alto, simili
 * a cime; si sommano tre ottave con frequenze crescenti e pesi decrescenti per avere dettagli di diversa scala.
 * [freq] e [phase] cambiano la forma, così le due file di creste non sono uguali.
 */
private fun peaks(t: Float, freq: Float, phase: Float): Float {
    /** Una singola ottava: punte verso l'alto con frequenza f e sfasamento p. */
    fun r(f: Float, p: Float) = 1f - abs(sin(PI.toFloat() * f * t + p))
    return 0.62f * r(freq, phase) + 0.28f * r(freq * 2.3f, phase * 1.7f + 1f) + 0.10f * r(freq * 5.9f, phase + 2.3f)
}

/**
 * Disegna la fila di abeti in primo piano lungo una linea ondulata attorno all'altezza [base]. Ogni abete è un
 * triangolo con altezza pseudo-casuale, modulata per formare boschetti più alti e radure più basse. [dp] è la
 * misura di 1 dp in pixel. La massa scura sotto le chiome scende oltre il fondo, per lo stesso motivo di [ridge].
 */
private fun forest(w: Float, h: Float, base: Float, dp: Float): Path = Path().apply {
    /** Altezza della linea del bosco nel punto x: un'onda lenta di 6 dp attorno a base. */
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

/**
 * Le 26 stelle del cielo notturno, sopra le creste ([maxY] è il bordo superiore della fascia delle creste): per
 * ciascuna posizione e raggio. Sono pseudo-casuali ma sempre uguali, così non cambiano a ogni disegno.
 */
private fun stars(w: Float, maxY: Float): List<Pair<Offset, Float>> =
    List(26) { i -> Offset(noise(i * 3 + 1) * w, noise(i * 7 + 5) * maxY * 0.9f) to (0.6f + 1.1f * noise(i + 41)) }

/**
 * Numero pseudo-casuale tra 0 e 1 che dipende solo da [i]: la parte frazionaria di un seno moltiplicato per una
 * costante grande, un metodo classico degli shader. È ripetibile, quindi il disegno è sempre lo stesso.
 */
private fun noise(i: Int): Float {
    val s = sin(i * 12.9898f) * 43758.547f
    return s - floor(s)
}
