// Componente che mostra l'attribuzione (autore e licenza) delle foto importate da fonti esterne, come quelle dei rifugi
// prese da Wikimedia Commons.
package it.unical.geosentinel_mobile.media.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.photo_credit
import org.jetbrains.compose.resources.stringResource
import it.unical.geosentinel_mobile.core.ui.AppIcons

/**
 * Mostra autore e licenza di una foto importata, come richiesto dalle licenze CC BY e CC BY-SA. Appare come una "i",
 * simile a quella dell'attribuzione della mappa, che al tocco apre un riquadro accanto con il testo. Non mostra nulla
 * se la foto non ha un credito (per esempio quelle caricate dagli utenti). Lo stato aperto/chiuso è legato a [credit]:
 * quando si passa a un'altra foto il riquadro si richiude.
 */
@Composable
fun PhotoCredit(credit: String?, modifier: Modifier = Modifier) {
    if (credit.isNullOrBlank()) return
    val colors = MaterialTheme.colorScheme
    var expanded by remember(credit) { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // L'area toccabile è di 48 dp (minimo per l'accessibilità), ma il cerchio disegnato è di 28 dp, così si nota
        // senza coprire troppo la foto.
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(colors.surfaceContainerLowest.copy(alpha = 0.94f), CircleShape)
                    .border(1.dp, colors.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Info, stringResource(Res.string.photo_credit), tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceContainerLowest.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Text(
                    credit,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
