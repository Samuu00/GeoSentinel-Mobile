// ViewModel della pagina di dettaglio di un sentiero: dati del sentiero e POI dalla cache, aggiornati dalla rete, più
// pericoli attivi e foto, che si caricano solo per l'utente loggato.
package it.unical.geosentinel_mobile.trail.ui

import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
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

/** Stato di una parte del dettaglio che si carica in modo indipendente (pericoli, foto). */
sealed interface Section<out T> {
    /** Sezione nascosta: per l'ospite non viene nemmeno richiesta. */
    data object Hidden : Section<Nothing>

    /** Caricamento in corso. */
    data object Loading : Section<Nothing>

    /** Dati arrivati. */
    data class Loaded<T>(val value: T) : Section<T>

    /** Caricamento fallito, con il messaggio da mostrare accanto a "Riprova". */
    data class Failed(val message: UiMessage) : Section<Nothing>
}

/**
 * Stato immutabile della pagina di dettaglio di un sentiero.
 *
 * @property trail il sentiero mostrato, oppure null finché non è disponibile.
 * @property updatedAt ultima conferma del server per la copia mostrata ("aggiornato alle").
 * @property error errore quando il dettaglio non si è caricato e non esiste una copia in cache.
 * @property stale errore quando l'aggiornamento non è riuscito ma c'è la copia in cache: si mostra quella, con l'invito
 * a riprovare.
 * @property pois i punti di interesse del sentiero.
 * @property hazards i pericoli attivi del sentiero.
 * @property photos le foto del sentiero.
 * @property photoUrls URL firmati (validi 5 minuti) per id della foto; quelli mancanti si chiedono quando la foto compare.
 * @property failedPhotos foto il cui URL non è arrivato (rete, 429) o che hanno esaurito i tentativi: si mostra un
 * tocco per riprovare.
 */
data class TrailDetailUiState(
    val trail: TrailDetailResponse? = null,
    val updatedAt: Instant? = null,
    val error: UiMessage? = null,
    val stale: UiMessage? = null,
    val pois: List<PoiResponse> = emptyList(),
    val hazards: Section<List<HazardResponse>> = Section.Hidden,
    val photos: Section<List<MediaResponse>> = Section.Hidden,
    val photoUrls: Map<String, String> = emptyMap(),
    val failedPhotos: Set<String> = emptySet(),
) {
    /** true finché non c'è né il sentiero né un errore. */
    val loading: Boolean get() = trail == null && error == null

    /** Rappresentazione per i log, senza contenuti. */
    override fun toString() = "TrailDetailUiState(loaded=${trail != null}, error=$error, stale=$stale, hazards=$hazards, photos=$photos)"
}

/**
 * ViewModel del dettaglio del sentiero [trailId]. La UI legge sempre dalla cache (prima la copia salvata, poi quella
 * aggiornata dalla rete). Pericoli e foto richiedono il login: si caricano quando l'utente accede e spariscono quando
 * esce, seguendo lo stato di [session].
 */
class TrailDetailViewModel(
    private val trailId: String,
    private val trails: TrailRepository,
    private val hazards: HazardRepository,
    private val media: MediaRepository,
    session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TrailDetailUiState())
    val state: StateFlow<TrailDetailUiState> = _state.asStateFlow()

    /**
     * Numero di URL chiesti per ogni foto. Un URL scaduto viene sostituito con uno nuovo, ma non all'infinito: una
     * foto danneggiata sullo storage fallirebbe sempre.
     */
    private val urlRequests = mutableMapOf<String, Int>()

    /**
     * Scope delle richieste della sessione attuale (pericoli, foto e URL), figlio di viewModelScope e annullato al
     * logout. Resta attivo anche dopo il caricamento di pericoli e foto, per gli URL chiesti quando le foto compaiono.
     */
    private var loggedInScope: CoroutineScope? = null

    init {
        // La UI legge sempre dalla cache: prima arriva la copia salvata, poi quella aggiornata dalla rete.
        viewModelScope.launch {
            trails.observeDetail(trailId).collect { cached ->
                _state.update { state ->
                    // La copia è sparita (il server ha risposto 404): un avviso "non aggiornato" già arrivato diventa
                    // l'errore della schermata.
                    if (cached == null) return@update state.copy(trail = null, updatedAt = null, error = state.error ?: state.stale, stale = null)
                    // Un errore arrivato prima della copia in cache diventa l'avviso "non aggiornato".
                    state.copy(trail = cached.value, updatedAt = cached.updatedAt, error = null, stale = state.stale ?: state.error)
                }
            }
        }
        viewModelScope.launch {
            trails.observePoisOf(trailId).collect { pois -> _state.update { it.copy(pois = pois) } }
        }
        refresh()
        // Pericoli e foto solo per l'utente loggato, perché le rotte richiedono il token: si caricano all'accesso e
        // spariscono al logout.
        viewModelScope.launch {
            session.state.map { it is SessionState.LoggedIn }.distinctUntilChanged().collect { loggedIn ->
                loggedInScope?.cancel()
                loggedInScope = null
                urlRequests.clear()
                _state.update { it.copy(hazards = Section.Hidden, photos = Section.Hidden, photoUrls = emptyMap(), failedPhotos = emptySet()) }
                if (loggedIn) {
                    // Con un SupervisorJob una sezione che fallisce non annulla le altre né i "Riprova" successivi.
                    val scope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
                    loggedInScope = scope
                    scope.launch { loadHazards() }
                    scope.launch { loadPhotos() }
                }
            }
        }
    }

    /** "Riprova" del dettaglio: riparte solo se c'è un errore o un avviso di copia non aggiornata. */
    fun retry() {
        if (_state.value.error != null || _state.value.stale != null) refresh()
    }

    /** "Riprova" della sezione dei pericoli, solo se il caricamento era fallito. */
    fun retryHazards() {
        if (_state.value.hazards is Section.Failed) loggedInScope?.launch { loadHazards() }
    }

    /** "Riprova" della sezione delle foto, solo se il caricamento era fallito. */
    fun retryPhotos() {
        if (_state.value.photos is Section.Failed) loggedInScope?.launch { loadPhotos() }
    }

    /** Aggiornamento del dettaglio in corso, conservato per non avviarne due insieme. */
    private var refreshJob: Job? = null

    /**
     * Aggiorna dalla rete dettaglio e POI nella cache, con l'ETag: se nulla è cambiato il server risponde 304 e si
     * aggiorna solo l'orario. Gira un aggiornamento alla volta, così tocchi ripetuti su "Riprova" non moltiplicano le
     * richieste (il server ne accetta 60 al minuto).
     */
    private fun refresh() {
        if (refreshJob?.isActive == true) return
        _state.update { it.copy(error = null, stale = null) }
        refreshJob = viewModelScope.launch {
            trails.refreshDetail(trailId).onFailure { failure ->
                val message = failure.toUiMessage()
                _state.update { if (it.trail != null) it.copy(stale = message) else it.copy(error = message) }
            }
        }
        // I POI non sono essenziali: se l'aggiornamento fallisce il dettaglio si mostra lo stesso, con quelli in cache.
        viewModelScope.launch { trails.refreshPoisOf(trailId) }
    }

    /** Carica i pericoli attivi del sentiero e aggiorna la sezione. */
    private suspend fun loadHazards() {
        _state.update { it.copy(hazards = Section.Loading) }
        val result = safely { hazards.activeOn(trailId) }
        _state.update { it.copy(hazards = result.toSection()) }
    }

    /** Carica le foto del sentiero e aggiorna la sezione. */
    private suspend fun loadPhotos() {
        _state.update { it.copy(photos = Section.Loading) }
        val result = safely { media.photosOfTrail(trailId) }
        _state.update { it.copy(photos = result.toSection()) }
    }

    /** La foto [mediaId] è comparsa sullo schermo: se non ha ancora un URL, e non è già stato chiesto, lo chiede. */
    fun onPhotoShown(mediaId: String) {
        if (mediaId in _state.value.photoUrls || mediaId in urlRequests) return
        requestUrl(mediaId)
    }

    /**
     * Il download della foto non è riuscito, di solito perché l'URL è scaduto (dopo 5 minuti): se ne chiede un altro,
     * finché non si raggiunge MaxUrlRequests.
     */
    fun onPhotoFailed(mediaId: String) {
        _state.update { it.copy(photoUrls = it.photoUrls - mediaId) }
        if ((urlRequests[mediaId] ?: 0) >= MaxUrlRequests) {
            // Tentativi esauriti: si mostra "Foto non disponibile" e un tocco riparte da capo.
            _state.update { it.copy(failedPhotos = it.failedPhotos + mediaId) }
            return
        }
        requestUrl(mediaId)
    }

    /** Tocco su "Foto non disponibile": riparte con un URL nuovo e il conteggio dei tentativi azzerato. */
    fun onPhotoRetry(mediaId: String) {
        urlRequests.remove(mediaId)
        _state.update { it.copy(failedPhotos = it.failedPhotos - mediaId) }
        requestUrl(mediaId)
    }

    /** Chiede al server un URL firmato per la foto, se l'utente è ancora loggato. */
    private fun requestUrl(mediaId: String) {
        val scope = loggedInScope ?: return
        urlRequests[mediaId] = (urlRequests[mediaId] ?: 0) + 1
        scope.launch {
            safely { media.downloadUrl(mediaId) }.fold(
                onSuccess = { url -> _state.update { it.copy(photoUrls = it.photoUrls + (mediaId to url)) } },
                // Senza URL la foto resterebbe in caricamento per sempre: si mostra l'errore con il tocco per riprovare.
                onFailure = { _state.update { it.copy(failedPhotos = it.failedPhotos + mediaId) } },
            )
        }
    }

    /** Costanti del ViewModel. */
    private companion object {
        /** URL massimi per foto: il primo più due rinnovi. */
        const val MaxUrlRequests = 3
    }
}

/** Converte il risultato di un caricamento nello stato della sezione. */
private fun <T> Result<T>.toSection(): Section<T> = fold({ Section.Loaded(it) }, { Section.Failed(it.toUiMessage()) })

/** Converte un errore in un messaggio per l'utente, con un testo generico se non è un errore dell'API. */
private fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
