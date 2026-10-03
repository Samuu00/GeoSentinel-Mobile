package it.unical.geosentinel_mobile.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

// Curve di livello come su una carta escursionistica: la "trama" del cielo nella testata (ForestHeader).
// Un piccolo rilievo inventato (tre cime e un'ondulazione), tagliato a quote costanti con marching squares:
// le curve non si incrociano mai, come sulle carte vere, e una ogni cinque (la "direttrice") è più marcata.

/** Curve per un riquadro [width] x [height] in px, con una cella ogni [cell] px: (normali, direttrici). */
internal fun contourPaths(width: Float, height: Float, cell: Float): Pair<Path, Path> {
    val unit = max(1f, minOf(width, height))
    val sx = width / unit
    val sy = height / unit
    val cols = (width / cell).toInt().coerceAtLeast(8)
    val rows = (height / cell).toInt().coerceAtLeast(8)
    val minor = Path()
    val major = Path()
    contourSegments(cols, rows, Levels) { x, y -> terrain(x * sx, y * sy, sx, sy) }.forEach { s ->
        val path = if (s.level % 5 == 4) major else minor
        path.moveTo(s.from.x * width, s.from.y * height)
        path.lineTo(s.to.x * width, s.to.y * height)
    }
    return minor to major
}

// Quote delle curve: 18 livelli tra le valli e le cime
private val Levels = List(18) { 0.07f + it * 0.055f }

// Rilievo in coordinate con la stessa unità sui due assi (il lato corto vale 1), così le cime restano tonde.
// Le cime sono posizionate in frazioni del riquadro: la principale in alto a destra, lontana dal testo.
private fun terrain(x: Float, y: Float, w: Float, h: Float): Float {
    fun peak(fx: Float, fy: Float, height: Float, spread: Float): Float {
        val dx = x - fx * w
        val dy = y - fy * h
        return height * exp(-(dx * dx + dy * dy) / (2 * spread * spread))
    }
    return peak(0.82f, 0.22f, 1.0f, 0.42f) +
        peak(0.12f, 0.02f, 0.62f, 0.30f) +
        peak(0.48f, 1.05f, 0.45f, 0.48f) +
        0.05f * sin(5.1f * x + 1.3f) * cos(3.7f * y + 0.4f)
}

internal data class ContourSegment(val level: Int, val from: Offset, val to: Offset)

/**
 * Marching squares su una griglia di [cols] x [rows] celle nel quadrato unitario: per ogni quota di [levels]
 * restituisce i segmenti (in coordinate 0..1) dove [field] attraversa quella quota.
 * Nei punti di sella (quattro attraversamenti) unisce i lati in ordine: sopra-destra, sotto-sinistra.
 */
internal fun contourSegments(cols: Int, rows: Int, levels: List<Float>, field: (Float, Float) -> Float): List<ContourSegment> {
    val values = Array(rows + 1) { j -> FloatArray(cols + 1) { i -> field(i.toFloat() / cols, j.toFloat() / rows) } }
    val segments = mutableListOf<ContourSegment>()
    val crossings = ArrayList<Offset>(4)
    for (j in 0 until rows) {
        for (i in 0 until cols) {
            val x0 = i.toFloat() / cols
            val x1 = (i + 1).toFloat() / cols
            val y0 = j.toFloat() / rows
            val y1 = (j + 1).toFloat() / rows
            val tl = values[j][i]
            val tr = values[j][i + 1]
            val br = values[j + 1][i + 1]
            val bl = values[j + 1][i]
            levels.forEachIndexed { level, q ->
                crossings.clear()
                // lati in ordine: sopra, destra, sotto, sinistra
                if ((tl >= q) != (tr >= q)) crossings += Offset(lerp(x0, x1, (q - tl) / (tr - tl)), y0)
                if ((tr >= q) != (br >= q)) crossings += Offset(x1, lerp(y0, y1, (q - tr) / (br - tr)))
                if ((bl >= q) != (br >= q)) crossings += Offset(lerp(x0, x1, (q - bl) / (br - bl)), y1)
                if ((tl >= q) != (bl >= q)) crossings += Offset(x0, lerp(y0, y1, (q - tl) / (bl - tl)))
                if (crossings.size >= 2) segments += ContourSegment(level, crossings[0], crossings[1])
                if (crossings.size == 4) segments += ContourSegment(level, crossings[2], crossings[3])
            }
        }
    }
    return segments
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
