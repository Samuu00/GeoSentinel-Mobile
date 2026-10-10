package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.ticket_rule_violation
import it.unical.geosentinel_mobile.api.model.TicketResponse
import it.unical.geosentinel_mobile.api.model.UpdateTicketRequest
import it.unical.geosentinel_mobile.api.model.UpdateTicketStatusRequest
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.Versioned
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.staff.data.TicketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Il form "Modifica" del dettaglio
data class TicketDraft(
    val title: String,
    val description: String,
    val priority: UpdateTicketRequest.Priority,
) {
    val valid: Boolean get() = ticketFieldsValid(title, description)
}

data class TicketDetailUiState(
    val ticket: TicketResponse? = null,
    // versione della copia mostrata: va in `If-Match` con ogni modifica
    val etag: String? = null,
    // l'utente loggato: per "Assegnato a te" e per nascondere "Assegna a me"
    val me: String? = null,
    val loading: Boolean = true,
    val loadError: UiMessage? = null,
    val busy: Boolean = false,
    val actionError: UiMessage? = null,
    // non null mentre si modifica
    val draft: TicketDraft? = null,
)

/**
 * Dettaglio di un ticket. Ogni modifica manda l'ETag dell'ultima lettura. Se un collega ha cambiato il ticket
 * nel frattempo, il server risponde 409 `concurrent-modification`: si mostra il messaggio e si rilegge la versione nuova
 * (la modifica in corso si chiude: era fatta su dati vecchi).
 */
class TicketDetailViewModel(
    private val ticketId: String,
    private val tickets: TicketRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TicketDetailUiState())
    val state: StateFlow<TicketDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val me = session.currentUserId()
            _state.update { it.copy(me = me) }
            load()
        }
    }

    fun reload() { viewModelScope.launch { load() } }

    fun startEdit() {
        val ticket = _state.value.ticket ?: return
        val priority = UpdateTicketRequest.Priority.entries.firstOrNull { it.value == ticket.priority.value }
            ?: UpdateTicketRequest.Priority.MEDIUM
        _state.update { it.copy(draft = TicketDraft(ticket.title, ticket.description.orEmpty(), priority), actionError = null) }
    }

    fun cancelEdit() = _state.update { it.copy(draft = null) }

    fun onDraftChange(draft: TicketDraft) = _state.update { if (it.draft == null) it else it.copy(draft = draft) }

    fun saveDraft() {
        val draft = _state.value.draft ?: return
        if (!draft.valid) return
        act { etag ->
            tickets.update(
                ticketId,
                etag,
                UpdateTicketRequest(
                    title = draft.title.trim(),
                    description = draft.description.trim().takeIf { it.isNotEmpty() },
                    priority = draft.priority,
                ),
            )
        }
    }

    fun assignToMe() {
        val me = _state.value.me ?: return
        act { etag -> tickets.assign(ticketId, etag, me) }
    }

    fun changeStatus(status: UpdateTicketStatusRequest.Status) = act { etag -> tickets.changeStatus(ticketId, etag, status) }

    private fun act(block: suspend (etag: String) -> Result<Versioned<TicketResponse>>) {
        val current = _state.value
        if (current.busy) return
        val etag = current.etag
        if (etag == null) {
            // senza ETag la modifica non si può mandare: prima si rilegge il ticket
            reload()
            return
        }
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            safely { block(etag) }.fold(
                onSuccess = { version ->
                    _state.update { it.copy(busy = false, ticket = version.value, etag = version.etag, draft = null) }
                },
                onFailure = { failure ->
                    val conflict = failure.isConcurrentModification()
                    _state.update {
                        it.copy(
                            busy = false,
                            actionError = failure.toUiMessage(onRuleViolation = Res.string.ticket_rule_violation),
                            draft = if (conflict) null else it.draft,
                        )
                    }
                    // 409 concurrent-modification: si rilegge la versione nuova (il messaggio resta a schermo)
                    if (conflict) load(keepError = true)
                },
            )
        }
    }

    private suspend fun load(keepError: Boolean = false) {
        _state.update { it.copy(loading = it.ticket == null, loadError = null, actionError = if (keepError) it.actionError else null) }
        safely { tickets.get(ticketId) }.fold(
            onSuccess = { version -> _state.update { it.copy(ticket = version.value, etag = version.etag, loading = false) } },
            onFailure = { failure ->
                _state.update {
                    // con il dettaglio già a schermo un errore della rilettura non lo toglie
                    if (it.ticket == null) it.copy(loading = false, loadError = failure.toUiMessage())
                    else it.copy(loading = false, actionError = failure.toUiMessage())
                }
            },
        )
    }
}

private fun Throwable.isConcurrentModification(): Boolean {
    val error = (this as? ApiException)?.error
    return error is ApiError.Conflict && error.slug == "concurrent-modification"
}
