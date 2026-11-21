package it.unical.geosentinel_mobile.trail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.route_close
import geosentinel_mobile.shared.generated.resources.route_from
import geosentinel_mobile.shared.generated.resources.route_from_map
import geosentinel_mobile.shared.generated.resources.route_from_me
import geosentinel_mobile.shared.generated.resources.route_loading
import geosentinel_mobile.shared.generated.resources.route_summary
import geosentinel_mobile.shared.generated.resources.route_to
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.core.ui.islandShadow
import it.unical.geosentinel_mobile.core.ui.resolve
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Il pannello del percorso A→B, un'isola come la card dei POI (Figma "Luce di quota"): "Da" con la scelta tra la
 * posizione dell'utente e il centro della mappa, "A" con la destinazione, e sotto lunghezza e sentieri, l'attesa o
 * l'errore. La X chiude il percorso.
 */
@Composable
internal fun RoutePanel(plan: RoutePlan, onOrigin: (RouteOrigin) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        // i tocchi sul pannello (anche tra le righe) non arrivano alla mappa sotto: aprivano il sentiero toccato
        modifier = modifier.widthIn(max = 560.dp).islandShadow(MaterialTheme.shapes.large).pointerInput(Unit) { detectTapGestures() },
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(Res.string.route_from)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    RouteOrigin.entries.forEach { origin ->
                        FilterChip(
                            selected = plan.origin == origin,
                            onClick = { onOrigin(origin) },
                            label = { Text(stringResource(if (origin == RouteOrigin.MyLocation) Res.string.route_from_me else Res.string.route_from_map), maxLines = 1) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(Res.string.route_to)
                Text(plan.destination.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                // la X di iOS, come nella card dei POI: tondo grigio da 30 dentro un bersaglio da 48
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(30.dp).background(colors.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Close, contentDescription = stringResource(Res.string.route_close), tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            // l'esito si annuncia da solo (cerco, trovato, errore)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                when (val result = plan.result) {
                    RouteResult.WaitingForOrigin, RouteResult.Loading -> {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(Res.string.route_loading), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    is RouteResult.Found -> {
                        // decorativa: il testo dice già che è un percorso
                        Icon(AppIcons.Route, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            pluralStringResource(Res.plurals.route_summary, result.trailCount, formatKm(result.lengthM), result.trailCount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    is RouteResult.Failed -> Text(result.message.resolve(), style = MaterialTheme.typography.bodyMedium, color = colors.error)
                }
            }
        }
    }
}

@Composable
private fun Label(text: org.jetbrains.compose.resources.StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(32.dp),
    )
}
