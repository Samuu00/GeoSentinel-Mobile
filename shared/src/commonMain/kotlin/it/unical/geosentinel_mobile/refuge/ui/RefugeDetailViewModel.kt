// ViewModel della pagina di dettaglio di un rifugio: dati del rifugio, posti liberi delle prossime notti, foto e
// sentieri che partono nei dintorni. Ogni parte si carica e fallisce in modo indipendente.
package it.unical.geosentinel_mobile.refuge.ui

import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.refuge.data.NightAvailability
import it.unical.geosentinel_mobile.refuge.data.RefugeRepository
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import it.unical.geosentinel_mobile.trail.ui.Section
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Stato della pagina del rifugio, diviso in sezioni che si caricano separatamente. */
data class RefugeDetailUiState(
    val refuge: Section<RefugeResponse> = Section.Loading,
    /** Posti liberi per notte: si caricano insieme al rifugio, ma un loro errore non nasconde il rifugio. */
    val nights: Section<List<NightAvailability>> = Section.Loading,
    /** Foto del rifugio. Sono un di più: se mancano o il caricamento fallisce, la sezione semplicemente non compare. */
    val photos: List<MediaResponse> = emptyList(),
    /** Sentieri vicini al rifugio, per la sezione "Sentieri da qui"; anche questa sezione compare solo se non è vuota. */
    val nearbyTrails: List<TrailSummaryResponse> = emptyList(),
) {
    // toString scritto a mano: nomi e posizioni non devono finire nei log.
    override fun toString() =
        "RefugeDetailUiState(refuge=${refuge::class.simpleName}, nights=${nights::class.simpleName}, photos=${photos.size}, trails=${nearbyTrails.size})"
}

/**
 * ViewModel del dettaglio del rifugio [refugeId]. Carica in parallelo il rifugio e il calendario delle prossime
 * notti; quando il rifugio è arrivato chiede anche le sue foto e i sentieri entro [TRAILS_NEAR_REFUGE_METERS].
 * [today] restituisce il giorno corrente del telefono ed è un parametro per poterlo fissare nei test.
 */
class RefugeDetailViewModel(
    private val refugeId: String,
    private val refuges: RefugeRepository,
    private val media: MediaRepository,
    private val trails: TrailRepository,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val _state = MutableStateFlow(RefugeDetailUiState())
    val state: StateFlow<RefugeDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Riprova solo quello che è fallito: se il rifugio è già caricato basta ricaricare il calendario. */
    fun retry() {
        val current = _state.value
        when {
            current.refuge is Section.Failed -> load()
            current.nights is Section.Failed -> loadNights()
        }
    }

    /**
     * Rilegge rifugio e calendario senza mostrare di nuovo il caricamento, per esempio al ritorno dalla schermata in
     * cui il gestore cambia i posti letto. Agisce solo se il rifugio è già caricato (al primo ingresso ci pensa
     * [load]); un errore lascia visibili i dati di prima.
     */
    fun refresh() {
        if (_state.value.refuge !is Section.Loaded) return
        viewModelScope.launch {
            val refuge = async { safely { refuges.get(refugeId) } }
            val nights = async { safely { refuges.availability(refugeId, from = today()) } }
            refuge.await().onSuccess { found -> _state.update { it.copy(refuge = Section.Loaded(found)) } }
            nights.await().onSuccess { list -> _state.update { it.copy(nights = Section.Loaded(list)) } }
        }
    }

    /** Caricamento completo della pagina, dallo stato iniziale. */
    private fun load() {
        _state.value = RefugeDetailUiState()
        viewModelScope.launch {
            val refuge = async { safely { refuges.get(refugeId) } }
            val nights = async { safely { refuges.availability(refugeId, from = today()) } }
            val loaded = refuge.await()
            _state.update { it.copy(refuge = loaded.toSection()) }
            loaded.onSuccess { found ->
                launch { safely { media.photosOfRefuge(found.id) }.onSuccess { list -> _state.update { it.copy(photos = list) } } }
                launch {
                    safely { trails.nearby(found.location.toGeoPoint(), TRAILS_NEAR_REFUGE_METERS) }
                        .onSuccess { list -> _state.update { it.copy(nearbyTrails = list.take(MAX_TRAILS_NEAR_REFUGE)) } }
                }
            }
            _state.update { it.copy(nights = nights.await().toSection()) }
        }
    }

    /** Ricarica solo il calendario delle notti. */
    private fun loadNights() {
        _state.update { it.copy(nights = Section.Loading) }
        viewModelScope.launch {
            val nights = safely { refuges.availability(refugeId, from = today()) }
            _state.update { it.copy(nights = nights.toSection()) }
        }
    }
}

/** Raggio in metri entro cui un sentiero compare in "Sentieri da qui". */
const val TRAILS_NEAR_REFUGE_METERS = 5_000.0
/** Numero massimo di sentieri mostrati in "Sentieri da qui", i più vicini. */
private const val MAX_TRAILS_NEAR_REFUGE = 5

/** Converte un risultato in una sezione caricata o fallita. */
private fun <T> Result<T>.toSection(): Section<T> = fold({ Section.Loaded(it) }, { Section.Failed(it.toUiMessage()) })

/** Messaggio per l'utente da un errore: quello del server o quello generico. Usata anche dagli altri ViewModel dei rifugi. */
internal fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
