// ViewModel dei sentieri sulla mappa: carica sentieri e POI dell'area inquadrata (dalla cache e poi dalla rete),
// gestisce il foglio "Sentieri vicini" e il calcolo del percorso da A a B. È usato da MapScreen.
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
import it.unical.geosentinel_mobile.core.location.within
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

/** Stato del foglio "Sentieri vicini": cosa deve mostrare in ogni momento. */
sealed interface NearbyTrails {
    /** Foglio chiuso. */
    data object Hidden : NearbyTrails

    /** In attesa di una posizione recente; è la schermata ad aspettarla, perché ha il provider della posizione. */
    data object WaitingForFix : NearbyTrails

    /** Richiesta al server in corso. */
    data object Loading : NearbyTrails

    /** Sentieri vicini arrivati, dal più vicino. */
    data class Loaded(val trails: List<TrailSummaryResponse>) : NearbyTrails

    /** Posizione non disponibile o richiesta fallita, con il messaggio da mostrare. */
    data class Failed(val message: UiMessage) : NearbyTrails
}

/** Un punto con un nome, usato come destinazione del percorso (un POI toccato o un punto tenuto premuto sulla mappa). */
data class Place(val name: String, val point: GeoPoint)

/** Punto di partenza del percorso: la posizione dell'utente oppure il centro della mappa (un luogo scelto spostandola). */
enum class RouteOrigin { MyLocation, MapCenter }

/** Esito del calcolo del percorso. */
sealed interface RouteResult {
    /** La schermata deve fornire il punto di partenza (una posizione recente o il centro della mappa). */
    data object WaitingForOrigin : RouteResult

    /** Richiesta al server in corso. */
    data object Loading : RouteResult

    /**
     * Percorso trovato. [from] è la partenza effettiva, usata per disegnare il tratto fuori dai sentieri fino all'inizio
     * del percorso; [path] è la linea del percorso, [lengthM] la lunghezza in metri e [trailCount] il numero di
     * sentieri attraversati.
     */
    data class Found(val from: GeoPoint, val path: GeoJsonLineString, val lengthM: Double, val trailCount: Int) : RouteResult
    /** Nessun percorso o errore, con il messaggio da mostrare. */
    data class Failed(val message: UiMessage) : RouteResult
}

/** Il percorso da A a B in corso di definizione, sul modello delle app di navigazione: destinazione, partenza ed esito. */
data class RoutePlan(val destination: Place, val origin: RouteOrigin, val result: RouteResult) {
    /** Nei log niente nomi né posizioni. */
    override fun toString() = "RoutePlan(origin=$origin, result=${result::class.simpleName})"
}

/**
 * Stato immutabile dei sentieri sulla mappa.
 *
 * @property trails sentieri dell'area inquadrata, letti dalla cache.
 * @property pois punti di interesse dell'area inquadrata, letti dalla cache.
 * @property loading caricamento dei sentieri dalla rete in corso.
 * @property failed errore dell'ultimo caricamento: restano i sentieri di prima, con l'invito a riprovare.
 * @property truncated il server ha restituito il numero massimo di sentieri (TrailRepository.MAX_BBOX_RESULTS):
 * ce ne sono altri, che compaiono avvicinandosi.
 * @property zoomTooLow lo zoom è sotto la soglia da cui si caricano i sentieri.
 * @property nearby stato del foglio "Sentieri vicini".
 * @property route percorso da A a B in corso, oppure null.
 */
data class TrailMapUiState(
    val trails: List<TrailSummaryResponse> = emptyList(),
    val pois: List<PoiResponse> = emptyList(),
    val loading: Boolean = false,
    val failed: UiMessage? = null,
    val truncated: Boolean = false,
    val zoomTooLow: Boolean = false,
    val nearby: NearbyTrails = NearbyTrails.Hidden,
    val route: RoutePlan? = null,
) {
    /** Nei log solo conteggi e flag, niente nomi né posizioni. */
    override fun toString() = "TrailMapUiState(trails=${trails.size}, pois=${pois.size}, loading=$loading, failed=$failed)"
}

/**
 * ViewModel di sentieri e POI dell'area visibile, del foglio "Sentieri vicini" e del percorso da A a B.
 *
 * La schermata chiama [onCameraIdle] quando la mappa si ferma: ciò che la cache contiene per l'area viene mostrato
 * subito (anche offline) e, dopo [Debounce] senza movimenti, parte la richiesta che aggiorna la cache; un nuovo
 * movimento annulla quella in attesa o in corso. Si carica anche un margine attorno alla vista: finché la vista resta
 * dentro l'area già caricata non si chiede nulla al server, che accetta 60 richieste al minuto per utente.
 */
class TrailMapViewModel(private val repository: TrailRepository) : ViewModel() {

    /**
     * Richiesta per una vista della mappa. [force] ignora le aree già caricate (dopo "Riprova"); [attempt] rende
     * diversa ogni nuova prova, così il flow la riemette; [limit] è il riquadro dei 50 km attorno all'utente, o null.
     */
    private data class Request(val bounds: GeoBounds, val zoom: Double, val force: Boolean, val attempt: Int, val limit: GeoBounds?) {
        /** La vista ritagliata sul limite; null se la vista è tutta fuori dal limite (niente da mostrare né da chiedere). */
        val view: GeoBounds? get() = bounds.within(limit)
        /** La vista allargata del margine e ritagliata anch'essa sul limite: è l'area che si carica. */
        val area: GeoBounds? get() = view?.expandedBy(Margin)?.within(limit)
    }

    private val _state = MutableStateFlow(TrailMapUiState())
    val state: StateFlow<TrailMapUiState> = _state.asStateFlow()

    /** Richieste di caricamento dalla rete, servite con debounce. */
    private val requests = MutableStateFlow<Request?>(null)

    /** Vista attuale, per leggere la cache: qui non serve il debounce, perché è una query locale. */
    private val visible = MutableStateFlow<Request?>(null)

    /** Area (vista più margine) i cui sentieri sono già stati caricati per intero; null se va caricata. */
    private var trailsArea: GeoBounds? = null

    /** Area i cui POI sono già stati caricati per intero; null se va caricata. */
    private var poisArea: GeoBounds? = null

    /** Ultimo limite su cui è stata ripulita la cache. */
    private var prunedLimit: GeoBounds? = null

    /** Contatore dei "Riprova". */
    private var attempts = 0

    init {
        viewModelScope.launch {
            requests.filterNotNull().collectLatest { request ->
                delay(Debounce)
                load(request)
            }
        }
        // La UI legge sempre dalla cache: anche ogni aggiornamento arrivato dalla rete passa da qui.
        viewModelScope.launch {
            visible.filterNotNull().collectLatest { view ->
                val area = view.area
                if (area == null) {
                    _state.update { it.copy(trails = emptyList(), pois = emptyList()) }
                    return@collectLatest
                }
                // Si legge solo l'area dentro il limite, perché la cache può contenere dati scaricati altrove (da
                // versioni precedenti o prima che l'utente si spostasse). Sotto le soglie di zoom la lista non si
                // svuota: sono i livelli della mappa a nascondere gli elementi con una dissolvenza (minZoom e opacità in
                // TrailMapLayers), mentre svuotarla qui li farebbe sparire di colpo.
                val trails = repository.observeInBounds(area)
                val pois = repository.observePoisInBounds(area)
                combine(trails, pois) { trailList, poiList -> trailList to poiList }.collect { (trails, poiList) ->
                    _state.update { it.copy(trails = trails, pois = poiList) }
                }
            }
        }
    }

    /**
     * Da chiamare quando la mappa si ferma, con la vista [bounds] e lo [zoom]. [limit] indica fin dove si mostra e si
     * carica (i [NearRadiusMeters] attorno all'utente); null significa nessun limite. Aggiorna subito la lettura della
     * cache e accoda il caricamento dalla rete.
     */
    fun onCameraIdle(bounds: GeoBounds, zoom: Double, limit: GeoBounds? = null) {
        if (!bounds.isValid) return
        val request = Request(bounds, zoom, force = false, attempt = attempts, limit = limit)
        visible.value = request
        requests.value = request
    }

    /** "Riprova" dopo un errore: richiede la stessa vista senza fidarsi delle aree già caricate. */
    fun retry() {
        val last = requests.value ?: return
        requests.value = last.copy(force = true, attempt = ++attempts)
    }

    /**
     * Esegue un caricamento: sotto MinTrailZoom segnala solo lo zoom troppo basso; se il limite è cambiato ripulisce la
     * cache; poi carica i sentieri e, da MinPoiZoom, i POI.
     */
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
        // Limite nuovo (si è spostato l'utente o, senza posizione, il centro della mappa): la cache elimina ciò che sta
        // fuori e le aree già caricate restano valide solo nella parte dentro il limite, perché tornando indietro ciò
        // che è stato cancellato va richiesto di nuovo.
        val limit = request.limit
        if (limit != null && limit != prunedLimit) {
            trailsArea = trailsArea?.let { it intersect limit }
            poisArea = poisArea?.let { it intersect limit }
            repository.pruneOutside(limit).onSuccess { prunedLimit = limit }
        }
        // Vista tutta fuori dal limite: non c'è nulla da chiedere, e un errore precedente qui non vale più.
        val view = request.view
        val area = request.area
        if (view == null || area == null) {
            _state.update { it.copy(loading = false, failed = null, truncated = false) }
            return
        }
        loadTrails(view, area)
        if (request.zoom >= MinPoiZoom) loadPois(view, area)
    }

    /**
     * Carica i sentieri di [area] se [view] non è già coperta. View e area sono già ritagliate sul limite, come l'area
     * salvata: così il confronto regge anche quando la vista reale supera il limite, altrimenti si rifarebbe la
     * richiesta a ogni fermata della mappa.
     */
    private suspend fun loadTrails(view: GeoBounds, area: GeoBounds) {
        // Vista già coperta da un caricamento riuscito: niente rete, e un errore relativo a un'altra area non vale più.
        if (trailsArea?.contains(view) == true) {
            _state.update { it.copy(loading = false, failed = null) }
            return
        }
        _state.update { it.copy(loading = true) }
        try {
            repository.refreshInBounds(area).fold(
                onSuccess = { count ->
                    // Con il numero massimo di risultati l'area non è completa: non si salva, così il prossimo
                    // movimento chiede di nuovo.
                    val complete = count < TrailRepository.MAX_BBOX_RESULTS
                    trailsArea = if (complete) area else null
                    _state.update { it.copy(failed = null, truncated = !complete) }
                },
                onFailure = { failure -> _state.update { it.copy(failed = failure.toUiMessage()) } },
            )
        } finally {
            // Il finally vale anche quando un nuovo movimento annulla la richiesta (collectLatest): l'indicatore di
            // caricamento non resta acceso per sempre.
            _state.update { it.copy(loading = false) }
        }
    }

    /** Carica i POI di [area] se [view] non è già coperta. */
    private suspend fun loadPois(view: GeoBounds, area: GeoBounds) {
        if (poisArea?.contains(view) == true) return
        // Il server cerca i POI per raggio: si chiede quello che copre l'area, fino ai 50 km che accetta.
        repository.refreshPoisIn(area).onSuccess { complete -> poisArea = if (complete) area else null }
        // Un errore sui POI non viene mostrato: quello dei sentieri, se c'è, segnala già che manca la rete.
    }

    // Gestione del foglio "Sentieri vicini".

    /** L'utente ha aperto "Sentieri vicini": il foglio attende la posizione. */
    fun onNearbyRequested() = _state.update { it.copy(nearby = NearbyTrails.WaitingForFix) }

    /** La posizione non è arrivata: se il foglio la stava aspettando mostra l'errore. */
    fun onNearbyNoFix() = _state.update {
        if (it.nearby == NearbyTrails.WaitingForFix) it.copy(nearby = NearbyTrails.Failed(UiMessage(Res.string.trails_nearby_no_fix))) else it
    }

    /** La posizione [at] è disponibile: chiede al server i sentieri vicini. */
    fun onNearbyFix(at: GeoPoint) {
        _state.update { it.copy(nearby = NearbyTrails.Loading) }
        viewModelScope.launch {
            val result = repository.nearby(at)
            _state.update { state ->
                // Se nel frattempo il foglio è stato chiuso la risposta non lo riapre.
                if (state.nearby != NearbyTrails.Loading) return@update state
                state.copy(nearby = result.fold({ NearbyTrails.Loaded(it) }, { NearbyTrails.Failed(it.toUiMessage()) }))
            }
        }
    }

    /** Chiude il foglio "Sentieri vicini". */
    fun closeNearby() = _state.update { it.copy(nearby = NearbyTrails.Hidden) }

    // Percorso da A a B.

    /** Calcolo del percorso in corso, annullato quando il percorso cambia o viene chiuso. */
    private var routeJob: Job? = null

    /** Azione "Indicazioni" verso [destination]: si parte dalla posizione dell'utente, che la schermata deve fornire. */
    fun routeTo(destination: Place) = startRoute(RoutePlan(destination, RouteOrigin.MyLocation, RouteResult.WaitingForOrigin))

    /** Cambia il tipo di partenza; la schermata dovrà fornire il nuovo punto. */
    fun setRouteOrigin(origin: RouteOrigin) {
        val plan = _state.value.route ?: return
        startRoute(plan.copy(origin = origin, result = RouteResult.WaitingForOrigin))
    }

    /** La schermata fornisce il punto di partenza [from]: parte il calcolo del percorso sul server. */
    fun onRouteOrigin(from: GeoPoint) {
        val plan = _state.value.route?.takeIf { it.result == RouteResult.WaitingForOrigin } ?: return
        _state.update { it.copy(route = plan.copy(result = RouteResult.Loading)) }
        routeJob = viewModelScope.launch {
            val result = repository.route(from, plan.destination.point).fold(
                onSuccess = { RouteResult.Found(from, it.path, it.lengthM, it.trailIds.size) },
                onFailure = { RouteResult.Failed(it.toRouteMessage()) },
            )
            _state.update { state ->
                // Se nel frattempo il percorso è stato chiuso o cambiato, la risposta vecchia non vale più.
                if (state.route != plan.copy(result = RouteResult.Loading)) state else state.copy(route = plan.copy(result = result))
            }
        }
    }

    /** La posizione di partenza non è arrivata: il pannello mostra l'errore. */
    fun onRouteNoFix() = _state.update { state ->
        val plan = state.route?.takeIf { it.result == RouteResult.WaitingForOrigin } ?: return@update state
        state.copy(route = plan.copy(result = RouteResult.Failed(UiMessage(Res.string.trails_nearby_no_fix))))
    }

    /** Chiude il percorso. */
    fun closeRoute() = startRoute(null)

    /** Annulla il calcolo in corso e imposta il nuovo piano (null per chiuderlo). */
    private fun startRoute(plan: RoutePlan?) {
        routeJob?.cancel()
        _state.update { it.copy(route = plan) }
    }

    /** Soglie e limiti usati dal caricamento, condivisi con MapScreen e con i livelli della mappa. */
    companion object {
        /** Attesa dopo l'ultimo movimento della mappa prima di caricare. */
        val Debounce = 400.milliseconds

        /**
         * Zoom minimo da cui si caricano i sentieri. A zoom 9 la vista copre circa 50 km: si disegnano solo i sentieri
         * vicini e non tutta la Calabria, che a zoom più bassi rallentava la mappa su iPhone.
         */
        const val MinTrailZoom = 9.0

        /**
         * Zoom minimo da cui si caricano i POI (la scala di un massiccio). A zoom 9 la vista supera i 50 km accettati dal
         * server, quindi la risposta è parziale (fino a 300 POI, i più vicini al centro) e quelli ai bordi arrivano
         * avvicinandosi; sulla mappa quelli che si sovrappongono vengono nascosti.
         */
        const val MinPoiZoom = 9.0

        /** Margine caricato attorno alla vista su ogni lato, come frazione della sua larghezza e altezza. */
        private const val Margin = 0.5

        /**
         * Distanza dall'utente (o dal centro della mappa, senza posizione) entro cui si mostrano e si caricano sentieri,
         * POI, rifugi e pericoli. Il riquadro corrispondente è calcolato in MapScreen ed è lo stesso per i tre ViewModel.
         */
        const val NearRadiusMeters = 50_000.0
    }
}

/** Messaggio per un errore del percorso: "nessun percorso" per lo slug `no-route`, altrimenti quello generico. */
private fun Throwable.toRouteMessage(): UiMessage =
    if (((this as? ApiException)?.error as? ApiError.BusinessRule)?.slug == "no-route") UiMessage(Res.string.route_none) else toUiMessage()

/** Converte un errore in un messaggio per l'utente, con un testo generico se non è un errore dell'API. */
private fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
