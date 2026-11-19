package it.unical.geosentinel_mobile.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Movimento del redesign "Luce di quota": poco e con un motivo. Le card entrano a cascata, quello che si tocca cede
// sotto il dito. Con "Riduci movimento" niente: le cose compaiono e basta.

/** Cede a 0,97 sotto il dito e torna con una molla: per card e pulsanti che hanno il loro [interactionSource]. */
@Composable
internal fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val still = rememberReduceMotion()
    val scale by animateFloatAsState(
        if (pressed && !still) 0.97f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Ingresso a cascata della card numero [index] di una lista: sale di 16 dp e compare, 45 ms dopo la precedente
 * (le prime 8; le altre arrivano scorrendo, senza attesa). Una volta sola per card.
 */
@Composable
internal fun Modifier.enterStagger(index: Int): Modifier {
    val still = rememberReduceMotion()
    val progress = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(45L * index.coerceIn(0, 8))
            progress.animateTo(1f, tween(380))
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 16.dp.toPx()
    }
}
