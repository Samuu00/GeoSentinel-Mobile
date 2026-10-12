package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.api.model.CreateTicketRequest
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.staff.data.TicketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// I limiti del backend (CreateTicketRequest e UpdateTicketRequest)
const val TICKET_TITLE_MIN = 3
const val TICKET_TITLE_MAX = 150
const val TICKET_DESCRIPTION_MAX = 2000

internal fun ticketFieldsValid(title: String, description: String): Boolean =
    title.trim().length in TICKET_TITLE_MIN..TICKET_TITLE_MAX && description.trim().length <= TICKET_DESCRIPTION_MAX

data class TicketCreateUiState(
    val title: String = "",
    val description: String = "",
    val priority: CreateTicketRequest.Priority = CreateTicketRequest.Priority.MEDIUM,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    val created: Boolean = false,
) {
    val canSave: Boolean get() = ticketFieldsValid(title, description) && !busy
}

// Nuovo ticket legato a un sentiero (il backend vuole un solo target): dal dettaglio del sentiero
class TicketCreateViewModel(
    private val trailId: String,
    private val tickets: TicketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TicketCreateUiState())
    val state: StateFlow<TicketCreateUiState> = _state.asStateFlow()

    fun onTitleChange(title: String) = _state.update { it.copy(title = title.take(TICKET_TITLE_MAX), error = null) }

    fun onDescriptionChange(description: String) = _state.update { it.copy(description = description.take(TICKET_DESCRIPTION_MAX), error = null) }

    fun onPriorityChange(priority: CreateTicketRequest.Priority) = _state.update { it.copy(priority = priority) }

    fun save() {
        val current = _state.value
        if (!current.canSave || current.created) return

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val request = CreateTicketRequest(
                title = current.title.trim(),
                priority = current.priority,
                description = current.description.trim().takeIf { it.isNotEmpty() },
                trailId = trailId,
            )
            val result = safely { tickets.create(request) }
            _state.update { it.copy(busy = false, created = result.isSuccess, error = result.exceptionOrNull()?.toUiMessage()) }
        }
    }
}
