// ViewModel della schermata in cui il gestore di un rifugio (o un amministratore) cambia il numero di posti letto.
package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.staff_capacity_below_booked
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stato del form dei posti letto. */
data class RefugeCapacityUiState(
    /** Ultima capacità confermata dal server. */
    val capacity: Int,
    /** Testo del campo, composto solo da cifre. */
    val draft: String,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    val saved: Boolean = false,
) {
    /** Valore del campo se è un numero valido (da 1 a [MAX_REFUGE_CAPACITY], come sul server), altrimenti null. */
    val parsed: Int? get() = draft.toIntOrNull()?.takeIf { it in 1..MAX_REFUGE_CAPACITY }
    /** Si può salvare un valore valido, diverso da quello attuale, senza un invio in corso. */
    val canSave: Boolean get() = parsed != null && parsed != capacity && !busy
}

/**
 * ViewModel della capacità del rifugio [refugeId]. Il server rifiuta un valore inferiore agli ospiti già confermati
 * (409 `capacity-exceeded`) e la schermata mostra il motivo.
 */
class RefugeCapacityViewModel(
    private val refugeId: String,
    initialCapacity: Int,
    private val staff: StaffRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RefugeCapacityUiState(capacity = initialCapacity, draft = initialCapacity.toString()))
    val state: StateFlow<RefugeCapacityUiState> = _state.asStateFlow()

    /** Aggiorna il testo del campo tenendo solo le cifre, al massimo [MaxDigits]. */
    fun onDraftChange(text: String) = _state.update { it.copy(draft = text.filter(Char::isDigit).take(MaxDigits), saved = false, error = null) }

    /** Invia la nuova capacità; se riesce, il campo prende il valore confermato dal server. */
    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val capacity = current.parsed ?: return

        _state.update { it.copy(busy = true, error = null, saved = false) }
        viewModelScope.launch {
            val result = safely { staff.updateRefugeCapacity(refugeId, capacity) }
            _state.update { state ->
                result.fold(
                    onSuccess = { refuge -> state.copy(busy = false, capacity = refuge.capacity, draft = refuge.capacity.toString(), saved = true) },
                    onFailure = { failure -> state.copy(busy = false, error = failure.toCapacityMessage()) },
                )
            }
        }
    }
}

/**
 * Messaggio per un errore del salvataggio. Il messaggio generico di `capacity-exceeded` parla di prenotazioni ("non ci
 * sono posti nelle date scelte"), che qui non c'entrano: lo si sostituisce con una frase sugli ospiti già prenotati.
 */
private fun Throwable.toCapacityMessage(): UiMessage {
    val error = (this as? ApiException)?.error
    return if (error is ApiError.Conflict && error.slug == "capacity-exceeded") UiMessage(Res.string.staff_capacity_below_booked) else toUiMessage()
}

/** Numero massimo di posti letto accettato dal server. */
const val MAX_REFUGE_CAPACITY = 1000

// Quattro cifre bastano per arrivare al massimo, e il testo resta sempre convertibile in un Int.
private const val MaxDigits = 4
