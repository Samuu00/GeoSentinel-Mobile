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

data class RefugeCapacityUiState(
    // ultima capacità confermata dal server
    val capacity: Int,
    // testo del campo: solo cifre
    val draft: String,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    val saved: Boolean = false,
) {
    // da 1 a 1000 posti, come il backend
    val parsed: Int? get() = draft.toIntOrNull()?.takeIf { it in 1..MAX_REFUGE_CAPACITY }
    val canSave: Boolean get() = parsed != null && parsed != capacity && !busy
}

// Capacità del proprio rifugio. Il server rifiuta sotto gli ospiti confermati (409 capacity-exceeded) e la schermata lo mostra.
class RefugeCapacityViewModel(
    private val refugeId: String,
    initialCapacity: Int,
    private val staff: StaffRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RefugeCapacityUiState(capacity = initialCapacity, draft = initialCapacity.toString()))
    val state: StateFlow<RefugeCapacityUiState> = _state.asStateFlow()

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text.filter(Char::isDigit).take(MaxDigits), saved = false, error = null) }

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

// Il messaggio generico di capacity-exceeded parla di prenotazioni ("non ci sono posti nelle date scelte"): qui no
private fun Throwable.toCapacityMessage(): UiMessage {
    val error = (this as? ApiException)?.error
    return if (error is ApiError.Conflict && error.slug == "capacity-exceeded") UiMessage(Res.string.staff_capacity_below_booked) else toUiMessage()
}

// il tetto del backend (UpdateCapacityRequest)
const val MAX_REFUGE_CAPACITY = 1000

// 4 cifre bastano per arrivare al tetto, e il testo resta sempre un Int valido
private const val MaxDigits = 4
