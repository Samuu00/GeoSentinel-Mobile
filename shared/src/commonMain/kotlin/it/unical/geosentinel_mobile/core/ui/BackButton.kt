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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
 * Indietro delle schermate "spinte": su iPhone non c'è un tasto di sistema; su Android il gesto Indietro funziona comunque.
 * Bersaglio da 48 dp, tondo visibile da 44.
 * - [floating] (default): un'isola sopra foto, mappa o contenuto, in alto a sinistra sotto la barra di stato (si
 *   posiziona da solo con la safe area);
 * - altrimenti: dentro una barra (LargeTitlePage), senza fondo e senza margini propri.
 */
@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier, floating: Boolean = true) {
    val label = stringResource(Res.string.back)
    val colors = MaterialTheme.colorScheme
    val placed = if (floating) {
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(start = 12.dp, top = 4.dp)
    } else {
        modifier.padding(start = 4.dp)
    }
    Box(
        placed
            .size(48.dp)
            // niente clip: taglierebbe l'ombra dell'isola; il ripple resta un tondo da 24
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
                .then(if (floating) Modifier.islandShadow(CircleShape).background(colors.surfaceContainerHigh, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(28.dp))
        }
    }
}
