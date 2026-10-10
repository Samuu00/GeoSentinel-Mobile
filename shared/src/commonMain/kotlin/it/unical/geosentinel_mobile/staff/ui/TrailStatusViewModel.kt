package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.staff_trail_status_rejected
import it.unical.geosentinel_mobile.api.model.UpdateTrailStatusRequest
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrailStatusUiState(
    // lo stato chiesto, finché il server non risponde
    val pending: UpdateTrailStatusRequest.Status? = null,
    val error: UiMessage? = null,
) {
    val busy: Boolean get() = pending != null
}

// Stato del sentiero per il gestore. La risposta va nella cache: dettaglio e mappa la leggono da lì.
class TrailStatusViewModel(
    private val trailId: String,
    private val staff: StaffRepository,
    private val trails: TrailRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrailStatusUiState())
    val state: StateFlow<TrailStatusUiState> = _state.asStateFlow()

    fun change(status: UpdateTrailStatusRequest.Status) {
        if (_state.value.busy) return
        _state.update { it.copy(pending = status, error = null) }
        viewModelScope.launch {
            val result = safely { staff.changeTrailStatus(trailId, status) }
            // la cache è un aiuto: se non si scrive, il dettaglio si aggiorna al prossimo giro
            result.onSuccess { trails.saveDetail(it) }
            // 422 (CLOSED -> WARNING): lo dice il server, l'app non duplica la regola; il messaggio spiega solo cosa fare
            _state.update { it.copy(pending = null, error = result.exceptionOrNull()?.toUiMessage(onRuleViolation = Res.string.staff_trail_status_rejected)) }
        }
    }
}
