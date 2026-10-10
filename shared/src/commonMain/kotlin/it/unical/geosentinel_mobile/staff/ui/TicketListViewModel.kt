package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
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

data class TicketListUiState(
    val tickets: List<TicketResponse> = emptyList(),
    val loading: Boolean = true,
    val error: UiMessage? = null,
    val status: MaintenanceApi.StatusSearchTickets? = null,
    val priority: MaintenanceApi.PrioritySearchTickets? = null,
    // solo i ticket assegnati a chi è loggato
    val mineOnly: Boolean = false,
)

// Lista dei ticket, con filtri. Un cambio di filtro o un "Riprova" annulla la richiesta in corso.
class TicketListViewModel(
    private val tickets: TicketRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TicketListUiState())
    val state: StateFlow<TicketListUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { reload() }

    fun reload() {
        job?.cancel()
        job = viewModelScope.launch { load() }
    }

    // Al ritorno dal dettaglio: si rilegge, ma non sopra una lettura già in corso (la prima, all'apertura)
    fun refresh() {
        if (job?.isActive != true) reload()
    }

    fun onStatusChange(status: MaintenanceApi.StatusSearchTickets?) {
        _state.update { it.copy(status = status) }
        reload()
    }

    fun onPriorityChange(priority: MaintenanceApi.PrioritySearchTickets?) {
        _state.update { it.copy(priority = priority) }
        reload()
    }

    fun onMineChange(mineOnly: Boolean) {
        _state.update { it.copy(mineOnly = mineOnly) }
        reload()
    }

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
