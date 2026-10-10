package it.unical.geosentinel_mobile.staff.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.staff_add_poi
import geosentinel_mobile.shared.generated.resources.staff_manage_title
import geosentinel_mobile.shared.generated.resources.staff_open_queue
import geosentinel_mobile.shared.generated.resources.staff_trail_status
import it.unical.geosentinel_mobile.api.model.TrailDetailResponse
import it.unical.geosentinel_mobile.api.model.UpdateTrailStatusRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPositions
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.hazard.ui.Choices
import it.unical.geosentinel_mobile.trail.ui.TrailState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Azioni del gestore nel dettaglio del sentiero (M8.1): stato del sentiero, coda delle segnalazioni e nuovo POI legato
 * al sentiero. Il ruolo decide solo se mostrarla: un 403 del server arriva come messaggio.
 */
@Composable
fun TrailManagementSection(trail: TrailDetailResponse, onOpenQueue: () -> Unit, onCreatePoi: (start: GeoPoint) -> Unit) {
    val viewModel = koinViewModel<TrailStatusViewModel>(key = "trail-status-${trail.id}") { parametersOf(trail.id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // lo stato chiesto si vede subito; con la risposta lo sostituisce quello della cache
    val selected = state.pending ?: TrailStatuses.firstOrNull { it.value == trail.status.value }

    Column(Modifier.padding(top = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.staff_manage_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            if (state.busy) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            stringResource(Res.string.staff_trail_status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        )
        Choices(
            TrailStatuses,
            selected = selected,
            label = { TrailState.of(it.value).label },
            onSelect = { if (it != selected) viewModel.change(it) },
            // il rosso solo per i sentieri chiusi (§2)
            emergency = { it == UpdateTrailStatusRequest.Status.CLOSED },
        )
        state.error?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.size(4.dp))
        ManageLink(stringResource(Res.string.staff_open_queue), onOpenQueue)
        // il form parte dall'inizio del sentiero, come la segnalazione
        trail.path.toPositions().firstOrNull()?.let { start ->
            ManageLink(stringResource(Res.string.staff_add_poi)) { onCreatePoi(start.toGeoPoint()) }
        }
    }
}

@Composable
private fun ManageLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

// Quelli del contratto, senza il caso dei valori futuri (che l'app non manda)
private val TrailStatuses = UpdateTrailStatusRequest.Status.entries - UpdateTrailStatusRequest.Status.unknown_default_open_api
