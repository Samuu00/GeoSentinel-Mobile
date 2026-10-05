package it.unical.geosentinel_mobile.hazard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.hazards_distance
import geosentinel_mobile.shared.generated.resources.hazards_loading
import geosentinel_mobile.shared.generated.resources.hazards_nearby_empty
import geosentinel_mobile.shared.generated.resources.hazards_nearby_title
import geosentinel_mobile.shared.generated.resources.trail_retry
import geosentinel_mobile.shared.generated.resources.trails_nearby_waiting
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.distanceTo
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.trail.ui.color
import it.unical.geosentinel_mobile.trail.ui.label
import org.jetbrains.compose.resources.stringResource

/** Foglio "Pericoli vicini": mentre cerca la posizione e carica, poi la lista dal più vicino (ordine del server). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyHazardsSheet(nearby: NearbyHazards, onSelect: (HazardResponse) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(Res.string.hazards_nearby_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
            )
            Spacer(Modifier.size(12.dp))
            when (nearby) {
                NearbyHazards.WaitingForFix, NearbyHazards.Loading -> Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(if (nearby == NearbyHazards.WaitingForFix) Res.string.trails_nearby_waiting else Res.string.hazards_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is NearbyHazards.Failed -> Column(Modifier.padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(nearby.message.resolve(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.trail_retry), fontWeight = FontWeight.SemiBold)
                    }
                }
                is NearbyHazards.Loaded -> if (nearby.hazards.isEmpty()) {
                    Text(
                        stringResource(Res.string.hazards_nearby_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(nearby.hazards, key = { it.id }) { hazard ->
                            HazardRow(hazard, from = nearby.from, onClick = { onSelect(hazard) })
                            HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                NearbyHazards.Hidden -> Unit
            }
        }
    }
}

// Tipo, distanza e gravità a parole (il colore del triangolo non basta, §2). Il tocco centra la mappa sul pericolo
@Composable
private fun HazardRow(hazard: HazardResponse, from: GeoPoint, onClick: () -> Unit) {
    val distance = distanceLabel(from.distanceTo(hazard.location.toGeoPoint()))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // decorativa: la gravità è scritta sotto
        Icon(Icons.Filled.Warning, contentDescription = null, tint = hazard.severity.color(), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(hazard.type.label()), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.hazards_distance, distance) + " · " + stringResource(hazard.severity.label()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
