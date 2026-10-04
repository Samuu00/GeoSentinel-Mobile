package it.unical.geosentinel_mobile.trail.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.TrailDetailResponse
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** Una parte del dettaglio che si carica da sola (pericoli, foto). Hidden = da ospite non si chiede. */
sealed interface Section<out T> {
    data object Hidden : Section<Nothing>
    data object Loading : Section<Nothing>
    data class Loaded<T>(val value: T) : Section<T>
    data class Failed(val message: UiMessage) : Section<Nothing>
}

data class TrailDetailUiState(
    val trail: TrailDetailResponse? = null,
    // ultima conferma del server per la copia mostrata ("aggiornato alle", M3.3)
    val updatedAt: Instant? = null,
    // il dettaglio non si è caricato e non c'è una copia in cache
    val error: UiMessage? = null,
    // l'aggiornamento non è riuscito ma c'è la copia in cache: si mostra quella, con l'invito a riprovare
    val stale: UiMessage? = null,
    val pois: List<PoiResponse> = emptyList(),
    val hazards: Section<List<HazardResponse>> = Section.Hidden,
    val photos: Section<List<MediaResponse>> = Section.Hidden,
    // URL firmati per id della foto (validi 5 minuti); quelle senza URL si chiedono quando compaiono
    val photoUrls: Map<String, String> = emptyMap(),
    // foto per cui l'URL non è arrivato (rete, 429) o i tentativi sono finiti: si mostra un tocco per riprovare
    val failedPhotos: Set<String> = emptySet(),
) {
    val loading: Boolean get() = trail == null && error == null

    override fun toString() = "TrailDetailUiState(loaded=${trail != null}, error=$error, stale=$stale, hazards=$hazards, photos=$photos)"
}

class TrailDetailViewModel(
    private val trailId: String,
    private val trails: TrailRepository,
    private val hazards: HazardRepository,
    private val media: MediaRepository,
    session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TrailDetailUiState())
    val state: StateFlow<TrailDetailUiState> = _state.asStateFlow()

    // URL chiesti per foto: un URL scaduto se ne fa dare un altro, ma non all'infinito (foto rotta sullo storage)
    private val urlRequests = mutableMapOf<String, Int>()
    // Pericoli, foto e URL della sessione attuale: uno scope figlio di viewModelScope, annullato all'uscita.
    // Resta attivo anche dopo pericoli e foto, per gli URL chiesti quando le foto compaiono.
    private var loggedInScope: CoroutineScope? = null

    init {
        // La UI legge sempre dalla cache (M3.3): prima la copia salvata, poi quella aggiornata dalla rete
        viewModelScope.launch {
            trails.observeDetail(trailId).collect { cached ->
                _state.update { state ->
                    // copia sparita (404 sul server): un "non aggiornato" già arrivato diventa l'errore della schermata
                    if (cached == null) return@update state.copy(trail = null, updatedAt = null, error = state.error ?: state.stale, stale = null)
                    // un errore arrivato prima della copia in cache diventa "non aggiornato"
                    state.copy(trail = cached.value, updatedAt = cached.updatedAt, error = null, stale = state.stale ?: state.error)
                }
            }
        }
        viewModelScope.launch {
            trails.observePoisOf(trailId).collect { pois -> _state.update { it.copy(pois = pois) } }
        }
        refresh()
        // Pericoli e foto solo da loggati (le rotte vogliono il bearer): si caricano all'accesso, spariscono all'uscita
        viewModelScope.launch {
            session.state.map { it is SessionState.LoggedIn }.distinctUntilChanged().collect { loggedIn ->
                loggedInScope?.cancel()
                loggedInScope = null
                urlRequests.clear()
                _state.update { it.copy(hazards = Section.Hidden, photos = Section.Hidden, photoUrls = emptyMap(), failedPhotos = emptySet()) }
                if (loggedIn) {
                    // SupervisorJob: una sezione che fallisce non ferma le altre né i "Riprova" successivi
                    val scope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
                    loggedInScope = scope
                    scope.launch { loadHazards() }
                    scope.launch { loadPhotos() }
                }
            }
        }
    }

    fun retry() {
        if (_state.value.error != null || _state.value.stale != null) refresh()
    }

    fun retryHazards() {
        if (_state.value.hazards is Section.Failed) loggedInScope?.launch { loadHazards() }
    }

    fun retryPhotos() {
        if (_state.value.photos is Section.Failed) loggedInScope?.launch { loadPhotos() }
    }

    // Aggiorna la cache dalla rete (con l'ETag: se non è cambiato niente, 304 e solo la data nuova). Uno alla volta:
    // tocchi ripetuti su "Riprova" non moltiplicano le richieste (rate limit di 60 al minuto)
    private var refreshJob: Job? = null

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        _state.update { it.copy(error = null, stale = null) }
        refreshJob = viewModelScope.launch {
            trails.refreshDetail(trailId).onFailure { failure ->
                val message = failure.toUiMessage()
                _state.update { if (it.trail != null) it.copy(stale = message) else it.copy(error = message) }
            }
        }
        // I POI non sono essenziali: senza, il dettaglio si mostra lo stesso (con quelli in cache)
        viewModelScope.launch { trails.refreshPoisOf(trailId) }
    }

    private suspend fun loadHazards() {
        _state.update { it.copy(hazards = Section.Loading) }
        val result = safely { hazards.activeOn(trailId) }
        _state.update { it.copy(hazards = result.toSection()) }
    }

    private suspend fun loadPhotos() {
        _state.update { it.copy(photos = Section.Loading) }
        val result = safely { media.photosOfTrail(trailId) }
        _state.update { it.copy(photos = result.toSection()) }
    }

    /** La foto è sullo schermo e non ha ancora un URL. */
    fun onPhotoShown(mediaId: String) {
        if (mediaId in _state.value.photoUrls || mediaId in urlRequests) return
        requestUrl(mediaId)
    }

    /** Il download non è riuscito: di solito l'URL è scaduto (5 minuti), se ne chiede un altro. */
    fun onPhotoFailed(mediaId: String) {
        _state.update { it.copy(photoUrls = it.photoUrls - mediaId) }
        if ((urlRequests[mediaId] ?: 0) >= MaxUrlRequests) {
            // tentativi finiti: "Foto non disponibile", e un tocco riparte da capo
            _state.update { it.copy(failedPhotos = it.failedPhotos + mediaId) }
            return
        }
        requestUrl(mediaId)
    }

    /** Tocco su "Foto non disponibile": si riparte con un URL nuovo e i tentativi azzerati. */
    fun onPhotoRetry(mediaId: String) {
        urlRequests.remove(mediaId)
        _state.update { it.copy(failedPhotos = it.failedPhotos - mediaId) }
        requestUrl(mediaId)
    }

    private fun requestUrl(mediaId: String) {
        val scope = loggedInScope ?: return
        urlRequests[mediaId] = (urlRequests[mediaId] ?: 0) + 1
        scope.launch {
            safely { media.downloadUrl(mediaId) }.fold(
                onSuccess = { url -> _state.update { it.copy(photoUrls = it.photoUrls + (mediaId to url)) } },
                // senza URL la foto resterebbe in caricamento: si mostra l'errore con il tocco per riprovare
                onFailure = { _state.update { it.copy(failedPhotos = it.failedPhotos + mediaId) } },
            )
        }
    }

    // Una risposta fuori contratto (ContractViolationException) non deve far cadere l'app: diventa un errore della sezione
    private suspend fun <T> safely(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        // il primo URL più due rinnovi
        const val MaxUrlRequests = 3
    }
}

private fun <T> Result<T>.toSection(): Section<T> = fold({ Section.Loaded(it) }, { Section.Failed(it.toUiMessage()) })

private fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
