package it.unical.geosentinel_mobile.core.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import it.unical.geosentinel_mobile.core.ui.theme.dawnColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Testata delle schermate: un paesaggio a strati di carta, dal cielo al bosco.
 * - cielo color muschio con le curve di livello appena accennate (la carta topografica);
 * - due creste, più chiare quanto più sono lontane (prospettiva aerea);
 * - una fascia fitta di abeti, il colore più scuro;
 * - in basso il "terreno", dello stesso colore dello sfondo: il contenuto sotto ci poggia sopra, senza bordo.
 * In chiaro gli strati scuriscono verso il verde abete; in scuro verso il nero del bosco di notte.
 * Disegnata con i colori del tema e deterministica (nessun valore casuale vero). Decorativa.
 * Va messa in cima alla schermata, anche sotto la barra di stato: lì c'è solo cielo.
 */
@Composable
fun ForestHeader(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val sky = scheme.primaryContainer
    val ground = scheme.background
    val dark = ground.luminance() < 0.5f
    val ink = if (dark) lerp(ground, Color.Black, 0.45f) else scheme.primary
    Spacer(
        modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val (minor, major) = contourPaths(w, h, 6.dp.toPx())
                val step = 3.dp.toPx()
                // cresta lontana: cime aguzze e alte
                val far = ridge(w, h, step) { t -> 0.60f - 0.34f * peaks(t, 3.1f, 0.6f) }
                // cresta vicina: più bassa e morbida
                val near = ridge(w, h, step) { t -> 0.74f - 0.15f * peaks(t, 2.2f, 2.4f) }
                val forest = forest(w, h, 1.dp.toPx())
                // terreno: una linea appena ondulata che copre la base degli alberi
                val land = ridge(w, h, step) { t -> 0.92f - 0.025f * sin(2 * PI.toFloat() * (t * 1.3f + 0.2f)) }
                val thin = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                val thick = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                onDrawBehind {
                    drawRect(sky)
                    drawPath(minor, lerp(sky, ink, 0.10f), style = thin)
                    drawPath(major, lerp(sky, ink, 0.20f), style = thick)
                    drawPath(far, lerp(sky, ink, 0.22f))
                    drawPath(near, lerp(sky, ink, 0.45f))
                    drawPath(forest, ink)
                    drawPath(land, ground)
                }
            },
    )
}

/**
 * Il fondo della testata delle schede (redesign 11/2026, terza passata): il cielo d'alba che sfuma nella nebbia del
 * fondo, due creste lontane e una fila di abeti, tutto in toni appena più scuri del fondo. Il titolo grande ci sta
 * sopra e resta leggibile (le creste sono velature). In basso il disegno finisce nel colore del fondo: il contenuto
 * ci poggia sopra senza bordo. Decorativo e deterministico.
 */
// [height]: il paesaggio occupa solo questa fascia in alto (sopra il titolo); sotto resta il fondo. null = tutto.
@Composable
internal fun Modifier.alpineBackdrop(height: androidx.compose.ui.unit.Dp? = null): Modifier {
    val scheme = MaterialTheme.colorScheme
    val ground = scheme.background
    val dawn = dawnColor()
    val dark = ground.luminance() < 0.5f
    // di notte le creste scuriscono verso il nero, di giorno si velano di abete
    val ink = if (dark) lerp(ground, Color.Black, 0.6f) else scheme.primary
    return this
        .clearAndSetSemantics { }
        .drawWithCache {
            val w = size.width
            val h = height?.toPx()?.coerceAtMost(size.height) ?: size.height
            val step = 3.dp.toPx()
            val (minor, _) = contourPaths(w, h, 8.dp.toPx())
            val far = ridge(w, h, step) { t -> 0.50f - 0.30f * peaks(t, 2.6f, 0.9f) }
            val near = ridge(w, h, step) { t -> 0.70f - 0.16f * peaks(t, 1.8f, 2.1f) }
            val forest = forest(w, h, 0.8f.dp.toPx())
            val land = ridge(w, h, step) { t -> 0.93f - 0.02f * sin(2 * PI.toFloat() * (t * 1.1f + 0.4f)) }
            val sky = Brush.verticalGradient(
                0f to lerp(ground, dawn, if (dark) 0.85f else 0.60f),
                0.55f to lerp(ground, dawn, if (dark) 0.35f else 0.40f),
                1f to ground,
                endY = h,
            )
            val thin = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            onDrawBehind {
                drawRect(sky, size = androidx.compose.ui.geometry.Size(w, h))
                drawPath(minor, lerp(ground, ink, 0.05f), style = thin)
                drawPath(far, lerp(ground, ink, if (dark) 0.30f else 0.07f))
                drawPath(near, lerp(ground, ink, if (dark) 0.50f else 0.12f))
                drawPath(forest, lerp(ground, ink, if (dark) 0.70f else 0.17f))
                drawPath(land, ground)
            }
        }
}

// Profilo chiuso in basso: [top] dà l'altezza (frazione del riquadro, 0 = in alto) per ogni t tra 0 e 1.
// Si chiude un po' sotto il fondo: un bordo esattamente sull'ultima riga di pixel, sfumato dall'antialiasing,
// lascerebbe intravedere gli strati sotto.
private fun ridge(w: Float, h: Float, step: Float, top: (Float) -> Float): Path = Path().apply {
    moveTo(0f, h + step)
    var x = 0f
    while (x <= w + step) {
        lineTo(x, top(x / w) * h)
        x += step
    }
    lineTo(w + step, h + step)
    close()
}

// Creste "ridged": 1 - |sin| ha punte verso l'alto, come le cime. Tre ottave, valore tra 0 e 1.
private fun peaks(t: Float, freq: Float, phase: Float): Float {
    fun r(f: Float, p: Float) = 1f - abs(sin(PI.toFloat() * f * t + p))
    return 0.62f * r(freq, phase) + 0.28f * r(freq * 2.3f, phase * 1.7f + 1f) + 0.10f * r(freq * 5.9f, phase + 2.3f)
}

// Fascia di abeti lungo una collina: alberi a passo e altezza variabili, a boschetti, ognuno con due palchi.
// [dp] = 1 dp in px, così gli alberi hanno la stessa misura su ogni schermo.
private fun forest(w: Float, h: Float, dp: Float): Path = Path().apply {
    fun base(x: Float) = h * (0.86f - 0.035f * sin(2 * PI.toFloat() * (x / w * 1.6f + 0.35f)))
    // la massa del bosco sotto le chiome
    moveTo(0f, h)
    var x = 0f
    while (x <= w) {
        lineTo(x, base(x))
        x += 4 * dp
    }
    lineTo(w, base(w))
    lineTo(w, h)
    close()
    var i = 0
    x = -4 * dp
    while (x < w + 8 * dp) {
        // boschetti: alberi più alti dove l'onda è alta, più bassi nelle radure
        val grove = 0.65f + 0.35f * sin(x / w * 11f + 0.8f)
        val tall = (14f + 22f * noise(i) * grove) * dp
        val half = tall * 0.24f
        val y = base(x)
        // palco basso, largo
        moveTo(x, y - tall * 0.68f)
        lineTo(x + half, y + 2 * dp)
        lineTo(x - half, y + 2 * dp)
        close()
        // palco alto, stretto fino alla punta
        moveTo(x, y - tall)
        lineTo(x + half * 0.72f, y - tall * 0.34f)
        lineTo(x - half * 0.72f, y - tall * 0.34f)
        close()
        x += (5f + 6f * noise(i + 97)) * dp
        i++
    }
}

// Pseudo-casuale ripetibile tra 0 e 1: lo stesso bosco a ogni disegno
private fun noise(i: Int): Float {
    val s = sin(i * 12.9898f) * 43758.547f
    return s - floor(s)
}
