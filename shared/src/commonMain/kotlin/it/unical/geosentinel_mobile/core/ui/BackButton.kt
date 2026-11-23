// Pulsante "Indietro" delle schermate aperte sopra le altre (dettagli, form).
// Su iPhone non esiste un tasto Indietro di sistema, quindi ogni schermata di questo tipo ha il proprio
// pulsante; su Android funziona in più anche il gesto Indietro del sistema.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.back
import org.jetbrains.compose.resources.stringResource

/**
 * Pulsante tondo con la freccia verso sinistra che chiama [onBack].
 *
 * L'area toccabile è di 48 dp (il minimo per l'accessibilità), il tondo visibile di 44 dp. Per i lettori di
 * schermo il pulsante si annuncia con l'etichetta "Indietro".
 *
 * @param floating se vero (predefinito) il tondo è una superficie quasi opaca con ombra, leggibile sopra foto,
 *   cielo e mappa; se falso si vede solo la freccia, adatta a una barra con sfondo pieno.
 * @param inBar se vero il pulsante sta in una barra che applica già il margine della safe area; se falso si
 *   posiziona da solo in alto a sinistra, sotto la barra di stato.
 */
@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier, floating: Boolean = true, inBar: Boolean = false) {
    val label = stringResource(Res.string.back)
    val colors = MaterialTheme.colorScheme
    val placed = if (inBar) {
        modifier
    } else {
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(start = 12.dp, top = 4.dp)
    }
    Box(
        placed
            .size(48.dp)
            // Niente clip sul contenitore, perché taglierebbe l'ombra: l'effetto ripple resta comunque un
            // cerchio di raggio 24 dp grazie a bounded = false.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Button,
                onClick = onBack,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .then(
                    if (floating) {
                        Modifier.islandShadow(CircleShape, 8.dp).background(colors.surfaceContainerHigh.copy(alpha = 0.92f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.ChevronLeft, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(24.dp))
        }
    }
}
