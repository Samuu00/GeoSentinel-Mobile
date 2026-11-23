// ViewModel della coda delle segnalazioni di pericolo per il gestore del parco, con le azioni per verificarle o
// rifiutarle.
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

/** Stato della coda delle segnalazioni. */
data class HazardQueueUiState(
    /** Segnalazioni ricevute dal server; per il gestore comprendono anche quelle `SUBMITTED` di tutti gli utenti. */
    val hazards: List<HazardResponse> = emptyList(),
    val loading: Boolean = true,
    val loadError: UiMessage? = null,
    /** Id della segnalazione su cui è in corso un'azione: se ne esegue una alla volta e i pulsanti delle righe si disattivano. */
    val busyHazardId: String? = null,
    val actionError: UiMessage? = null,
)

// Numero di segnalazioni da verificare chieste al server. Limite noto: si legge solo la prima pagina, cioè le 50 più
// recenti; se la coda dovesse crescere andrebbe paginata come "Le mie segnalazioni".
const val PENDING_QUEUE_SIZE = 50

/**
 * ViewModel della coda delle segnalazioni del gestore. Con [trailId] mostra quelle di un sentiero (verificate e da
 * verificare); con [trailId] null quelle da verificare di tutto il parco, nella scheda Gestione. Le transizioni ammesse
 * le decide il server: un 409 o un 422 si mostra e la lista si rilegge.
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

    /** Ricarica la coda mostrando il caricamento, per esempio con "Riprova". */
    fun reload() {
        _state.update { it.copy(loading = true, loadError = null) }
        load()
    }

    /**
     * Rilegge in silenzio la coda al ritorno da un sentiero, dove la segnalazione si può gestire. Non fa nulla se è in
     * corso una lettura (come la prima, avviata in init) o un'azione.
     */
    fun refresh() {
        if (loadJob?.isActive == true || _state.value.busyHazardId != null) return
        load()
    }

    /** Porta la segnalazione [hazard] allo stato [status], poi rilegge la coda. */
    fun change(hazard: HazardResponse, status: UpdateHazardStatusRequest.Status) {
        if (_state.value.busyHazardId != null) return
        _state.update { it.copy(busyHazardId = hazard.id, actionError = null) }
        viewModelScope.launch {
            val result = safely { staff.changeHazardStatus(hazard.id, status) }
            _state.update { state ->
                result.fold(
                    // La riga mostra subito il nuovo stato; la rilettura successiva toglie quelle non più in coda.
                    onSuccess = { updated -> state.copy(hazards = state.hazards.map { if (it.id == updated.id) updated else it }, busyHazardId = null) },
                    onFailure = { failure -> state.copy(busyHazardId = null, actionError = failure.toUiMessage(onRuleViolation = Res.string.staff_queue_already_handled)) },
                )
            }
            // Dopo un 409 o un 422 la lista è superata (un collega ha già gestito la segnalazione); dopo un successo la
            // riga può uscire dalla coda. In entrambi i casi si rilegge.
            load()
        }
    }

    /** Legge la coda dal server. Una lettura alla volta: una vecchia non deve sovrascrivere quella successiva a un'azione. */
    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = safely {
                if (trailId != null) hazards.activeOn(trailId) else hazards.pending(page = 0, size = PENDING_QUEUE_SIZE).map { it.content }
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { list -> state.copy(hazards = list, loading = false, loadError = null) },
                    // Se la lista è già a schermo, l'errore di una rilettura non la sostituisce.
                    onFailure = { failure ->
                        if (state.hazards.isEmpty()) state.copy(loading = false, loadError = failure.toUiMessage()) else state.copy(loading = false)
                    },
                )
            }
        }
    }
}
