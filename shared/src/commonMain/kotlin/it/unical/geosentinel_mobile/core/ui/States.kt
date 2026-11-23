// Componenti condivisi per gli stati di una pagina: vuoto, errore e caricamento.
// Tutte le schermate li usano, così questi casi hanno lo stesso aspetto e lo stesso comportamento in tutta l'app.
// Al posto di un'illustrazione, gli stati vuoti e di errore mostrano un piccolo cielo dell'ora corrente.
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * Tondo di diametro [size] con il cielo dell'ora corrente e le creste dei monti: è l'illustrazione degli stati
 * vuoti e di errore. È solo decorativo, quindi viene nascosto ai lettori di schermo.
 */
@Composable
internal fun SkyBadge(size: Dp = 112.dp) {
    SkyPicture(Modifier.size(size).clip(CircleShape).clearAndSetSemantics { })
}

/**
 * Stato vuoto di una pagina o di una lista: illustrazione, [title] (annunciato come intestazione), una riga di
 * spiegazione facoltativa ([body]) e al massimo un'azione ([action], per esempio un pulsante).
 */
@Composable
internal fun EmptyState(title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        SkyBadge()
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
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
 * Errore che lascia una pagina senza contenuto: mostra [message] e il pulsante "Riprova", che chiama [onRetry].
 * Il messaggio è una live region, quindi i lettori di schermo lo annunciano appena compare.
 *
 * @param compact se vero la versione è ridotta, senza illustrazione e con un pulsante tonale, adatta a una sola
 *   sezione della pagina.
 */
@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 32.dp, vertical = if (compact) 16.dp else 48.dp),
    ) {
        if (!compact) {
            SkyBadge()
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

/** Piccolo indicatore di avanzamento, per le pagine successive in fondo a una lista e per le sezioni caricate a parte. */
@Composable
internal fun LoadingMore() {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/**
 * Trasforma l'elemento in una sagoma di caricamento: un blocco colorato con forma [shape] la cui opacità pulsa tra
 * 0,5 e 1 ogni 900 ms. Con "Riduci movimento" la sagoma resta ferma all'80%.
 * È decorativa: il caricamento lo annuncia una sola volta chi usa le sagome (per esempio [SkeletonRows] con
 * `loadingLabel`).
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

/**
 * Righe segnaposto di una lista raggruppata, mostrate mentre arriva la prima pagina di dati.
 *
 * @param count numero di righe.
 * @param thumb se vero ogni riga ha una miniatura a sinistra, oltre alle due righe di testo.
 * @param loadingLabel testo letto dai lettori di schermo al posto delle sagome (per esempio "Caricamento").
 */
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
