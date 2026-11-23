// ViewModel della schermata SOS. Fa da tramite verso SosController, che vive per tutta la durata dell'app, e tiene
// solo lo stato dell'annullamento.
package it.unical.geosentinel_mobile.sos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.sos.data.SosState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Annullamento in corso e suo eventuale errore: è l'unico stato della schermata che non sta nel controller. */
data class CancelUi(val running: Boolean = false, val error: UiMessage? = null)

/**
 * ViewModel della schermata SOS, costruito sopra [SosController]. Il controller vive più a lungo della schermata, così
 * l'invio dell'SOS continua anche se l'utente la chiude. [session] serve alla schermata per sapere se l'utente è loggato.
 */
class SosViewModel(private val controller: SosController, val session: StateFlow<SessionState>) : ViewModel() {
    val state: StateFlow<SosState> = controller.state

    private val _cancel = MutableStateFlow(CancelUi())
    val cancel: StateFlow<CancelUi> = _cancel.asStateFlow()

    /** Lancia l'SOS dalla posizione [at] con le note mediche indicate. */
    fun raise(at: GeoPoint, medicalNotes: String) {
        // Il controller esegue l'invio nel proprio scope: uscire dalla schermata non interrompe l'SOS.
        viewModelScope.launch { controller.raise(at, medicalNotes) }
    }

    /** Rilegge l'allerta attiva; la schermata la chiama ogni 30 secondi finché è aperta. */
    suspend fun refresh() = controller.refresh()

    /**
     * Annulla l'SOS tramite il controller. Un errore che non viene dal server si mostra come errore di rete, perché
     * l'annullamento potrebbe non essere arrivato.
     */
    fun cancelSos() {
        if (_cancel.value.running) return
        _cancel.value = CancelUi(running = true)
        viewModelScope.launch {
            val error = controller.cancel().exceptionOrNull()?.let { (it as? ApiException)?.error ?: ApiError.Network }
            _cancel.value = CancelUi(error = error?.uiMessage())
        }
    }
}
