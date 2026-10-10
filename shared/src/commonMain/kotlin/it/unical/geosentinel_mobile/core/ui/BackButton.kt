package it.unical.geosentinel_mobile.core.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.back
import org.jetbrains.compose.resources.stringResource

// Indietro delle schermate "spinte": un tondo da 48 dp con la freccia, in alto a sinistra sotto la barra di stato.
// Su iPhone non c'è un tasto di sistema; su Android il gesto Indietro funziona comunque.
// Sta sopra la testata, quindi ha un fondo pieno e un filo di bordo per staccarsi dal cielo.
@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.back)
    Surface(
        onClick = onBack,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(start = 16.dp, top = 8.dp)
            .size(48.dp)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}
