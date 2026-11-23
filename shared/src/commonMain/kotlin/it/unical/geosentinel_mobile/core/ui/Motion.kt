// Animazioni comuni dell'interfaccia.
// Il movimento è usato poco e sempre con uno scopo: le card entrano in sequenza quando compare una lista e
// gli elementi toccabili si rimpiccioliscono leggermente sotto il dito. Se l'utente ha chiesto di ridurre il
// movimento (vedi `rememberReduceMotion`), gli elementi compaiono senza animazione.
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

/**
 * Riduce l'elemento al 97% mentre è premuto e lo riporta alla dimensione normale con un'animazione a molla.
 * Si applica a card e pulsanti che hanno il proprio [interactionSource], da cui si legge lo stato di pressione.
 * La scala è applicata in `graphicsLayer`, quindi non provoca un nuovo layout.
 */
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
 * Ingresso in sequenza della card in posizione [index] di una lista: la card sale di 16 dp e diventa visibile
 * in 380 ms, partendo 45 ms dopo la precedente. Il ritardo cresce solo per le prime 8 card; le successive
 * compaiono scorrendo e partono senza attendere. L'animazione avviene una sola volta per card.
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
