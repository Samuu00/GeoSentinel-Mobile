package it.unical.geosentinel_mobile.staff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.poi_open_trail
import geosentinel_mobile.shared.generated.resources.staff_pending_empty_body
import geosentinel_mobile.shared.generated.resources.staff_pending_intro
import geosentinel_mobile.shared.generated.resources.staff_pending_title
import geosentinel_mobile.shared.generated.resources.staff_queue_empty_body
import geosentinel_mobile.shared.generated.resources.staff_queue_off_trail
import geosentinel_mobile.shared.generated.resources.staff_queue_empty_title
import geosentinel_mobile.shared.generated.resources.staff_queue_intro
import geosentinel_mobile.shared.generated.resources.staff_queue_reject
import geosentinel_mobile.shared.generated.resources.staff_queue_resolve
import geosentinel_mobile.shared.generated.resources.staff_queue_title
import geosentinel_mobile.shared.generated.resources.staff_queue_verify
import geosentinel_mobile.shared.generated.resources.trail_retry
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.UpdateHazardStatusRequest
import it.unical.geosentinel_mobile.core.location.decimals5
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.hazard.ui.label
import it.unical.geosentinel_mobile.trail.ui.color
import it.unical.geosentinel_mobile.trail.ui.label
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

private val ContentMaxWidth = 600.dp
private val CardShape = RoundedCornerShape(20.dp)

/**
 * Coda delle segnalazioni per il gestore. Con [trailId] quelle di un sentiero ("spinta", con [onBack]): le segnalazioni in
 * verifica si verificano o si respingono, quelle confermate si risolvono. Con [trailId] null è la scheda Gestione: le
 * segnalazioni da verificare di tutto il parco, ognuna con il suo sentiero ([onOpenTrail]) o le coordinate. Le
 * transizioni non valide le decide il server: un 409 o 422 si mostra e la lista si rilegge.
 */
@Composable
fun HazardQueueScreen(trailId: String?, onBack: (() -> Unit)?, onOpenTrail: ((String) -> Unit)? = null) {
    val viewModel = koinViewModel<HazardQueueViewModel>(key = "queue-${trailId ?: "pending"}") { parametersOf(trailId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadError = state.loadError
    val actionError = state.actionError

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { ForestHeader(Modifier.fillMaxWidth().height(200.dp)) }
            item(key = "title") { Title(pending = trailId == null) }
            if (actionError != null) {
                item(key = "action-error") { Notice(actionError.resolve()) }
            }
            when {
                state.hazards.isEmpty() && state.loading -> item(key = "loading") { CenteredProgress() }
                state.hazards.isEmpty() && loadError != null -> item(key = "error") {
                    Message(text = loadError.resolve(), isError = true) {
                        PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = viewModel::reload)
                    }
                }
                state.hazards.isEmpty() -> item(key = "empty") {
                    Message(
                        title = stringResource(Res.string.staff_queue_empty_title),
                        text = stringResource(if (trailId == null) Res.string.staff_pending_empty_body else Res.string.staff_queue_empty_body),
                    )
                }
                else -> items(state.hazards, key = { it.id }) { hazard ->
                    QueueRow(
                        hazard = hazard,
                        busy = state.busyHazardId == hazard.id,
                        // una sola azione alla volta: con una in corso le altre righe si disattivano
                        enabled = state.busyHazardId == null,
                        onChange = { status -> viewModel.change(hazard, status) },
                        // nella coda del parco: dove si trova (nella coda di un sentiero lo si sa già)
                        onOpenTrail = if (trailId == null) onOpenTrail else null,
                        showPlace = trailId == null,
                    )
                }
            }
        }
        if (onBack != null) BackButton(onBack)
    }
}

@Composable
private fun Title(pending: Boolean) {
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp)) {
        Text(
            stringResource(if (pending) Res.string.staff_pending_title else Res.string.staff_queue_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(if (pending) Res.string.staff_pending_intro else Res.string.staff_queue_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// Una segnalazione: tipo e stato, gravità e data, la descrizione. Le azioni dipendono dallo stato; il rosso non compare
// mai qui (solo per CRITICAL nell'icona, come nelle altre liste)
@Composable
private fun QueueRow(
    hazard: HazardResponse,
    busy: Boolean,
    enabled: Boolean,
    onChange: (UpdateHazardStatusRequest.Status) -> Unit,
    onOpenTrail: ((String) -> Unit)?,
    showPlace: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(CardShape)
            .background(colors.surfaceContainerLowest)
            .border(1.dp, colors.outlineVariant, CardShape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // decorativa: tipo e gravità sono scritti accanto
            Icon(Icons.Filled.Warning, contentDescription = null, tint = hazard.severity.color(), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(hazard.type.label()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            StatusChip(hazard.status)
        }
        Text(
            stringResource(hazard.severity.label()) + " · " + remember(hazard.createdAt) { formatDate(hazard.createdAt) },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        hazard.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
        if (showPlace) {
            val trailId = hazard.trailId
            if (trailId != null && onOpenTrail != null) {
                TextButton(
                    onClick = { onOpenTrail(trailId) },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.poi_open_trail), fontWeight = FontWeight.SemiBold)
                }
            } else {
                // una segnalazione dalla mappa: le coordinate (5 decimali, circa un metro) per ritrovarla
                Text(
                    stringResource(Res.string.staff_queue_off_trail, hazard.location.lat.decimals5(), hazard.location.lon.decimals5()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        // Solo SUBMITTED (verifica o respingi) e VERIFIED (risolvi). Le altre non hanno azioni.
        if (hazard.status == HazardResponse.Status.SUBMITTED) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryPillButton(
                    stringResource(Res.string.staff_queue_verify),
                    onClick = { onChange(UpdateHazardStatusRequest.Status.VERIFIED) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    loading = busy,
                )
                OutlinedButton(
                    onClick = { onChange(UpdateHazardStatusRequest.Status.REJECTED) },
                    enabled = enabled,
                    // alta come la capsula accanto (56 dp), così le due azioni si leggono come una coppia
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(stringResource(Res.string.staff_queue_reject))
                }
            }
        } else if (hazard.status == HazardResponse.Status.VERIFIED) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                PrimaryPillButton(
                    stringResource(Res.string.staff_queue_resolve),
                    onClick = { onChange(UpdateHazardStatusRequest.Status.RESOLVED) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    loading = busy,
                )
            }
        }
    }
}

// Lo stato è sempre scritto; il colore resta neutro (il rosso è riservato alle emergenze)
@Composable
private fun StatusChip(status: HazardResponse.Status) {
    Text(
        stringResource(status.label()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
    }
}

// Errore dell'azione (409 di un collega, 422 di una transizione non permessa): sopra la lista, letto dagli screen reader
@Composable
private fun Notice(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

// Stato vuoto o d'errore: testo centrato e una sola azione
@Composable
private fun Message(text: String, title: String? = null, isError: Boolean = false, action: @Composable () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
        )
        Spacer(Modifier.height(24.dp))
        action()
    }
}

// "05/10/2026, 13:36" nell'ora del telefono (come in MyHazardsScreen)
private fun formatDate(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    fun Int.two() = toString().padStart(2, '0')
    return "${local.day.two()}/${local.month.number.two()}/${local.year}, ${local.hour.two()}:${local.minute.two()}"
}