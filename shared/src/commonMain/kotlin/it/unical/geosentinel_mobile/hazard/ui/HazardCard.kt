// Card che compare sopra la mappa quando l'utente tocca il simbolo di un pericolo.
package it.unical.geosentinel_mobile.hazard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.poi_close
import geosentinel_mobile.shared.generated.resources.poi_open_trail
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.media.ui.HazardPhotoStrip
import it.unical.geosentinel_mobile.trail.ui.color
import it.unical.geosentinel_mobile.trail.ui.label
import org.jetbrains.compose.resources.stringResource
import it.unical.geosentinel_mobile.core.ui.AppIcons

/**
 * Mostra il pericolo toccato sulla mappa: tipo, gravità scritta a parole, descrizione (al massimo tre righe), foto e,
 * se la segnalazione è legata a un sentiero, il collegamento per aprirlo. Ha la stessa struttura della card dei POI.
 * [onOpenTrail] è null quando non c'è un sentiero; [onClose] chiude la card. La card è una live region, così i lettori
 * di schermo annunciano il nuovo contenuto.
 */
@Composable
fun HazardCard(hazard: HazardResponse, onOpenTrail: (() -> Unit)?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 3.dp,
        modifier = modifier.widthIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            // Icona decorativa (contentDescription null): tipo e gravità sono già scritti accanto.
            Icon(
                AppIcons.Alert,
                contentDescription = null,
                tint = hazard.severity.color(),
                modifier = Modifier.padding(top = 12.dp).size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(stringResource(hazard.type.label()), style = MaterialTheme.typography.titleMedium)
                // Se è una propria segnalazione ancora in verifica lo si scrive a parole, perché sulla mappa è solo
                // attenuata.
                val severity = stringResource(hazard.severity.label())
                Text(
                    if (hazard.status == HazardResponse.Status.SUBMITTED) severity + " · " + stringResource(hazard.status.label()) else severity,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                hazard.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                HazardPhotoStrip(hazard.id, Modifier.padding(top = 8.dp))
                if (onOpenTrail != null) {
                    TextButton(onClick = onOpenTrail, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.poi_open_trail), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.Close, contentDescription = stringResource(Res.string.poi_close))
            }
        }
    }
}
