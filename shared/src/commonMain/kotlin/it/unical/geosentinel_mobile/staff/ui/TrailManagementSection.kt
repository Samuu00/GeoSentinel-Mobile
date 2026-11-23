// Sezione "Gestione" del dettaglio di un sentiero, visibile al personale: stato del sentiero e azioni del gestore.
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
import geosentinel_mobile.shared.generated.resources.staff_open_ticket
import geosentinel_mobile.shared.generated.resources.staff_trail_status
import it.unical.geosentinel_mobile.api.model.TrailDetailResponse
import it.unical.geosentinel_mobile.api.model.UpdateTrailStatusRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPositions
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.hazard.ui.Choices
import it.unical.geosentinel_mobile.trail.ui.TrailState
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.core.ui.GroupCard
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.GroupIconIndent
import it.unical.geosentinel_mobile.core.ui.GroupRow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Azioni del gestore nel dettaglio del sentiero: cambio dello stato, coda delle segnalazioni, nuovo POI legato al
 * sentiero e nuovo ticket. Il ruolo decide solo se mostrare la sezione: un eventuale 403 del server arriva comunque
 * come messaggio.
 */
@Composable
fun TrailManagementSection(
    trail: TrailDetailResponse,
    onOpenQueue: () -> Unit,
    onCreatePoi: (start: GeoPoint) -> Unit,
    onCreateTicket: () -> Unit,
) {
    val viewModel = koinViewModel<TrailStatusViewModel>(key = "trail-status-${trail.id}") { parametersOf(trail.id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Lo stato richiesto si mostra subito; quando arriva la risposta lo sostituisce quello salvato in cache.
    val selected = state.pending ?: TrailStatuses.firstOrNull { it.value == trail.status.value }

    Column(Modifier.padding(top = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.staff_manage_title),
                style = MaterialTheme.typography.titleLarge,
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
            // Il rosso si usa solo per i sentieri chiusi.
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
        Spacer(Modifier.size(16.dp))
        // Le azioni del gestore sono righe di un gruppo, ognuna con la sua icona.
        GroupCard {
            GroupRow(stringResource(Res.string.staff_open_queue), onClick = onOpenQueue, icon = AppIcons.Alert)
            // Il form del POI parte con la mappa sull'inizio del sentiero, come quello della segnalazione.
            trail.path.toPositions().firstOrNull()?.let { start ->
                GroupDivider(GroupIconIndent)
                GroupRow(stringResource(Res.string.staff_add_poi), onClick = { onCreatePoi(start.toGeoPoint()) }, icon = AppIcons.MapPin)
            }
            GroupDivider(GroupIconIndent)
            GroupRow(stringResource(Res.string.staff_open_ticket), onClick = onCreateTicket, icon = AppIcons.Wrench)
        }
    }
}

/** Azione del personale mostrata come link nel colore primario, per esempio sotto il titolo della scheda Gestione. */
@Composable
internal fun ManageLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Stati del sentiero proposti al gestore: quelli del contratto, tranne il caso dei valori sconosciuti, che l'app non invia. */
private val TrailStatuses = UpdateTrailStatusRequest.Status.entries - UpdateTrailStatusRequest.Status.unknown_default_open_api
