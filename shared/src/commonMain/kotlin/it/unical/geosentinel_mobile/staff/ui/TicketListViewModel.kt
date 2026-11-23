// ViewModel della lista dei ticket di manutenzione, con i filtri per stato, priorità e "solo i miei".
package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import it.unical.geosentinel_mobile.api.MaintenanceApi
import it.unical.geosentinel_mobile.api.model.TicketResponse
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.staff.data.TicketRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stato della lista dei ticket con i filtri scelti. */
data class TicketListUiState(
    val tickets: List<TicketResponse> = emptyList(),
    val loading: Boolean = true,
    val error: UiMessage? = null,
    val status: MaintenanceApi.StatusSearchTickets? = null,
    val priority: MaintenanceApi.PrioritySearchTickets? = null,
    /** Mostra solo i ticket assegnati all'utente loggato. */
    val mineOnly: Boolean = false,
)

/** ViewModel della lista dei ticket. Un cambio di filtro o un "Riprova" annulla la richiesta in corso e ne avvia una nuova. */
class TicketListViewModel(
    private val tickets: TicketRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TicketListUiState())
    val state: StateFlow<TicketListUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { reload() }

    /** Ricarica la lista annullando la richiesta in corso. */
    fun reload() {
        job?.cancel()
        job = viewModelScope.launch { load() }
    }

    /** Rilegge la lista al ritorno dal dettaglio, ma non se una lettura è già in corso (come la prima, all'apertura). */
    fun refresh() {
        if (job?.isActive != true) reload()
    }

    /** Imposta il filtro per stato (null: tutti) e ricarica. */
    fun onStatusChange(status: MaintenanceApi.StatusSearchTickets?) {
        _state.update { it.copy(status = status) }
        reload()
    }

    /** Imposta il filtro per priorità (null: tutte) e ricarica. */
    fun onPriorityChange(priority: MaintenanceApi.PrioritySearchTickets?) {
        _state.update { it.copy(priority = priority) }
        reload()
    }

    /** Attiva o disattiva il filtro "solo i miei" e ricarica. */
    fun onMineChange(mineOnly: Boolean) {
        _state.update { it.copy(mineOnly = mineOnly) }
        reload()
    }

    /**
     * Esegue la ricerca con i filtri attuali. Con "solo i miei" serve l'id dell'utente: se non è disponibile si mostra
     * un errore invece di cercare senza filtro.
     */
    private suspend fun load() {
        _state.update { it.copy(loading = true, error = null) }
        val filter = _state.value
        val assignee = if (filter.mineOnly) session.currentUserId() else null

        if (filter.mineOnly && assignee == null) {
            _state.update { it.copy(loading = false, error = UiMessage(Res.string.error_generic)) }
            return
        }

        val result = safely { tickets.search(filter.status, filter.priority, assignee) }
        _state.update { state ->
            result.fold(
                onSuccess = { page -> state.copy(tickets = page.content, loading = false) },
                onFailure = { failure -> state.copy(loading = false, error = failure.toUiMessage()) },
            )
        }
    }
}
