// ViewModel del cambio di stato di un sentiero, nella sezione "Gestione" del suo dettaglio.
package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
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

/** Stato del cambio di stato del sentiero. */
data class TrailStatusUiState(
    /** Stato richiesto, in attesa della risposta del server; null se non c'è una richiesta in corso. */
    val pending: UpdateTrailStatusRequest.Status? = null,
    val error: UiMessage? = null,
) {
    /** Vero mentre una richiesta è in corso. */
    val busy: Boolean get() = pending != null
}

/**
 * ViewModel del cambio di stato del sentiero [trailId] per il gestore. La risposta del server si salva nella cache del
 * catalogo, da cui leggono sia il dettaglio sia la mappa.
 */
class TrailStatusViewModel(
    private val trailId: String,
    private val staff: StaffRepository,
    private val trails: TrailRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrailStatusUiState())
    val state: StateFlow<TrailStatusUiState> = _state.asStateFlow()

    /** Chiede al server di portare il sentiero allo stato [status]; ignora la richiesta se ce n'è già una in corso. */
    fun change(status: UpdateTrailStatusRequest.Status) {
        if (_state.value.busy) return
        _state.update { it.copy(pending = status, error = null) }
        viewModelScope.launch {
            val result = safely { staff.changeTrailStatus(trailId, status) }
            // La cache è solo un aiuto: se la scrittura non riesce, il dettaglio si aggiorna alla lettura successiva.
            result.onSuccess { trails.saveDetail(it) }
            // Un 422 indica una transizione non ammessa (per esempio da chiuso ad attenzione): la regola sta sul
            // server e l'app non la duplica, il messaggio spiega solo cosa fare.
            _state.update { it.copy(pending = null, error = result.exceptionOrNull()?.toUiMessage(onRuleViolation = Res.string.staff_trail_status_rejected)) }
        }
    }
}
