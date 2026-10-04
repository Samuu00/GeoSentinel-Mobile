package it.unical.geosentinel_mobile.trail.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.trails_nearby_no_fix
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Cosa mostra il foglio "Sentieri vicini". */
sealed interface NearbyTrails {
    data object Hidden : NearbyTrails
    // in attesa di un fix recente (lo aspetta la schermata, che ha il provider della posizione)
    data object WaitingForFix : NearbyTrails
    data object Loading : NearbyTrails
    data class Loaded(val trails: List<TrailSummaryResponse>) : NearbyTrails
    data class Failed(val message: UiMessage) : NearbyTrails
}

data class TrailMapUiState(
    val trails: List<TrailSummaryResponse> = emptyList(),
    val pois: List<PoiResponse> = emptyList(),
    val loading: Boolean = false,
    // ultimo caricamento fallito: restano i sentieri di prima, con l'invito a riprovare
    val failed: UiMessage? = null,
    // il server ne ha dati 200 (il massimo): ce ne sono altri, avvicinandosi si vedono tutti
    val truncated: Boolean = false,
    val zoomTooLow: Boolean = false,
    val nearby: NearbyTrails = NearbyTrails.Hidden,
) {
    // niente nomi né posizioni nei log
    override fun toString() = "TrailMapUiState(trails=${trails.size}, pois=${pois.size}, loading=$loading, failed=$failed)"
}

/**
 * Sentieri e POI dell'area visibile. La schermata chiama [onCameraIdle] quando la mappa si ferma: quello che c'è in
 * cache per l'area si mostra subito (anche offline, M3.3), e dopo [Debounce] senza movimenti parte la richiesta che
 * aggiorna la cache (un movimento nuovo annulla quella in attesa o in corso). Si carica un margine attorno alla
 * vista: finché la vista resta dentro l'area già caricata non si chiede nulla al server (rate limit di 60 richieste
 * al minuto per utente).
 */
class TrailMapViewModel(private val repository: TrailRepository) : ViewModel() {

    private data class Request(val bounds: GeoBounds, val zoom: Double, val force: Boolean, val attempt: Int)

    private val _state = MutableStateFlow(TrailMapUiState())
    val state: StateFlow<TrailMapUiState> = _state.asStateFlow()

    private val requests = MutableStateFlow<Request?>(null)
    // La vista attuale, per leggere la cache: niente debounce, è una query locale
    private val visible = MutableStateFlow<Request?>(null)
    // Aree già caricate (vista più margine); null = da caricare
    private var trailsArea: GeoBounds? = null
    private var poisArea: GeoBounds? = null
    private var attempts = 0

    init {
        viewModelScope.launch {
            requests.filterNotNull().collectLatest { request ->
                delay(Debounce)
                load(request)
            }
        }
        // La UI legge sempre dalla cache: ogni aggiornamento della rete arriva da qui
        viewModelScope.launch {
            visible.filterNotNull().collectLatest { view ->
                val area = view.bounds.expandedBy(Margin)
                // da lontano niente: la cache dell'area intera potrebbe avere migliaia di sentieri (si invita ad avvicinarsi)
                val trails = if (view.zoom >= MinTrailZoom) repository.observeInBounds(area) else flowOf(emptyList())
                val pois = if (view.zoom >= MinPoiZoom) repository.observePoisInBounds(area) else flowOf(emptyList())
                combine(trails, pois) { trailList, poiList -> trailList to poiList }.collect { (trails, poiList) ->
                    _state.update { it.copy(trails = trails, pois = poiList) }
                }
            }
        }
    }

    fun onCameraIdle(bounds: GeoBounds, zoom: Double) {
        if (!bounds.isValid) return
        val request = Request(bounds, zoom, force = false, attempt = attempts)
        visible.value = request
        requests.value = request
    }

    // Dopo un errore: stessa vista, senza fidarsi delle aree già caricate
    fun retry() {
        val last = requests.value ?: return
        requests.value = last.copy(force = true, attempt = ++attempts)
    }

    private suspend fun load(request: Request) {
        if (request.zoom < MinTrailZoom) {
            _state.update { it.copy(zoomTooLow = true, loading = false) }
            return
        }
        _state.update { it.copy(zoomTooLow = false) }
        if (request.force) {
            trailsArea = null
            poisArea = null
        }
        loadTrails(request.bounds)
        if (request.zoom >= MinPoiZoom) loadPois(request.bounds)
    }

    private suspend fun loadTrails(view: GeoBounds) {
        // vista già coperta da un caricamento riuscito: niente rete, e un errore di un'altra area non vale più qui
        if (trailsArea?.contains(view) == true) {
            _state.update { it.copy(loading = false, failed = null) }
            return
        }
        val area = view.expandedBy(Margin)
        _state.update { it.copy(loading = true) }
        try {
            repository.refreshInBounds(area).fold(
                onSuccess = { count ->
                    // 200 risultati: l'area non è completa, il prossimo movimento chiede di nuovo
                    val complete = count < TrailRepository.MAX_BBOX_RESULTS
                    trailsArea = if (complete) area else null
                    _state.update { it.copy(failed = null, truncated = !complete) }
                },
                onFailure = { failure -> _state.update { it.copy(failed = failure.toUiMessage()) } },
            )
        } finally {
            // anche quando un movimento nuovo annulla la richiesta (collectLatest): niente "Carico…" per sempre
            _state.update { it.copy(loading = false) }
        }
    }

    private suspend fun loadPois(view: GeoBounds) {
        if (poisArea?.contains(view) == true) return
        val area = view.expandedBy(Margin)
        // I POI arrivano per raggio: quello che copre l'area, fino ai 50 km del server
        repository.refreshPoisIn(area).onSuccess { complete -> poisArea = if (complete) area else null }
        // un errore sui POI non si mostra: quello dei sentieri, se c'è, dice già che la rete manca
    }

    // ---- Sentieri vicini ----

    fun onNearbyRequested() = _state.update { it.copy(nearby = NearbyTrails.WaitingForFix) }

    fun onNearbyNoFix() = _state.update {
        if (it.nearby == NearbyTrails.WaitingForFix) it.copy(nearby = NearbyTrails.Failed(UiMessage(Res.string.trails_nearby_no_fix))) else it
    }

    fun onNearbyFix(at: GeoPoint) {
        _state.update { it.copy(nearby = NearbyTrails.Loading) }
        viewModelScope.launch {
            val result = repository.nearby(at)
            _state.update { state ->
                // chiuso nel frattempo: la risposta non riapre il foglio
                if (state.nearby != NearbyTrails.Loading) return@update state
                state.copy(nearby = result.fold({ NearbyTrails.Loaded(it) }, { NearbyTrails.Failed(it.toUiMessage()) }))
            }
        }
    }

    fun closeNearby() = _state.update { it.copy(nearby = NearbyTrails.Hidden) }

    companion object {
        val Debounce = 400.milliseconds
        // Sotto, la vista copre mezza regione: troppi sentieri da scaricare e da disegnare
        const val MinTrailZoom = 8.0
        // I POI sono puntini: servono solo da vicino
        const val MinPoiZoom = 12.0
        // Margine caricato attorno alla vista, su ogni lato
        private const val Margin = 0.5
    }
}

private fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
