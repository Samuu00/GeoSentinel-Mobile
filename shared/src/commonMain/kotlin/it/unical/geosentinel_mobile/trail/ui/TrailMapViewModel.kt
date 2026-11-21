package it.unical.geosentinel_mobile.trail.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.route_none
import geosentinel_mobile.shared.generated.resources.trails_nearby_no_fix
import it.unical.geosentinel_mobile.api.model.GeoJsonLineString
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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

/** Un punto con un nome: la destinazione del percorso (un POI toccato o un punto tenuto premuto sulla mappa). */
data class Place(val name: String, val point: GeoPoint)

/** Da dove parte il percorso: dalla posizione dell'utente o dal centro della mappa (un posto scelto spostandola). */
enum class RouteOrigin { MyLocation, MapCenter }

sealed interface RouteResult {
    // la schermata deve dare il punto di partenza (un fix recente o il centro della mappa)
    data object WaitingForOrigin : RouteResult
    data object Loading : RouteResult
    // from: la partenza vera, per il tratto fuori dai sentieri fino all'inizio del percorso
    data class Found(val from: GeoPoint, val path: GeoJsonLineString, val lengthM: Double, val trailCount: Int) : RouteResult
    data class Failed(val message: UiMessage) : RouteResult
}

/** Il percorso A→B come su Google Maps: destinazione, partenza e risultato. */
data class RoutePlan(val destination: Place, val origin: RouteOrigin, val result: RouteResult) {
    // niente nomi né posizioni nei log
    override fun toString() = "RoutePlan(origin=$origin, result=${result::class.simpleName})"
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
    val route: RoutePlan? = null,
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
                // Anche da lontano: la cache ha solo quello già scaricato (la Calabria intera sono circa 350 sentieri).
                // Sotto le soglie li nascondono i livelli, con una dissolvenza (minZoom e opacità in TrailMapLayers):
                // svuotare la lista qui li faceva sparire di colpo
                val trails = repository.observeInBounds(area)
                val pois = repository.observePoisInBounds(area)
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

    // ---- Percorso A→B ----

    private var routeJob: Job? = null

    /** "Indicazioni": si parte dalla posizione dell'utente, la schermata aspetta il fix. */
    fun routeTo(destination: Place) = startRoute(RoutePlan(destination, RouteOrigin.MyLocation, RouteResult.WaitingForOrigin))

    fun setRouteOrigin(origin: RouteOrigin) {
        val plan = _state.value.route ?: return
        startRoute(plan.copy(origin = origin, result = RouteResult.WaitingForOrigin))
    }

    fun onRouteOrigin(from: GeoPoint) {
        val plan = _state.value.route?.takeIf { it.result == RouteResult.WaitingForOrigin } ?: return
        _state.update { it.copy(route = plan.copy(result = RouteResult.Loading)) }
        routeJob = viewModelScope.launch {
            val result = repository.route(from, plan.destination.point).fold(
                onSuccess = { RouteResult.Found(from, it.path, it.lengthM, it.trailIds.size) },
                onFailure = { RouteResult.Failed(it.toRouteMessage()) },
            )
            _state.update { state ->
                // chiuso o cambiato nel frattempo: la risposta vecchia non vale più
                if (state.route != plan.copy(result = RouteResult.Loading)) state else state.copy(route = plan.copy(result = result))
            }
        }
    }

    fun onRouteNoFix() = _state.update { state ->
        val plan = state.route?.takeIf { it.result == RouteResult.WaitingForOrigin } ?: return@update state
        state.copy(route = plan.copy(result = RouteResult.Failed(UiMessage(Res.string.trails_nearby_no_fix))))
    }

    fun closeRoute() = startRoute(null)

    private fun startRoute(plan: RoutePlan?) {
        routeJob?.cancel()
        _state.update { it.copy(route = plan) }
    }

    companion object {
        val Debounce = 400.milliseconds
        // A 9 la vista è di circa 50 km: si disegnano solo i sentieri vicini, non tutta la Calabria (a 7 laggava su iPhone)
        const val MinTrailZoom = 9.0
        // I POI da un massiccio in giù. A 9 la vista supera i 50 km del server: la risposta è parziale (fino a 300, i
        // più vicini al centro) e quelli ai bordi arrivano avvicinandosi; quelli che si sovrappongono si nascondono
        const val MinPoiZoom = 9.0
        // Margine caricato attorno alla vista, su ogni lato
        private const val Margin = 0.5
    }
}

private fun Throwable.toRouteMessage(): UiMessage =
    if (((this as? ApiException)?.error as? ApiError.BusinessRule)?.slug == "no-route") UiMessage(Res.string.route_none) else toUiMessage()

private fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
