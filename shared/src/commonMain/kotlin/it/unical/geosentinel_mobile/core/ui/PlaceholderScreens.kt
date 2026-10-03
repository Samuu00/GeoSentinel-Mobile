package it.unical.geosentinel_mobile.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.back
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Schermate provvisorie di M0.4: ognuna sparisce con la milestone che la implementa.
// Ogni schermata ha uno sfondo pieno: durante le transizioni non deve trasparire quella sotto.
// Le testate (ForestHeader) arrivano sotto la barra di stato; il resto del contenuto parte sotto (safeDrawing, Top).

private val ContentMaxWidth = 600.dp

// Stato vuoto: il paesaggio in testata, poi titolo "da cartello" e testo, allineati a sinistra sul "terreno".
// emergency = true solo per la schermata SOS: niente paesaggio, simbolo rosso (l'unico rosso oltre al pulsante).
@Composable
fun PlaceholderScreen(
    icon: ImageVector,
    title: StringResource,
    body: StringResource,
    emergency: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (emergency) {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(88.dp))
                Icon(icon, contentDescription = null, tint = EmergencyRed, modifier = Modifier.padding(horizontal = 28.dp).size(56.dp))
                Spacer(Modifier.size(20.dp))
            } else {
                ForestHeader(Modifier.fillMaxWidth().height(340.dp))
            }
            Column(Modifier.padding(horizontal = 28.dp).widthIn(max = 480.dp)) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(32.dp))
            }
        }
        if (onBack != null) BackButton(onBack, Modifier.align(Alignment.TopStart))
    }
}

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
