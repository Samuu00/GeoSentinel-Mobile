package it.unical.geosentinel_mobile.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics

// Paesaggio piatto e minimale: montagne lontane, una collina, una fila di pini.
// Disegnato con i colori del tema, quindi cambia da solo tra chiaro e scuro. Puramente decorativo.
@Composable
fun ForestIllustration(modifier: Modifier = Modifier) {
    val sky = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        drawRect(sky)
        // montagne lontane
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.62f)
                lineTo(w * 0.16f, h * 0.40f)
                lineTo(w * 0.30f, h * 0.54f)
                lineTo(w * 0.50f, h * 0.24f)
                lineTo(w * 0.66f, h * 0.48f)
                lineTo(w * 0.82f, h * 0.34f)
                lineTo(w, h * 0.56f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            },
            ink.copy(alpha = 0.22f),
        )
        // collina
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.80f)
                quadraticTo(w * 0.30f, h * 0.60f, w * 0.60f, h * 0.76f)
                quadraticTo(w * 0.80f, h * 0.86f, w, h * 0.70f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            },
            ink.copy(alpha = 0.45f),
        )
        // pini in primo piano: (posizione orizzontale, altezza) in frazioni del riquadro
        Pines.forEach { (x, height) -> drawPine(x * w, h, height * h, ink) }
    }
}

private val Pines = listOf(0.05f to 0.44f, 0.13f to 0.30f, 0.21f to 0.52f, 0.79f to 0.38f, 0.88f to 0.56f, 0.96f to 0.34f)

private fun DrawScope.drawPine(cx: Float, base: Float, height: Float, color: Color) {
    val half = height * 0.28f
    // due palchi di chioma e il tronco
    drawPath(
        Path().apply {
            moveTo(cx, base - height)
            lineTo(cx + half * 0.75f, base - height * 0.45f)
            lineTo(cx - half * 0.75f, base - height * 0.45f)
            close()
        },
        color,
    )
    drawPath(
        Path().apply {
            moveTo(cx, base - height * 0.70f)
            lineTo(cx + half, base - height * 0.10f)
            lineTo(cx - half, base - height * 0.10f)
            close()
        },
        color,
    )
    drawRect(color, topLeft = Offset(cx - height * 0.03f, base - height * 0.12f), size = Size(height * 0.06f, height * 0.12f))
}
