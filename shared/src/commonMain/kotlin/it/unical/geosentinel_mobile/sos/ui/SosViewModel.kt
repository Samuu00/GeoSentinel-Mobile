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

/** L'annullamento in corso e il suo errore: l'unico stato della schermata che non sta nel controller. */
data class CancelUi(val running: Boolean = false, val error: UiMessage? = null)

/** La schermata SOS sopra [SosController], che vive più a lungo di lei (l'invio continua anche fuori). */
class SosViewModel(private val controller: SosController, val session: StateFlow<SessionState>) : ViewModel() {
    val state: StateFlow<SosState> = controller.state

    private val _cancel = MutableStateFlow(CancelUi())
    val cancel: StateFlow<CancelUi> = _cancel.asStateFlow()

    fun raise(at: GeoPoint, medicalNotes: String) {
        // il controller lo fa girare nel suo scope: uscire dalla schermata non interrompe l'SOS
        viewModelScope.launch { controller.raise(at, medicalNotes) }
    }

    /** M6.2: rilegge l'allerta attiva (la chiama la schermata ogni 30 s, finché è aperta). */
    suspend fun refresh() = controller.refresh()

    fun cancelSos() {
        if (_cancel.value.running) return
        _cancel.value = CancelUi(running = true)
        viewModelScope.launch {
            val error = controller.cancel().exceptionOrNull()?.let { (it as? ApiException)?.error ?: ApiError.Network }
            _cancel.value = CancelUi(error = error?.uiMessage())
        }
    }
}
