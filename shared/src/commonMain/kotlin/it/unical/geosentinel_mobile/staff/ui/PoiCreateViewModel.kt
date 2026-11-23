// ViewModel del form per creare un nuovo punto di interesse, aperto dal dettaglio di un sentiero o dalla mappa.
package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.api.model.CreatePoiRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Lunghezza minima e massima del nome del POI, le stesse imposte dal server.
const val POI_NAME_MIN = 2
const val POI_NAME_MAX = 100

/** Stato del form di creazione del POI. */
data class PoiCreateUiState(
    val name: String = "",
    val type: CreatePoiRequest.Type = CreatePoiRequest.Type.INFORMATION,
    /** Posizione scelta dal gestore (il centro della mappa); finché manca il form non si può inviare. */
    val location: GeoPoint? = null,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    /** POI creato: la schermata torna indietro. */
    val created: Boolean = false,
) {
    /** Vero se il nome ha una lunghezza valida, c'è una posizione e non c'è un invio in corso. */
    val canSave: Boolean get() = name.trim().length in POI_NAME_MIN..POI_NAME_MAX && location != null && !busy

    // toString scritto a mano: la posizione non deve finire nei log.
    override fun toString() = "PoiCreateUiState(type=$type, hasLocation=${location != null}, busy=$busy, error=$error, created=$created)"
}

/** ViewModel della creazione di un POI; [trailId] è il sentiero a cui legarlo, null se si crea dalla mappa. */
class PoiCreateViewModel(
    private val trailId: String?,
    private val staff: StaffRepository,
    private val trails: TrailRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PoiCreateUiState())
    val state: StateFlow<PoiCreateUiState> = _state.asStateFlow()

    /** Aggiorna il nome, troncato alla lunghezza massima. */
    fun onNameChange(name: String) = _state.update { it.copy(name = name.take(POI_NAME_MAX), error = null) }
    /** Imposta il tipo di POI. */
    fun onTypeChange(type: CreatePoiRequest.Type) = _state.update { it.copy(type = type) }
    /** Imposta la posizione scelta sulla mappa. */
    fun onLocationPicked(location: GeoPoint) = _state.update { it.copy(location = location) }

    /** Invia il nuovo POI al server, una volta sola. */
    fun save() {
        val current = _state.value
        if (!current.canSave || current.created) return
        val location = current.location ?: return
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            val result = safely { staff.createPoi(current.name, current.type, location, trailId) }
            // Il POI creato si salva subito in cache: mappa e dettaglio del sentiero lo mostrano senza aspettare la rete.
            result.onSuccess { trails.savePoi(it) }
            _state.update { it.copy(busy = false, created = result.isSuccess, error = result.exceptionOrNull()?.toUiMessage()) }
        }
    }
}
