// ViewModel del form per aprire un nuovo ticket di manutenzione su un sentiero, più le regole sui campi condivise con
// la modifica di un ticket.
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

// Limiti di lunghezza di titolo e descrizione, gli stessi del server per la creazione e la modifica dei ticket.
const val TICKET_TITLE_MIN = 3
const val TICKET_TITLE_MAX = 150
const val TICKET_DESCRIPTION_MAX = 2000

/** Vero se titolo e descrizione (senza spazi ai bordi) rispettano i limiti di lunghezza. */
internal fun ticketFieldsValid(title: String, description: String): Boolean =
    title.trim().length in TICKET_TITLE_MIN..TICKET_TITLE_MAX && description.trim().length <= TICKET_DESCRIPTION_MAX

/** Stato del form del nuovo ticket. */
data class TicketCreateUiState(
    val title: String = "",
    val description: String = "",
    val priority: CreateTicketRequest.Priority = CreateTicketRequest.Priority.MEDIUM,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    val created: Boolean = false,
) {
    /** Vero se i campi sono validi e non c'è un invio in corso. */
    val canSave: Boolean get() = ticketFieldsValid(title, description) && !busy
}

/**
 * ViewModel del nuovo ticket, aperto dal dettaglio del sentiero [trailId]. Il server vuole un solo oggetto a cui
 * legare il ticket, e qui è sempre il sentiero.
 */
class TicketCreateViewModel(
    private val trailId: String,
    private val tickets: TicketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TicketCreateUiState())
    val state: StateFlow<TicketCreateUiState> = _state.asStateFlow()

    /** Aggiorna il titolo, troncato alla lunghezza massima. */
    fun onTitleChange(title: String) = _state.update { it.copy(title = title.take(TICKET_TITLE_MAX), error = null) }

    /** Aggiorna la descrizione, troncata alla lunghezza massima. */
    fun onDescriptionChange(description: String) = _state.update { it.copy(description = description.take(TICKET_DESCRIPTION_MAX), error = null) }

    /** Imposta la priorità. */
    fun onPriorityChange(priority: CreateTicketRequest.Priority) = _state.update { it.copy(priority = priority) }

    /** Crea il ticket, una volta sola; una descrizione vuota si invia come assente. */
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
