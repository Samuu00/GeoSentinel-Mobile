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

// Come il backend: nome tra 2 e 100 caratteri
const val POI_NAME_MIN = 2
const val POI_NAME_MAX = 100

data class PoiCreateUiState(
    val name: String = "",
    val type: CreatePoiRequest.Type = CreatePoiRequest.Type.INFORMATION,
    // il centro della mappa scelto dal gestore: senza, il form non si invia
    val location: GeoPoint? = null,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    // creato: la schermata torna indietro
    val created: Boolean = false,
) {
    val canSave: Boolean get() = name.trim().length in POI_NAME_MIN..POI_NAME_MAX && location != null && !busy

    // niente posizione nei log (§4)
    override fun toString() = "PoiCreateUiState(type=$type, hasLocation=${location != null}, busy=$busy, error=$error, created=$created)"
}

class PoiCreateViewModel(
    private val trailId: String?,
    private val staff: StaffRepository,
    private val trails: TrailRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PoiCreateUiState())
    val state: StateFlow<PoiCreateUiState> = _state.asStateFlow()

    fun onNameChange(name: String) = _state.update { it.copy(name = name.take(POI_NAME_MAX), error = null) }
    fun onTypeChange(type: CreatePoiRequest.Type) = _state.update { it.copy(type = type) }
    fun onLocationPicked(location: GeoPoint) = _state.update { it.copy(location = location) }

    fun save() {
        val current = _state.value
        if (!current.canSave || current.created) return
        val location = current.location ?: return
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            val result = safely { staff.createPoi(current.name, current.type, location, trailId) }
            // subito in cache: la mappa e il dettaglio del sentiero lo mostrano senza aspettare la rete
            result.onSuccess { trails.savePoi(it) }
            _state.update { it.copy(busy = false, created = result.isSuccess, error = result.exceptionOrNull()?.toUiMessage()) }
        }
    }
}
