package it.unical.geosentinel_mobile.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.trail_retry
import org.jetbrains.compose.resources.stringResource

// Stati condivisi delle pagine (redesign, 11/2026): vuoto, errore, caricamento. Prima ogni schermata aveva il suo
// Message privato, con margini e colori un po' diversi.

/**
 * Curve di livello disegnate dietro al contenuto: l'elemento firma dell'app (titoli grandi, stati vuoti, biglietto
 * della prenotazione). [strength] è quanto l'inchiostro si stacca dal fondo [over]: 0.06–0.12, mai di più (è trama).
 */
@Composable
internal fun Modifier.contours(over: Color, strength: Float = 0.08f, cell: Dp = 7.dp): Modifier {
    val ink = MaterialTheme.colorScheme.primary
    return drawWithCache {
        val (minor, major) = contourPaths(size.width, size.height, cell.toPx())
        val thin = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
        val thick = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        onDrawBehind {
            drawPath(minor, lerp(over, ink, strength), style = thin)
            drawPath(major, lerp(over, ink, strength * 1.8f), style = thick)
        }
    }
}

/** Il tondo muschio con le curve di livello: il "disegno" degli stati vuoti e d'errore. Decorativo. */
@Composable
internal fun ContourBadge(size: Dp = 96.dp) {
    val fill = MaterialTheme.colorScheme.primaryContainer
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .contours(over = fill, strength = 0.18f, cell = 5.dp)
            .clearAndSetSemantics { },
    )
}

/** Stato vuoto: disegno, titolo, una riga di spiegazione e al massimo un'azione. */
@Composable
internal fun EmptyState(title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        ContourBadge()
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/**
 * Errore che lascia la pagina (o una sezione, con [compact]) senza contenuto: il messaggio, annunciato dagli screen
 * reader, e "Riprova".
 */
@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 32.dp, vertical = if (compact) 16.dp else 48.dp),
    ) {
        if (!compact) {
            ContourBadge()
            Spacer(Modifier.height(24.dp))
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        if (compact) {
            TonalPillButton(stringResource(Res.string.trail_retry), onClick = onRetry)
        } else {
            PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = onRetry, modifier = Modifier.widthIn(max = 240.dp))
        }
    }
}

/** Spinner piccolo per "altre pagine" in fondo a una lista e per le sezioni che si caricano a parte. */
@Composable
internal fun LoadingMore() {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/**
 * Sagoma di caricamento: un blocco tonale che "respira" (0,5 ↔ 1 in 900 ms), fermo con "Riduci movimento".
 * Decorativa: chi usa le sagome annuncia il caricamento una volta sola ([SkeletonRows] con loadingLabel).
 */
@Composable
internal fun Modifier.skeleton(shape: Shape = MaterialTheme.shapes.extraSmall): Modifier {
    val still = rememberReduceMotion()
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    val alpha = if (still) {
        0.8f
    } else {
        val pulse by rememberInfiniteTransition().animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        )
        pulse
    }
    return this.graphicsLayer { this.alpha = alpha }.clip(shape).background(color)
}

/** Righe finte di una lista raggruppata mentre arriva la prima pagina: miniatura (se [thumb]) e due righe di testo. */
@Composable
internal fun SkeletonRows(count: Int = 5, thumb: Boolean = true, loadingLabel: String? = null) {
    GroupCard(
        Modifier
            .widthIn(max = PageMaxWidth)
            .padding(horizontal = 20.dp)
            .clearAndSetSemantics { if (loadingLabel != null) contentDescription = loadingLabel },
    ) {
        repeat(count) { index ->
            if (index > 0) GroupDivider(if (thumb) 104.dp else 16.dp)
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (thumb) {
                    Box(Modifier.size(72.dp).skeleton(MaterialTheme.shapes.small))
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(16.dp).skeleton())
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.45f).height(12.dp).skeleton())
                }
            }
        }
    }
}
