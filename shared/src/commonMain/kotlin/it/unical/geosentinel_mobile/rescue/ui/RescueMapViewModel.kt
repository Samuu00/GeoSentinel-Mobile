package it.unical.geosentinel_mobile.rescue.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.rescue.data.MAX_ALERTS_RADIUS_METERS
import it.unical.geosentinel_mobile.rescue.data.MAX_NEARBY_ALERTS
import it.unical.geosentinel_mobile.rescue.data.RescueRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Le allerte aperte sulla mappa (M7.2), solo per gli operatori ([setEnabled]). Come i pericoli: a mappa ferma, dopo
 * [Debounce], il cerchio che contiene la vista più un margine; dentro l'area già caricata si richiede solo dopo [MaxAge],
 * perché le allerte cambiano spesso. A mappa ferma le rilegge [refresh] (ogni 30 s e a ogni push di un SOS nuovo).
 * Un errore non si mostra: la lista della scheda Allerte resta la fonte completa.
 */
class RescueMapViewModel(private val rescue: RescueRepository) : ViewModel() {

    // attempt: due refresh sulla stessa vista sono due richieste diverse per lo StateFlow
    private data class Request(val bounds: GeoBounds, val zoom: Double, val attempt: Int = 0)
    private class LoadedArea(val bounds: GeoBounds, val at: TimeSource.Monotonic.ValueTimeMark)

    private val _state = MutableStateFlow<List<SosAlertResponse>>(emptyList())
    val state: StateFlow<List<SosAlertResponse>> = _state.asStateFlow()

    // la vista è più grande di quanto il server dà in una richiesta (100 km, 50 allerte): ne possono mancare, la mappa
    // chiede di avvicinarsi
    private val _partial = MutableStateFlow(false)
    val partial: StateFlow<Boolean> = _partial.asStateFlow()

    private val requests = MutableStateFlow<Request?>(null)
    private var lastView: Request? = null
    private var loaded: LoadedArea? = null
    private var enabled = false
    private var attempts = 0

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
            _partial.value = false
        }
    }

    /** Rilegge la vista attuale anche dentro l'area già caricata: SOS nuovi, squadre inviate, allerte chiuse. */
    fun refresh() {
        val last = lastView ?: return
        if (!enabled) return
        loaded = null
        requests.value = last.copy(attempt = ++attempts)
    }

    fun onCameraIdle(bounds: GeoBounds, zoom: Double) {
        if (!bounds.isValid) return
        val request = Request(bounds, zoom, attempts)
        lastView = request
        if (enabled) requests.value = request
    }

    private suspend fun load(request: Request) {
        if (request.zoom < MinZoom) {
            _state.value = emptyList()
            _partial.value = true
            // la lista è vuota: tornando vicino nella stessa area si deve ricaricare
            loaded = null
            return
        }
        val previous = loaded
        if (previous != null && request.bounds in previous.bounds && previous.at.elapsedNow() < MaxAge) return
        val area = request.bounds.expandedBy(Margin)
        val radius = area.radiusMeters
        rescue.nearby(area.center, radius).onSuccess { list ->
            val complete = list.size < MAX_NEARBY_ALERTS && radius <= MAX_ALERTS_RADIUS_METERS
            loaded = if (complete) LoadedArea(area, TimeSource.Monotonic.markNow()) else null
            _state.value = list
            _partial.value = !complete
        }
    }

    companion object {
        val Debounce = 400.milliseconds
        // più in basso che per i pericoli: le allerte sono poche e l'operatore guarda tutta la zona
        const val MinZoom = 7.0
        val MaxAge = 1.minutes
        private const val Margin = 0.5
    }
}
