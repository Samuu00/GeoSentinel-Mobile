package it.unical.geosentinel_mobile.hazard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.trails_nearby_no_fix
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import it.unical.geosentinel_mobile.hazard.data.MAX_NEARBY_HAZARDS
import it.unical.geosentinel_mobile.hazard.data.MAX_NEARBY_HAZARDS_RADIUS_METERS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** Cosa mostra il foglio "Pericoli vicini". */
sealed interface NearbyHazards {
    data object Hidden : NearbyHazards
    // in attesa di un fix recente (lo aspetta la schermata, che ha il provider della posizione)
    data object WaitingForFix : NearbyHazards
    data object Loading : NearbyHazards
    // from: il punto da cui si misurano le distanze
    data class Loaded(val hazards: List<HazardResponse>, val from: GeoPoint) : NearbyHazards {
        override fun toString() = "Loaded(hazards=${hazards.size})"
    }
    data class Failed(val message: UiMessage) : NearbyHazards
}

data class HazardMapUiState(
    // il livello della mappa: vuoto per l'ospite, sotto lo zoom minimo e senza rete
    val hazards: List<HazardResponse> = emptyList(),
    // ultimo caricamento del livello fallito: si mostra l'avviso con "riprova"
    val failed: Boolean = false,
    // il pericolo toccato sulla mappa o scelto dalla lista, mostrato nella card
    val selected: HazardResponse? = null,
    val nearby: NearbyHazards = NearbyHazards.Hidden,
) {
    // niente descrizioni né posizioni nei log
    override fun toString() = "HazardMapUiState(hazards=${hazards.size}, failed=$failed, selected=${selected != null}, nearby=$nearby)"
}

/**
 * Pericoli attivi sulla mappa e "Pericoli vicini" (M4.4), solo da loggati ([setEnabled]). Il livello si carica come i POI:
 * a mappa ferma, dopo [Debounce], per il cerchio che contiene la vista più un margine. Finché la vista resta dentro
 * l'area già caricata e il caricamento è recente non si richiama il server. Niente cache su disco: un pericolo vecchio
 * mostrato come attivo inganna, quindi senza rete il livello resta vuoto (con l'avviso).
 */
class HazardMapViewModel(private val hazards: HazardRepository) : ViewModel() {

    private data class Request(val bounds: GeoBounds, val zoom: Double, val attempt: Int)
    private class LoadedArea(val bounds: GeoBounds, val at: TimeSource.Monotonic.ValueTimeMark)

    private val _state = MutableStateFlow(HazardMapUiState())
    val state: StateFlow<HazardMapUiState> = _state.asStateFlow()

    private val requests = MutableStateFlow<Request?>(null)
    // l'ultima vista, anche da ospite: al login il livello si carica senza aspettare un movimento
    private var lastView: Request? = null
    private var loaded: LoadedArea? = null
    private var enabled = false
    private var attempts = 0

    init {
        viewModelScope.launch {
            // null = spento: annulla anche una richiesta in corso
            requests.collectLatest { request ->
                if (request == null) return@collectLatest
                delay(Debounce)
                load(request)
            }
        }
    }

    /** Da loggati sì, da ospite no (il server risponderebbe 401). Al logout il livello e i fogli spariscono. */
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) {
            requests.value = lastView
        } else {
            requests.value = null
            loaded = null
            _state.value = HazardMapUiState()
        }
    }

    fun onCameraIdle(bounds: GeoBounds, zoom: Double) {
        if (!bounds.isValid) return
        val request = Request(bounds, zoom, attempts)
        lastView = request
        if (enabled) requests.value = request
    }

    fun retry() {
        val last = lastView ?: return
        if (!enabled) return
        loaded = null
        requests.value = last.copy(attempt = ++attempts)
    }

    private suspend fun load(request: Request) {
        if (request.zoom < MinZoom) {
            _state.update { it.copy(hazards = emptyList(), failed = false) }
            return
        }
        val previous = loaded
        if (previous != null && request.bounds in previous.bounds && previous.at.elapsedNow() < MaxAge) return
        val area = request.bounds.expandedBy(Margin)
        val radius = area.radiusMeters
        hazards.nearby(area.center, radius).fold(
            onSuccess = { list ->
                // 50 risultati o raggio tagliato dal server: l'area non è completa, il prossimo movimento chiede di nuovo
                val complete = list.size < MAX_NEARBY_HAZARDS && radius <= MAX_NEARBY_HAZARDS_RADIUS_METERS
                loaded = if (complete) LoadedArea(area, TimeSource.Monotonic.markNow()) else null
                _state.update { it.copy(hazards = list, failed = false) }
            },
            onFailure = {
                loaded = null
                _state.update { it.copy(hazards = emptyList(), failed = true) }
            },
        )
    }

    fun select(hazard: HazardResponse) = _state.update { it.copy(selected = hazard) }

    fun select(id: String) = _state.update { state -> state.copy(selected = state.hazards.firstOrNull { it.id == id }) }

    fun clearSelection() = _state.update { it.copy(selected = null) }

    // ---- Pericoli vicini ----

    fun onNearbyRequested() {
        if (enabled) _state.update { it.copy(nearby = NearbyHazards.WaitingForFix) }
    }

    fun onNearbyNoFix() = _state.update {
        if (it.nearby == NearbyHazards.WaitingForFix) it.copy(nearby = NearbyHazards.Failed(UiMessage(Res.string.trails_nearby_no_fix))) else it
    }

    fun onNearbyFix(at: GeoPoint) {
        _state.update { it.copy(nearby = NearbyHazards.Loading) }
        viewModelScope.launch {
            val result = hazards.nearby(at)
            _state.update { state ->
                // chiuso nel frattempo (o logout): la risposta non riapre il foglio
                if (state.nearby != NearbyHazards.Loading) return@update state
                state.copy(nearby = result.fold({ NearbyHazards.Loaded(it, at) }, { NearbyHazards.Failed(it.toUiMessage()) }))
            }
        }
    }

    fun closeNearby() = _state.update { it.copy(nearby = NearbyHazards.Hidden) }

    companion object {
        val Debounce = 400.milliseconds
        // come i sentieri: più lontano la vista copre mezza regione
        const val MinZoom = 8.0
        // dopo, anche dentro l'area già caricata si richiede: i pericoli cambiano spesso
        val MaxAge = 5.minutes
        private const val Margin = 0.5
    }
}

private fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
