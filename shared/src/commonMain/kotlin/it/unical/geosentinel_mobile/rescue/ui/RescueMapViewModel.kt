// ViewModel delle allerte SOS mostrate sulla mappa principale agli operatori del soccorso. Carica le allerte della
// vista quando la mappa si ferma e le rilegge periodicamente, perché cambiano spesso.
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
 * ViewModel delle allerte aperte sulla mappa, attivo solo per gli operatori ([setEnabled]).
 *
 * Funziona come per i pericoli: quando la mappa resta ferma per [Debounce] chiede le allerte nel cerchio che contiene
 * la vista più un margine. Dentro l'area già caricata richiede di nuovo solo dopo [MaxAge], perché le allerte cambiano
 * spesso. Con la mappa ferma le rilegge [refresh], chiamata ogni 30 secondi e a ogni notifica push di un nuovo SOS.
 * Gli errori non si mostrano: la fonte completa resta la lista della scheda Allerte.
 */
class RescueMapViewModel(private val rescue: RescueRepository) : ViewModel() {

    /**
     * Vista da caricare. [attempt] rende diverse due richieste sulla stessa vista: lo StateFlow ignora un valore
     * uguale al precedente, e senza questo campo un secondo refresh non partirebbe.
     */
    private data class Request(val bounds: GeoBounds, val zoom: Double, val attempt: Int = 0)
    /** Area caricata per intero e momento del caricamento, per sapere quando è troppo vecchia. */
    private class LoadedArea(val bounds: GeoBounds, val at: TimeSource.Monotonic.ValueTimeMark)

    private val _state = MutableStateFlow<List<SosAlertResponse>>(emptyList())
    val state: StateFlow<List<SosAlertResponse>> = _state.asStateFlow()

    // Vero se la vista supera quanto il server restituisce in una richiesta (100 km, 50 allerte) o lo zoom è troppo
    // basso: alcune allerte possono mancare e la mappa invita ad avvicinarsi.
    private val _partial = MutableStateFlow(false)
    val partial: StateFlow<Boolean> = _partial.asStateFlow()

    // Vista da caricare, ultima vista ricevuta, area già caricata, flag di attivazione e contatore dei refresh.
    private val requests = MutableStateFlow<Request?>(null)
    private var lastView: Request? = null
    private var loaded: LoadedArea? = null
    private var enabled = false
    private var attempts = 0

    // Attende che la mappa resti ferma per Debounce; collectLatest annulla l'attesa se arriva una vista nuova.
    init {
        viewModelScope.launch {
            requests.collectLatest { request ->
                if (request == null) return@collectLatest
                delay(Debounce)
                load(request)
            }
        }
    }

    /** Attiva o disattiva le allerte sulla mappa. Disattivando si svuota tutto; attivando si carica l'ultima vista. */
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

    /** Rilegge la vista attuale anche dentro l'area già caricata, per vedere SOS nuovi, squadre inviate e allerte chiuse. */
    fun refresh() {
        val last = lastView ?: return
        if (!enabled) return
        loaded = null
        requests.value = last.copy(attempt = ++attempts)
    }

    /** Chiamata quando la mappa si ferma: registra la vista e, se attivo, la mette in coda da caricare. */
    fun onCameraIdle(bounds: GeoBounds, zoom: Double) {
        if (!bounds.isValid) return
        val request = Request(bounds, zoom, attempts)
        lastView = request
        if (enabled) requests.value = request
    }

    /**
     * Carica le allerte per la vista [request]. Sotto [MinZoom] la lista si svuota. Si salta la richiesta se la vista
     * è dentro un'area caricata da meno di [MaxAge]; il risultato conta come area completa solo se il server non ha
     * tagliato la lista e il raggio è dentro il suo limite.
     */
    private suspend fun load(request: Request) {
        if (request.zoom < MinZoom) {
            _state.value = emptyList()
            _partial.value = true
            // La lista ora è vuota: tornando a uno zoom più vicino nella stessa area bisogna ricaricare.
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
        /** Tempo di mappa ferma prima di caricare. */
        val Debounce = 400.milliseconds
        /** Zoom minimo per mostrare le allerte: sono poche e l'operatore deve poter vedere tutta la zona. */
        const val MinZoom = 7.0
        /** Età massima dell'area caricata prima di richiederla di nuovo. */
        val MaxAge = 1.minutes
        /** Margine aggiunto alla vista su ogni lato, come frazione della sua misura. */
        private const val Margin = 0.5
    }
}
