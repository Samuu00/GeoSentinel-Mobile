package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.staff_queue_already_handled
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.UpdateHazardStatusRequest
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HazardQueueUiState(
    // dal server: per il gestore anche le SUBMITTED di tutti (max 50, dalla più recente)
    val hazards: List<HazardResponse> = emptyList(),
    val loading: Boolean = true,
    val loadError: UiMessage? = null,
    // una sola azione alla volta: i pulsanti delle righe si disattivano
    val busyHazardId: String? = null,
    val actionError: UiMessage? = null,
)

// ponytail: solo la prima pagina, le 50 più recenti; paginare come "Le mie segnalazioni" se la coda cresce
const val PENDING_QUEUE_SIZE = 50

/**
 * Coda delle segnalazioni del gestore: quelle di un sentiero ([trailId], verificate e da verificare) o, con [trailId]
 * null, le da verificare di tutto il parco (scheda Gestione). Il server decide le transizioni: 409/422 si mostrano e la
 * lista si rilegge.
 */
class HazardQueueViewModel(
    private val trailId: String?,
    private val hazards: HazardRepository,
    private val staff: StaffRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HazardQueueUiState())
    val state: StateFlow<HazardQueueUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init { reload() }

    fun reload() {
        _state.update { it.copy(loading = true, loadError = null) }
        load()
    }

    // Al ritorno sulla coda (da un sentiero, dove la segnalazione si può gestire): si rilegge, ma non sopra una lettura
    // o un'azione in corso (la prima lettura è quella di init)
    fun refresh() {
        if (loadJob?.isActive == true || _state.value.busyHazardId != null) return
        load()
    }

    fun change(hazard: HazardResponse, status: UpdateHazardStatusRequest.Status) {
        if (_state.value.busyHazardId != null) return
        _state.update { it.copy(busyHazardId = hazard.id, actionError = null) }
        viewModelScope.launch {
            val result = safely { staff.changeHazardStatus(hazard.id, status) }
            _state.update { state ->
                result.fold(
                    // la riga prende subito lo stato nuovo; la rilettura sotto toglie quelle che non sono più in coda
                    onSuccess = { updated -> state.copy(hazards = state.hazards.map { if (it.id == updated.id) updated else it }, busyHazardId = null) },
                    onFailure = { failure -> state.copy(busyHazardId = null, actionError = failure.toUiMessage(onRuleViolation = Res.string.staff_queue_already_handled)) },
                )
            }
            // dopo un 409/422 la lista è vecchia (un collega l'ha già gestita); dopo un successo la riga può uscire dalla coda
            load()
        }
    }

    // Una lettura alla volta: una vecchia non sovrascrive quella dopo un'azione
    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = safely {
                if (trailId != null) hazards.activeOn(trailId) else hazards.pending(page = 0, size = PENDING_QUEUE_SIZE).map { it.content }
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { list -> state.copy(hazards = list, loading = false, loadError = null) },
                    // con la lista già a schermo l'errore di una rilettura non la sostituisce
                    onFailure = { failure ->
                        if (state.hazards.isEmpty()) state.copy(loading = false, loadError = failure.toUiMessage()) else state.copy(loading = false)
                    },
                )
            }
        }
    }
}
