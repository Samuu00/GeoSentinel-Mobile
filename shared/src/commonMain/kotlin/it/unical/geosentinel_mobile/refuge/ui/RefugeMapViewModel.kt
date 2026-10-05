package it.unical.geosentinel_mobile.refuge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.refuge.data.MAX_NEARBY_REFUGES
import it.unical.geosentinel_mobile.refuge.data.MAX_REFUGES_RADIUS_METERS
import it.unical.geosentinel_mobile.refuge.data.RefugeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * I rifugi sulla mappa (M5.1), solo da loggati ([setEnabled]). Come i POI: a mappa ferma, dopo [Debounce], il cerchio
 * che contiene la vista più un margine; dentro l'area già caricata non si richiede (i rifugi cambiano di rado). Un errore
 * non si mostra: l'avviso dei sentieri dice già se manca la rete, e i rifugi restano quelli di prima.
 */
class RefugeMapViewModel(private val refuges: RefugeRepository) : ViewModel() {

    private data class Request(val bounds: GeoBounds, val zoom: Double)

    private val _state = MutableStateFlow<List<RefugeResponse>>(emptyList())
    val state: StateFlow<List<RefugeResponse>> = _state.asStateFlow()

    private val requests = MutableStateFlow<Request?>(null)
    private var lastView: Request? = null
    private var loaded: GeoBounds? = null
    private var enabled = false

    init {
        viewModelScope.launch {
            requests.collectLatest { request ->
                if (request == null) return@collectLatest
                delay(Debounce)
                load(request)
            }
        }
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) {
            requests.value = lastView
        } else {
            requests.value = null
            loaded = null
            _state.value = emptyList()
        }
    }

    fun onCameraIdle(bounds: GeoBounds, zoom: Double) {
        if (!bounds.isValid) return
        val request = Request(bounds, zoom)
        lastView = request
        if (enabled) requests.value = request
    }

    private suspend fun load(request: Request) {
        if (request.zoom < MinZoom) {
            _state.value = emptyList()
            return
        }
        if (loaded?.contains(request.bounds) == true) return
        val area = request.bounds.expandedBy(Margin)
        val radius = area.radiusMeters
        refuges.nearby(area.center, radius).onSuccess { list ->
            val complete = list.size < MAX_NEARBY_REFUGES && radius <= MAX_REFUGES_RADIUS_METERS
            loaded = if (complete) area else null
            _state.value = list
        }
    }

    companion object {
        val Debounce = 400.milliseconds
        const val MinZoom = 8.0
        private const val Margin = 0.5
    }
}
