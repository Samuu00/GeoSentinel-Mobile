// ViewModel dei rifugi mostrati sulla mappa principale. Carica i rifugi dell'area visibile quando la mappa si ferma,
// evitando richieste inutili quando la vista resta dentro un'area già caricata.
package it.unical.geosentinel_mobile.refuge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.within
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
 * ViewModel dei rifugi sulla mappa, attivo solo da loggati ([setEnabled]).
 *
 * Funziona come per i POI: quando la mappa resta ferma per [Debounce], chiede i rifugi nel cerchio che contiene la
 * vista più un margine. Se la vista è ancora dentro l'area già caricata non richiede nulla, perché i rifugi cambiano
 * di rado. Gli errori non si mostrano: l'avviso dei sentieri segnala già la rete assente, e restano i rifugi di prima.
 */
class RefugeMapViewModel(private val refuges: RefugeRepository) : ViewModel() {

    /** Vista della mappa da caricare. [limit] è il riquadro di 50 km attorno all'utente; null vuol dire nessun limite. */
    private data class Request(val bounds: GeoBounds, val zoom: Double, val limit: GeoBounds?)

    private val _state = MutableStateFlow<List<RefugeResponse>>(emptyList())
    val state: StateFlow<List<RefugeResponse>> = _state.asStateFlow()

    // Ultima vista da caricare (null: niente da fare), l'ultima vista ricevuta (anche da disattivato), l'area già caricata
    // per intero e il flag di attivazione.
    private val requests = MutableStateFlow<Request?>(null)
    private var lastView: Request? = null
    private var loaded: GeoBounds? = null
    private var enabled = false

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

    /** Attiva o disattiva i rifugi sulla mappa. Disattivando si svuota tutto; attivando si carica l'ultima vista. */
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

    /** Chiamata quando la mappa si ferma: registra la vista e, se attivo, la mette in coda da caricare. */
    fun onCameraIdle(bounds: GeoBounds, zoom: Double, limit: GeoBounds? = null) {
        if (!bounds.isValid) return
        val request = Request(bounds, zoom, limit)
        lastView = request
        if (enabled) requests.value = request
    }

    /**
     * Carica i rifugi per la vista [request]. Sotto [MinZoom] o fuori dal limite la lista si svuota. Il risultato
     * conta come area caricata per intero solo se il server non ha tagliato la lista (meno del massimo di risultati)
     * e il raggio è dentro il limite del server.
     */
    private suspend fun load(request: Request) {
        if (request.zoom < MinZoom) {
            _state.value = emptyList()
            // La lista ora è vuota: tornando a uno zoom più vicino nella stessa area bisogna ricaricare.
            loaded = null
            return
        }
        // Vista e area si ritagliano sul limite dei 50 km. Anche l'area salvata è ritagliata, così il confronto con
        // la vista funziona pure vicino al bordo del limite.
        val view = request.bounds.within(request.limit)
        val area = view?.expandedBy(Margin)?.within(request.limit)
        if (view == null || area == null) {
            _state.value = emptyList()
            loaded = null
            return
        }
        if (loaded?.contains(view) == true) return
        val radius = area.radiusMeters
        refuges.nearby(area.center, radius).onSuccess { list ->
            val complete = list.size < MAX_NEARBY_REFUGES && radius <= MAX_REFUGES_RADIUS_METERS
            loaded = if (complete) area else null
            _state.value = list
        }
    }

    companion object {
        /** Tempo di mappa ferma prima di caricare. */
        val Debounce = 400.milliseconds
        /** Zoom minimo per mostrare i rifugi, lo stesso dei pericoli: a zoom 7 l'intera Calabria sta sullo schermo. */
        const val MinZoom = 7.0
        /** Margine aggiunto alla vista su ogni lato, come frazione della sua misura. */
        private const val Margin = 0.5
    }
}
