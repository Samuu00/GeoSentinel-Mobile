// ViewModel del dettaglio di un ticket di manutenzione: lettura con l'ETag, modifica dei campi, assegnazione a sé,
// cambio di stato e gestione dei conflitti con le modifiche dei colleghi.
package it.unical.geosentinel_mobile.staff.ui

import geosentinel_mobile.shared.generated.resources.ticket_missing_version
import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
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

/** Valori del form "Modifica" del dettaglio; [valid] dice se rispettano i limiti di lunghezza. */
data class TicketDraft(
    val title: String,
    val description: String,
    val priority: UpdateTicketRequest.Priority,
) {
    val valid: Boolean get() = ticketFieldsValid(title, description)
}

/** Stato del dettaglio del ticket. */
data class TicketDetailUiState(
    val ticket: TicketResponse? = null,
    /** ETag della copia mostrata: si invia in `If-Match` con ogni modifica. */
    val etag: String? = null,
    /** Id dell'utente loggato, per mostrare "Assegnato a te" e nascondere "Assegna a me". */
    val me: String? = null,
    val loading: Boolean = true,
    val loadError: UiMessage? = null,
    val busy: Boolean = false,
    val actionError: UiMessage? = null,
    /** Bozza della modifica; non è null solo mentre il form "Modifica" è aperto. */
    val draft: TicketDraft? = null,
)

/**
 * ViewModel del dettaglio del ticket [ticketId]. Ogni modifica invia l'ETag dell'ultima lettura. Se nel frattempo un
 * collega ha cambiato il ticket, il server risponde 409 `concurrent-modification`: si mostra il messaggio, si rilegge
 * la versione nuova e la modifica in corso si chiude, perché era basata su dati superati.
 */
class TicketDetailViewModel(
    private val ticketId: String,
    private val tickets: TicketRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TicketDetailUiState())
    val state: StateFlow<TicketDetailUiState> = _state.asStateFlow()

    // All'apertura legge l'id dell'utente e poi il ticket.
    init {
        viewModelScope.launch {
            val me = session.currentUserId()
            _state.update { it.copy(me = me) }
            load()
        }
    }

    /** Rilegge il ticket, per esempio con "Riprova". */
    fun reload() { viewModelScope.launch { load() } }

    /** Apre il form "Modifica" con i valori attuali del ticket; una priorità sconosciuta diventa media. */
    fun startEdit() {
        val ticket = _state.value.ticket ?: return
        val priority = UpdateTicketRequest.Priority.entries.firstOrNull { it.value == ticket.priority.value }
            ?: UpdateTicketRequest.Priority.MEDIUM
        _state.update { it.copy(draft = TicketDraft(ticket.title, ticket.description.orEmpty(), priority), actionError = null) }
    }

    /** Chiude il form "Modifica" senza salvare. */
    fun cancelEdit() = _state.update { it.copy(draft = null) }

    /** Aggiorna la bozza, solo se il form è aperto. */
    fun onDraftChange(draft: TicketDraft) = _state.update { if (it.draft == null) it else it.copy(draft = draft) }

    /** Salva la bozza se è valida; una descrizione vuota si invia come assente. */
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

    /** Assegna il ticket all'utente loggato. */
    fun assignToMe() {
        val me = _state.value.me ?: return
        act { etag -> tickets.assign(ticketId, etag, me) }
    }

    /** Porta il ticket allo stato [status]. */
    fun changeStatus(status: UpdateTicketStatusRequest.Status) = act { etag -> tickets.changeStatus(ticketId, etag, status) }

    /**
     * Esegue una modifica [block] con l'ETag attuale, una alla volta. In caso di successo salva il ticket e il nuovo
     * ETag e chiude il form; in caso di errore mostra il messaggio e, se è un conflitto, rilegge il ticket.
     */
    private fun act(block: suspend (etag: String) -> Result<Versioned<TicketResponse>>) {
        val current = _state.value
        if (current.busy) return
        val etag = current.etag
        if (etag == null) {
            // Senza ETag la modifica non si può inviare: si rilegge il ticket e si chiede di riprovare, invece di
            // ignorare il tocco in silenzio come se il pulsante fosse rotto.
            _state.update { it.copy(actionError = UiMessage(Res.string.ticket_missing_version)) }
            viewModelScope.launch { load(keepError = true) }
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
                    // Conflitto: si rilegge la versione nuova, lasciando a schermo il messaggio d'errore.
                    if (conflict) load(keepError = true)
                },
            )
        }
    }

    /** Legge il ticket con il suo ETag; con [keepError] il messaggio dell'ultima azione resta visibile. */
    private suspend fun load(keepError: Boolean = false) {
        _state.update { it.copy(loading = it.ticket == null, loadError = null, actionError = if (keepError) it.actionError else null) }
        safely { tickets.get(ticketId) }.fold(
            onSuccess = { version -> _state.update { it.copy(ticket = version.value, etag = version.etag, loading = false) } },
            onFailure = { failure ->
                _state.update {
                    // Se il dettaglio è già a schermo, un errore della rilettura non lo toglie: diventa un errore d'azione.
                    if (it.ticket == null) it.copy(loading = false, loadError = failure.toUiMessage())
                    else it.copy(loading = false, actionError = failure.toUiMessage())
                }
            },
        )
    }
}

/** Vero se l'errore è il 409 `concurrent-modification` di un ETag non più valido. */
private fun Throwable.isConcurrentModification(): Boolean {
    val error = (this as? ApiException)?.error
    return error is ApiError.Conflict && error.slug == "concurrent-modification"
}
