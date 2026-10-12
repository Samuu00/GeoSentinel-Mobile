package it.unical.geosentinel_mobile.staff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.poi_open_trail
import geosentinel_mobile.shared.generated.resources.staff_tab_hazards
import geosentinel_mobile.shared.generated.resources.staff_tab_tickets
import geosentinel_mobile.shared.generated.resources.ticket_assign_me
import geosentinel_mobile.shared.generated.resources.ticket_assigned_me
import geosentinel_mobile.shared.generated.resources.ticket_assigned_other
import geosentinel_mobile.shared.generated.resources.ticket_cancel
import geosentinel_mobile.shared.generated.resources.ticket_cancel_confirm
import geosentinel_mobile.shared.generated.resources.ticket_cancel_confirm_body
import geosentinel_mobile.shared.generated.resources.ticket_cancel_confirm_title
import geosentinel_mobile.shared.generated.resources.ticket_cancel_keep
import geosentinel_mobile.shared.generated.resources.ticket_closed_note
import geosentinel_mobile.shared.generated.resources.ticket_create_body
import geosentinel_mobile.shared.generated.resources.ticket_create_save
import geosentinel_mobile.shared.generated.resources.ticket_create_title
import geosentinel_mobile.shared.generated.resources.ticket_discard
import geosentinel_mobile.shared.generated.resources.ticket_edit
import geosentinel_mobile.shared.generated.resources.ticket_field_description
import geosentinel_mobile.shared.generated.resources.ticket_field_priority
import geosentinel_mobile.shared.generated.resources.ticket_field_title
import geosentinel_mobile.shared.generated.resources.ticket_field_title_count
import geosentinel_mobile.shared.generated.resources.ticket_filter_all
import geosentinel_mobile.shared.generated.resources.ticket_filter_any_priority
import geosentinel_mobile.shared.generated.resources.ticket_filter_mine
import geosentinel_mobile.shared.generated.resources.ticket_list_empty_body
import geosentinel_mobile.shared.generated.resources.ticket_list_empty_title
import geosentinel_mobile.shared.generated.resources.ticket_list_intro
import geosentinel_mobile.shared.generated.resources.ticket_list_title
import geosentinel_mobile.shared.generated.resources.ticket_opened_at
import geosentinel_mobile.shared.generated.resources.ticket_priority_critical
import geosentinel_mobile.shared.generated.resources.ticket_priority_high
import geosentinel_mobile.shared.generated.resources.ticket_priority_label
import geosentinel_mobile.shared.generated.resources.ticket_priority_low
import geosentinel_mobile.shared.generated.resources.ticket_priority_medium
import geosentinel_mobile.shared.generated.resources.ticket_resolve
import geosentinel_mobile.shared.generated.resources.ticket_resolved_at
import geosentinel_mobile.shared.generated.resources.ticket_save
import geosentinel_mobile.shared.generated.resources.ticket_start
import geosentinel_mobile.shared.generated.resources.ticket_status_assigned
import geosentinel_mobile.shared.generated.resources.ticket_status_cancelled
import geosentinel_mobile.shared.generated.resources.ticket_status_in_progress
import geosentinel_mobile.shared.generated.resources.ticket_status_open
import geosentinel_mobile.shared.generated.resources.ticket_status_resolved
import geosentinel_mobile.shared.generated.resources.ticket_target_hazard
import geosentinel_mobile.shared.generated.resources.ticket_unassigned
import geosentinel_mobile.shared.generated.resources.trail_retry
import it.unical.geosentinel_mobile.api.MaintenanceApi
import it.unical.geosentinel_mobile.api.model.CreateTicketRequest
import it.unical.geosentinel_mobile.api.model.TicketResponse
import it.unical.geosentinel_mobile.api.model.UpdateTicketRequest
import it.unical.geosentinel_mobile.api.model.UpdateTicketStatusRequest
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.groupedItem
import it.unical.geosentinel_mobile.core.ui.PageHeader
import it.unical.geosentinel_mobile.core.ui.pageBottomInset
import it.unical.geosentinel_mobile.core.ui.PageHeaderHeight
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.hazard.ui.Choices
import it.unical.geosentinel_mobile.hazard.ui.Field
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Scheda Gestione: le segnalazioni da verificare (M8.1) e i ticket di manutenzione (M8.2), scelti con due voci sotto
 * la testata. La voce scelta resta anche tornando da un dettaglio.
 */
@Composable
fun ManagementScreen(onOpenTrail: (String) -> Unit, onOpenTicket: (String) -> Unit) {
    var tickets by rememberSaveable { mutableStateOf(false) }
    val tabs = @Composable { ManagementTabs(tickets) { tickets = it } }
    if (tickets) {
        TicketListScreen(onOpenTicket = onOpenTicket, tabs = tabs)
    } else {
        HazardQueueScreen(trailId = null, onBack = null, onOpenTrail = onOpenTrail, tabs = tabs)
    }
}

@Composable
private fun ManagementTabs(tickets: Boolean, onSelect: (Boolean) -> Unit) {
    val labels = listOf(Res.string.staff_tab_hazards, Res.string.staff_tab_tickets)
    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth(),
    ) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = tickets == (index == 1),
                onClick = { onSelect(index == 1) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Lista
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun TicketListScreen(onOpenTicket: (String) -> Unit, tabs: @Composable () -> Unit) {
    val viewModel = koinViewModel<TicketListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error = state.error

    // tornando dal dettaglio la lista si rilegge: il ticket può essere cambiato
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 24.dp + pageBottomInset()),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item(key = "header") {
            PageHeader(
                title = stringResource(Res.string.ticket_list_title),
                intro = stringResource(Res.string.ticket_list_intro),
                below = tabs,
            )
        }
        item(key = "filters") { Filters(state, viewModel) }
        when {
            state.tickets.isEmpty() && state.loading -> item(key = "loading") { CenteredProgress() }
            error != null -> item(key = "error") {
                Message(text = error.resolve(), isError = true) {
                    PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = viewModel::reload)
                }
            }
            state.tickets.isEmpty() -> item(key = "empty") {
                Message(title = stringResource(Res.string.ticket_list_empty_title), text = stringResource(Res.string.ticket_list_empty_body))
            }
            else -> itemsIndexed(state.tickets, key = { _, ticket -> ticket.id }) { index, ticket ->
                TicketRow(ticket, first = index == 0, last = index == state.tickets.lastIndex) { onOpenTicket(ticket.id) }
            }
        }
    }
}

// Due righe di filtri che scorrono in orizzontale: stato (con "Solo i miei") e priorità
@Composable
private fun Filters(state: TicketListUiState, viewModel: TicketListViewModel) {
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(state.mineOnly, onClick = { viewModel.onMineChange(!state.mineOnly) }, label = { Text(stringResource(Res.string.ticket_filter_mine)) })
            FilterChip(state.status == null, onClick = { viewModel.onStatusChange(null) }, label = { Text(stringResource(Res.string.ticket_filter_all)) })
            SearchStatuses.forEach { status ->
                FilterChip(
                    state.status == status,
                    onClick = { viewModel.onStatusChange(status) },
                    label = { Text(stringResource(statusLabel(status.value))) },
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(state.priority == null, onClick = { viewModel.onPriorityChange(null) }, label = { Text(stringResource(Res.string.ticket_filter_any_priority)) })
            SearchPriorities.forEach { priority ->
                FilterChip(
                    state.priority == priority,
                    onClick = { viewModel.onPriorityChange(priority) },
                    label = { Text(stringResource(priorityLabel(priority.value))) },
                )
            }
        }
    }
}

// Un ticket: titolo e stato, poi priorità, luogo e data
@Composable
private fun TicketRow(ticket: TicketResponse, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // una riga del gruppo dei ticket (prima una card per ticket)
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).groupedItem(first, last)) {
        if (!first) GroupDivider()
        TicketRowContent(ticket, onClick)
    }
}

@Composable
private fun TicketRowContent(ticket: TicketResponse, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ticket.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Chip(stringResource(statusLabel(ticket.status.value)))
        }
        Text(
            stringResource(Res.string.ticket_priority_label, stringResource(priorityLabel(ticket.priority.value))) +
                " · " + ticket.place() + " · " + remember(ticket.createdAt) { formatDate(ticket.createdAt) },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Dettaglio
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Dettaglio di un ticket (M8.2): dati, azioni secondo lo stato e "Modifica". Le transizioni le decide il server; qui si
 * mostrano solo quelle che ha senso proporre. Un 409 `concurrent-modification` mostra il messaggio e la versione nuova.
 */
@Composable
fun TicketDetailScreen(ticketId: String, onBack: () -> Unit, onOpenTrail: (String) -> Unit) {
    val viewModel = koinViewModel<TicketDetailViewModel>(key = "ticket-$ticketId") { parametersOf(ticketId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ticket = state.ticket
    val loadError = state.loadError
    var confirmCancel by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ForestHeader(Modifier.fillMaxWidth().height(PageHeaderHeight))
            when {
                ticket == null && loadError != null -> Message(text = loadError.resolve(), isError = true) {
                    PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = viewModel::reload)
                }
                ticket == null -> CenteredProgress()
                else -> Column(
                    Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TicketInfo(ticket, state.me, onOpenTrail)
                    FormErrorBanner(state.actionError?.resolve())
                    val draft = state.draft
                    if (draft != null) {
                        EditForm(draft, state.busy, viewModel)
                    } else {
                        Actions(ticket, state, viewModel, onCancel = { confirmCancel = true })
                    }
                    Spacer(Modifier.height(24.dp + WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()))
                }
            }
        }
        BackButton(onBack)
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(Res.string.ticket_cancel_confirm_title)) },
            text = { Text(stringResource(Res.string.ticket_cancel_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.changeStatus(UpdateTicketStatusRequest.Status.CANCELLED)
                }) { Text(stringResource(Res.string.ticket_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(Res.string.ticket_cancel_keep)) }
            },
        )
    }
}

@Composable
private fun TicketInfo(ticket: TicketResponse, me: String?, onOpenTrail: (String) -> Unit) {
    Column {
        Text(ticket.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(statusLabel(ticket.status.value)))
            Chip(stringResource(Res.string.ticket_priority_label, stringResource(priorityLabel(ticket.priority.value))))
        }
        val assignee = when (ticket.assignedTo) {
            null -> Res.string.ticket_unassigned
            me -> Res.string.ticket_assigned_me
            else -> Res.string.ticket_assigned_other
        }
        val opened = stringResource(Res.string.ticket_opened_at, remember(ticket.createdAt) { formatDate(ticket.createdAt) })
        val resolved = ticket.resolvedAt?.let { stringResource(Res.string.ticket_resolved_at, remember(it) { formatDate(it) }) }
        Text(
            listOfNotNull(ticket.place(), stringResource(assignee), opened, resolved).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        ticket.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
        }
        ticket.trailId?.let { trailId ->
            TextButton(onClick = { onOpenTrail(trailId) }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.poi_open_trail), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Il passo successivo è la capsula piena; le altre azioni sono contornate. Un ticket chiuso non ha azioni.
@Composable
private fun Actions(ticket: TicketResponse, state: TicketDetailUiState, viewModel: TicketDetailViewModel, onCancel: () -> Unit) {
    val status = ticket.status
    if (status == TicketResponse.Status.RESOLVED || status == TicketResponse.Status.CANCELLED) {
        Text(stringResource(Res.string.ticket_closed_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val canAssign = state.me != null && ticket.assignedTo != state.me
    val canStart = status == TicketResponse.Status.OPEN || status == TicketResponse.Status.ASSIGNED
    val canResolve = status == TicketResponse.Status.ASSIGNED || status == TicketResponse.Status.IN_PROGRESS
    val actions = buildList<Pair<StringResource, () -> Unit>> {
        if (canAssign) add(Res.string.ticket_assign_me to viewModel::assignToMe)
        if (canStart) add(Res.string.ticket_start to { viewModel.changeStatus(UpdateTicketStatusRequest.Status.IN_PROGRESS) })
        if (canResolve) add(Res.string.ticket_resolve to { viewModel.changeStatus(UpdateTicketStatusRequest.Status.RESOLVED) })
        add(Res.string.ticket_edit to viewModel::startEdit)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEachIndexed { index, (label, onClick) ->
            if (index == 0) {
                PrimaryPillButton(stringResource(label), onClick = onClick, loading = state.busy)
            } else {
                OutlinedButton(onClick = onClick, enabled = !state.busy, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(label))
                }
            }
        }
        TextButton(onClick = onCancel, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.ticket_cancel))
        }
    }
}

@Composable
private fun EditForm(draft: TicketDraft, busy: Boolean, viewModel: TicketDetailViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TicketFields(
            title = draft.title,
            onTitleChange = { viewModel.onDraftChange(draft.copy(title = it)) },
            description = draft.description,
            onDescriptionChange = { viewModel.onDraftChange(draft.copy(description = it)) },
        )
        Field(title = Res.string.ticket_field_priority, error = null) {
            Choices(EditPriorities, selected = draft.priority, label = { priorityLabel(it.value) }, onSelect = { viewModel.onDraftChange(draft.copy(priority = it)) })
        }
        PrimaryPillButton(stringResource(Res.string.ticket_save), onClick = viewModel::saveDraft, enabled = draft.valid, loading = busy)
        TextButton(onClick = viewModel::cancelEdit, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.ticket_discard))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Nuovo ticket
// ---------------------------------------------------------------------------------------------------------------------

// Nuovo ticket dal dettaglio di un sentiero (M8.2). Quando il server lo salva si torna indietro con [onCreated].
@Composable
fun TicketCreateScreen(trailId: String, onBack: () -> Unit, onCreated: () -> Unit) {
    val viewModel = koinViewModel<TicketCreateViewModel>(key = "ticket-create-$trailId") { parametersOf(trailId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.created) {
        if (state.created) onCreated()
    }

    FormScaffold(
        onBack = onBack,
        title = Res.string.ticket_create_title,
        body = Res.string.ticket_create_body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(stringResource(Res.string.ticket_create_save), onClick = viewModel::save, enabled = state.canSave, loading = state.busy)
        },
    ) {
        TicketFields(
            title = state.title,
            onTitleChange = viewModel::onTitleChange,
            description = state.description,
            onDescriptionChange = viewModel::onDescriptionChange,
        )
        Field(title = Res.string.ticket_field_priority, error = null) {
            Choices(CreatePriorities, selected = state.priority, label = { priorityLabel(it.value) }, onSelect = viewModel::onPriorityChange)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// In comune
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun TicketFields(title: String, onTitleChange: (String) -> Unit, description: String, onDescriptionChange: (String) -> Unit) {
    FormTextField(
        value = title,
        onValueChange = onTitleChange,
        label = stringResource(Res.string.ticket_field_title),
        supporting = stringResource(Res.string.ticket_field_title_count, title.trim().length, TICKET_TITLE_MAX, TICKET_TITLE_MIN),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    FormTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = stringResource(Res.string.ticket_field_description),
        singleLine = false,
        minLines = 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

// Stato e priorità in una capsula neutra: il rosso non si usa per i ticket (nemmeno CRITICAL, che non è un'emergenza)
@Composable
private fun Chip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// Il sentiero o il rifugio del ticket; quelli aperti da una segnalazione non hanno un nome
@Composable
private fun TicketResponse.place(): String = trailName ?: refugeName ?: stringResource(Res.string.ticket_target_hazard)

// Per valore: i tre enum generati (risposta, ricerca, richieste) hanno gli stessi valori
private fun statusLabel(value: String): StringResource = when (value) {
    "ASSIGNED" -> Res.string.ticket_status_assigned
    "IN_PROGRESS" -> Res.string.ticket_status_in_progress
    "RESOLVED" -> Res.string.ticket_status_resolved
    "CANCELLED" -> Res.string.ticket_status_cancelled
    else -> Res.string.ticket_status_open // OPEN, o un valore futuro: come un ticket aperto
}

private fun priorityLabel(value: String): StringResource = when (value) {
    "LOW" -> Res.string.ticket_priority_low
    "HIGH" -> Res.string.ticket_priority_high
    "CRITICAL" -> Res.string.ticket_priority_critical
    else -> Res.string.ticket_priority_medium
}

// Quelle del contratto, senza il caso dei valori futuri (che l'app non manda)
private val SearchStatuses = MaintenanceApi.StatusSearchTickets.entries.filter { it.name != "unknown_default_open_api" }
private val SearchPriorities = MaintenanceApi.PrioritySearchTickets.entries.filter { it.name != "unknown_default_open_api" }
private val CreatePriorities = CreateTicketRequest.Priority.entries - CreateTicketRequest.Priority.unknown_default_open_api
private val EditPriorities = UpdateTicketRequest.Priority.entries - UpdateTicketRequest.Priority.unknown_default_open_api
