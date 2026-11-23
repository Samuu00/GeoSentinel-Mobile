// ViewModel dei pericoli sulla mappa: carica i pericoli attivi della zona inquadrata, le proprie segnalazioni ancora in
// verifica, il pericolo selezionato e il foglio "Pericoli vicini". È usato da MapScreen.
package it.unical.geosentinel_mobile.hazard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.trails_nearby_no_fix
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.within
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import it.unical.geosentinel_mobile.hazard.data.MAX_NEARBY_HAZARDS
import it.unical.geosentinel_mobile.hazard.data.MAX_NEARBY_HAZARDS_RADIUS_METERS
import it.unical.geosentinel_mobile.core.network.safely
import kotlinx.coroutines.Job
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

/** Stato del foglio "Pericoli vicini": cosa deve mostrare in ogni momento. */
sealed interface NearbyHazards {
    /** Foglio chiuso. */
    data object Hidden : NearbyHazards

    /** In attesa di una posizione recente; è la schermata ad aspettarla, perché è lei ad avere il provider della posizione. */
    data object WaitingForFix : NearbyHazards

    /** Posizione ottenuta, richiesta al server in corso. */
    data object Loading : NearbyHazards

    /** Lista arrivata; [from] è il punto da cui si misurano le distanze mostrate. */
    data class Loaded(val hazards: List<HazardResponse>, val from: GeoPoint) : NearbyHazards {
        /** Nei log solo il numero di pericoli, mai posizioni o descrizioni. */
        override fun toString() = "Loaded(hazards=${hazards.size})"
    }

    /** Posizione non disponibile o richiesta fallita, con il messaggio da mostrare. */
    data class Failed(val message: UiMessage) : NearbyHazards
}

/**
 * Stato immutabile dei pericoli sulla mappa.
 *
 * @property hazards i pericoli attivi della zona inquadrata; la lista è vuota per l'ospite, sotto lo zoom minimo e
 * senza rete.
 * @property failed true se l'ultimo caricamento è fallito: la mappa mostra l'avviso con "Riprova".
 * @property selected il pericolo toccato sulla mappa o scelto dalla lista, mostrato nella card.
 * @property nearby lo stato del foglio "Pericoli vicini".
 * @property mine le proprie segnalazioni ancora in verifica, mostrate attenuate finché un gestore non le verifica.
 */
data class HazardMapUiState(
    val hazards: List<HazardResponse> = emptyList(),
    val failed: Boolean = false,
    val selected: HazardResponse? = null,
    val nearby: NearbyHazards = NearbyHazards.Hidden,
    val mine: List<HazardResponse> = emptyList(),
) {
    /** Pericoli da disegnare: quelli verificati più le proprie segnalazioni in verifica, senza doppioni. */
    val shown: List<HazardResponse> get() = hazards + mine.filter { own -> hazards.none { it.id == own.id } }

    /** Rappresentazione per i log: solo conteggi e flag, niente descrizioni né posizioni. */
    override fun toString() =
        "HazardMapUiState(hazards=${hazards.size}, mine=${mine.size}, failed=$failed, selected=${selected != null}, nearby=$nearby)"
}

/**
 * ViewModel dei pericoli sulla mappa e del foglio "Pericoli vicini", attivo solo per l'utente loggato ([setEnabled]).
 *
 * Il livello si carica con la stessa strategia dei POI: quando la mappa si ferma, dopo [Debounce], si chiedono i
 * pericoli nel cerchio che contiene la vista allargata di un margine. Finché la vista resta dentro l'area già caricata
 * e il caricamento è recente (meno di [MaxAge]) il server non viene richiamato. Non c'è cache su disco: un pericolo
 * vecchio mostrato come attivo sarebbe fuorviante, quindi senza rete il livello resta vuoto e compare un avviso.
 */
class HazardMapViewModel(private val hazards: HazardRepository) : ViewModel() {

    /**
     * Richiesta di caricamento per una vista della mappa. [attempt] cambia a ogni "Riprova", così una richiesta uguale
     * alla precedente viene comunque riemessa dal flow. [limit] è il riquadro dei 50 km attorno all'utente
     * (TrailMapViewModel.NearRadiusMeters); null significa nessun limite.
     */
    private data class Request(val bounds: GeoBounds, val zoom: Double, val attempt: Int, val limit: GeoBounds?)

    /** Area già caricata per intero e momento del caricamento, per evitare richieste ripetute. */
    private class LoadedArea(val bounds: GeoBounds, val at: TimeSource.Monotonic.ValueTimeMark)

    private val _state = MutableStateFlow(HazardMapUiState())
    val state: StateFlow<HazardMapUiState> = _state.asStateFlow()

    /** Ultima richiesta da servire; null quando il livello è spento. */
    private val requests = MutableStateFlow<Request?>(null)

    /** Ultima vista ricevuta, salvata anche da ospite: dopo il login il livello si carica senza aspettare un movimento. */
    private var lastView: Request? = null

    /** Area caricata per intero, oppure null se va ricaricata. */
    private var loaded: LoadedArea? = null

    /** true quando l'utente è loggato e il livello è attivo. */
    private var enabled = false

    /** Contatore dei "Riprova", copiato in Request.attempt. */
    private var attempts = 0

    init {
        viewModelScope.launch {
            // collectLatest annulla il caricamento precedente quando arriva una vista nuova; il delay fa da debounce.
            // Un valore null (livello spento) annulla anche una richiesta in corso.
            requests.collectLatest { request ->
                if (request == null) return@collectLatest
                delay(Debounce)
                load(request)
            }
        }
    }

    /**
     * Attiva il livello per l'utente loggato e lo spegne per l'ospite, per il quale il server risponderebbe 401.
     * All'attivazione carica subito l'ultima vista e le proprie segnalazioni; al logout svuota tutto lo stato, quindi
     * spariscono anche card e fogli.
     */
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) {
            requests.value = lastView
            refreshMine()
        } else {
            requests.value = null
            loaded = null
            _state.value = HazardMapUiState()
        }
    }

    /**
     * Da chiamare quando la mappa smette di muoversi, con la vista [bounds], lo [zoom] e l'eventuale [limit] dei 50 km.
     * Se il livello è attivo avvia il caricamento (con debounce).
     */
    fun onCameraIdle(bounds: GeoBounds, zoom: Double, limit: GeoBounds? = null) {
        if (!bounds.isValid) return
        val request = Request(bounds, zoom, attempts, limit)
        lastView = request
        if (enabled) requests.value = request
    }

    /** "Riprova" dopo un errore: dimentica l'area caricata e richiede l'ultima vista. */
    fun retry() {
        val last = lastView ?: return
        if (!enabled) return
        loaded = null
        requests.value = last.copy(attempt = ++attempts)
    }

    /**
     * Carica i pericoli per una richiesta. Sotto lo zoom minimo o fuori dal limite svuota il livello; se la vista è già
     * coperta da un caricamento recente non fa nulla; altrimenti chiede al server i pericoli nel cerchio che contiene
     * l'area allargata.
     */
    private suspend fun load(request: Request) {
        if (request.zoom < MinZoom) {
            _state.update { it.copy(hazards = emptyList(), failed = false) }
            // La lista ora è vuota, quindi tornando a uno zoom maggiore nella stessa area bisogna ricaricare.
            loaded = null
            return
        }
        // Vista e area da caricare vengono ritagliate sul limite dei 50 km. Anche l'area salvata in loaded è ritagliata,
        // quindi il confronto "vista contenuta nell'area" resta corretto quando la vista sporge oltre il bordo.
        val view = request.bounds.within(request.limit)
        val area = view?.expandedBy(Margin)?.within(request.limit)
        if (view == null || area == null) {
            _state.update { it.copy(hazards = emptyList(), failed = false) }
            loaded = null
            return
        }
        val previous = loaded
        if (previous != null && view in previous.bounds && previous.at.elapsedNow() < MaxAge) return
        val radius = area.radiusMeters
        hazards.nearby(area.center, radius).fold(
            onSuccess = { list ->
                // Se il server ha restituito il numero massimo di risultati, o il raggio supera quello che accetta (e
                // quindi lo ha ridotto), l'area non è stata caricata per intero: non si salva, così il prossimo
                // movimento chiede di nuovo.
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

    /**
     * Lettura in corso delle proprie segnalazioni. Ce n'è una alla volta: setEnabled e il ritorno sulla mappa possono
     * chiederla insieme, e vince l'ultima.
     */
    private var mineJob: Job? = null

    /**
     * Aggiorna le proprie segnalazioni in verifica, prese dalla prima pagina di "Le mie segnalazioni" (le più recenti).
     * La mappa la chiama all'accesso e a ogni ritorno sulla schermata, così una segnalazione appena inviata compare
     * subito. In caso di errore restano quelle di prima.
     */
    fun refreshMine() {
        if (!enabled) return
        mineJob?.cancel()
        mineJob = viewModelScope.launch {
            safely { hazards.mine(page = 0) }.onSuccess { page ->
                if (enabled) _state.update { it.copy(mine = page.content.filter { h -> h.status == HazardResponse.Status.SUBMITTED }) }
            }
        }
    }

    /** Seleziona il pericolo scelto dalla lista "Pericoli vicini". */
    fun select(hazard: HazardResponse) = _state.update { it.copy(selected = hazard) }

    /** Seleziona il pericolo toccato sulla mappa, cercandolo per id tra quelli disegnati. */
    fun select(id: String) = _state.update { state -> state.copy(selected = state.shown.firstOrNull { it.id == id }) }

    /** Chiude la card del pericolo selezionato. */
    fun clearSelection() = _state.update { it.copy(selected = null) }

    // Gestione del foglio "Pericoli vicini".

    /** L'utente ha aperto "Pericoli vicini": il foglio attende la posizione (solo da loggati). */
    fun onNearbyRequested() {
        if (enabled) _state.update { it.copy(nearby = NearbyHazards.WaitingForFix) }
    }

    /** La schermata non è riuscita a ottenere la posizione: se il foglio la stava aspettando mostra l'errore. */
    fun onNearbyNoFix() = _state.update {
        if (it.nearby == NearbyHazards.WaitingForFix) it.copy(nearby = NearbyHazards.Failed(UiMessage(Res.string.trails_nearby_no_fix))) else it
    }

    /**
     * Richiesta di "Pericoli vicini" in corso. Si annulla chiudendo il foglio o ripartendo da un altro punto, così una
     * risposta vecchia non riempie un foglio riaperto altrove, con le distanze misurate dal punto sbagliato.
     */
    private var nearbyJob: Job? = null

    /** La posizione [at] è disponibile: chiede al server i pericoli vicini e aggiorna il foglio. */
    fun onNearbyFix(at: GeoPoint) {
        _state.update { it.copy(nearby = NearbyHazards.Loading) }
        nearbyJob?.cancel()
        nearbyJob = viewModelScope.launch {
            val result = hazards.nearby(at)
            _state.update { state ->
                // Se nel frattempo il foglio è stato chiuso (o c'è stato il logout) la risposta non lo riapre.
                if (state.nearby != NearbyHazards.Loading) return@update state
                state.copy(nearby = result.fold({ NearbyHazards.Loaded(it, at) }, { NearbyHazards.Failed(it.toUiMessage()) }))
            }
        }
    }

    /** Chiude il foglio "Pericoli vicini" e annulla la richiesta in corso. */
    fun closeNearby() {
        nearbyJob?.cancel()
        _state.update { it.copy(nearby = NearbyHazards.Hidden) }
    }

    /** Soglie e tempi del caricamento, condivisi con i livelli della mappa. */
    companion object {
        /** Attesa dopo l'ultimo movimento della mappa prima di caricare. */
        val Debounce = 400.milliseconds

        /** Zoom minimo da cui si mostrano i pericoli: a zoom 7 l'intera Calabria sta sullo schermo. */
        const val MinZoom = 7.0

        /** Età oltre la quale si richiede di nuovo anche un'area già caricata, perché i pericoli cambiano spesso. */
        val MaxAge = 5.minutes

        /** Margine aggiunto alla vista su ogni lato, come frazione della sua larghezza e altezza. */
        private const val Margin = 0.5
    }
}

/** Converte un errore in un messaggio per l'utente, con un testo generico se non è un errore dell'API. */
private fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
