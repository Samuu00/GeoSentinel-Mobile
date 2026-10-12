package it.unical.geosentinel_mobile.rescue.ui

import it.unical.geosentinel_mobile.core.ui.PageMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.rescue_detail_title
import geosentinel_mobile.shared.generated.resources.rescue_dispatch
import geosentinel_mobile.shared.generated.resources.rescue_location_title
import geosentinel_mobile.shared.generated.resources.rescue_location_updated
import geosentinel_mobile.shared.generated.resources.rescue_medical_none
import geosentinel_mobile.shared.generated.resources.rescue_medical_title
import geosentinel_mobile.shared.generated.resources.rescue_raised_ago
import geosentinel_mobile.shared.generated.resources.rescue_resolve
import geosentinel_mobile.shared.generated.resources.rescue_resolve_body
import geosentinel_mobile.shared.generated.resources.rescue_resolve_confirm
import geosentinel_mobile.shared.generated.resources.rescue_resolve_dismiss
import geosentinel_mobile.shared.generated.resources.rescue_resolve_title
import geosentinel_mobile.shared.generated.resources.rescue_team_assigned
import geosentinel_mobile.shared.generated.resources.rescue_team_contact
import geosentinel_mobile.shared.generated.resources.rescue_teams_empty
import geosentinel_mobile.shared.generated.resources.rescue_teams_title
import geosentinel_mobile.shared.generated.resources.trail_retry
import it.unical.geosentinel_mobile.api.model.RescueTeamResponse
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.location.decimals5
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(20.dp)

/**
 * Dettaglio di un'allerta per l'operatore (M7.2), spinto dalla scheda Allerte, dalla mappa o dalla push. Posizione con la
 * sua età, note mediche (solo qui, mai in lista o nella notifica), squadra. Con l'allerta `ACTIVE` si sceglie una squadra
 * libera e la si invia; con `ACTIVE` o `DISPATCHED` si chiude, con conferma. L'azione principale sta in fondo, fissa.
 * Con la schermata aperta l'allerta si rilegge ogni 30 s: l'escursionista manda la posizione ogni 60 s.
 */
@Composable
fun RescueAlertDetailScreen(alertId: String, onBack: () -> Unit) {
    val viewModel = koinViewModel<RescueAlertDetailViewModel>(key = alertId) { parametersOf(alertId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmResolve by rememberSaveable { mutableStateOf(false) }
    val now = rememberNow()

    LaunchedEffect(state.resolved) { if (state.resolved) onBack() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(RescueRefreshEvery)
                viewModel.refresh()
            }
        }
    }

    if (confirmResolve) {
        AlertDialog(
            onDismissRequest = { confirmResolve = false },
            title = { Text(stringResource(Res.string.rescue_resolve_title)) },
            text = { Text(stringResource(Res.string.rescue_resolve_body)) },
            confirmButton = {
                TextButton(onClick = { confirmResolve = false; viewModel.resolve() }) { Text(stringResource(Res.string.rescue_resolve_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmResolve = false }) { Text(stringResource(Res.string.rescue_resolve_dismiss)) }
            },
        )
    }

    val alert = state.alert
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    // sotto il pulsante Indietro
                    .padding(top = 64.dp, bottom = 16.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    val loadError = state.loadError
                    when {
                        alert == null && loadError != null -> {
                            Text(
                                loadError.resolve(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = viewModel::retry)
                        }
                        alert == null -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(36.dp))
                        }
                        else -> AlertContent(alert, state, now, onSelectTeam = viewModel::selectTeam, onRetryTeams = viewModel::retryTeams)
                    }
                }
            }
            if (alert != null) BottomActions(alert, state, onDispatch = viewModel::dispatch, onResolve = { confirmResolve = true })
        }
        BackButton(onBack)
    }
}

@Composable
private fun AlertContent(
    alert: SosAlertResponse,
    state: RescueAlertDetailUiState,
    now: Instant,
    onSelectTeam: (String) -> Unit,
    onRetryTeams: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AlertBadge(waiting = alert.status == SosAlertResponse.Status.ACTIVE)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(Res.string.rescue_detail_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            // lo stato cambia con la schermata aperta (squadra di un collega, chiusura): lo si annuncia
            Text(
                stringResource(alert.status.label()) + " · " + stringResource(Res.string.rescue_raised_ago, ago(alert.createdAt, now)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    FormErrorBanner(state.actionError?.resolve())

    InfoCard(stringResource(Res.string.rescue_location_title)) {
        Text("${alert.location.lat.decimals5()}, ${alert.location.lon.decimals5()}", style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.rescue_location_updated, ago(alert.locationUpdatedAt, now)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    InfoCard(stringResource(Res.string.rescue_medical_title)) {
        val notes = alert.medicalNotes?.takeIf { it.isNotBlank() }
        Text(
            notes ?: stringResource(Res.string.rescue_medical_none),
            style = MaterialTheme.typography.bodyLarge,
            color = if (notes != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    alert.assignedTeam?.let { team ->
        InfoCard(stringResource(Res.string.rescue_team_assigned)) { TeamText(team) }
    }

    if (alert.status == SosAlertResponse.Status.ACTIVE) {
        Text(
            stringResource(Res.string.rescue_teams_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        val teamsError = state.teamsError
        when {
            state.teamsLoading && state.teams.isEmpty() -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            teamsError != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(teamsError.resolve(), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetryTeams, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.trail_retry)) }
            }
            state.teams.isEmpty() -> Text(stringResource(Res.string.rescue_teams_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.teams.forEach { team ->
                    TeamOption(team, selected = team.id == state.selectedTeamId, enabled = !state.busy) { onSelectTeam(team.id) }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun TeamText(team: RescueTeamResponse) {
    Text("${team.name} (${team.callSign})", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    Text(stringResource(Res.string.rescue_team_contact, team.contactNumber), style = MaterialTheme.typography.bodyMedium)
}

// Una squadra libera: tutta la riga si tocca (radio), bordo primary quando è scelta
@Composable
private fun TeamOption(team: RescueTeamResponse, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerLowest)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, CardShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) { TeamText(team) }
        // la riga intera è il radio: questo lo mostra e basta
        RadioButton(selected = selected, onClick = null)
    }
}

// In fondo, fisso: "Invia la squadra" con l'allerta in attesa, "Chiudi l'allerta" con la squadra in viaggio.
// Un'allerta in attesa si può chiudere lo stesso (falso allarme), con il link sotto.
@Composable
private fun BottomActions(alert: SosAlertResponse, state: RescueAlertDetailUiState, onDispatch: () -> Unit, onResolve: () -> Unit) {
    val waiting = alert.status == SosAlertResponse.Status.ACTIVE
    if (!waiting && alert.status != SosAlertResponse.Status.DISPATCHED) return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (waiting) {
                PrimaryPillButton(
                    stringResource(Res.string.rescue_dispatch),
                    onClick = onDispatch,
                    enabled = state.selectedTeamId != null && !state.busy,
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onResolve, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.rescue_resolve))
                }
            } else {
                PrimaryPillButton(
                    stringResource(Res.string.rescue_resolve),
                    onClick = onResolve,
                    enabled = !state.busy,
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
