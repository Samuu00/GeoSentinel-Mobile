package it.unical.geosentinel_mobile.core.ui

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

class ContourLinesTest {

    // Cono con la cima al centro: la quota 0,5 è il cerchio di raggio 0,25
    private val cone = { x: Float, y: Float -> 1f - 2f * hypot(x - 0.5f, y - 0.5f) }

    @Test
    fun `la curva di un cono e un cerchio attorno alla cima`() {
        val segments = contourSegments(40, 40, listOf(0.5f), cone)
        assertTrue(segments.size > 40)
        segments.flatMap { listOf(it.from, it.to) }.forEach { p ->
            assertTrue(abs(hypot(p.x - 0.5f, p.y - 0.5f) - 0.25f) < 0.01f, "punto fuori dal cerchio")
        }
    }

    @Test
    fun `una quota sopra la cima non da curve`() {
        assertTrue(contourSegments(40, 40, listOf(1.5f), cone).isEmpty())
    }
}
