package it.unical.geosentinel_mobile.refuge.ui

import androidx.lifecycle.ViewModel
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

data class RefugeDetailUiState(
    val refuge: Section<RefugeResponse> = Section.Loading,
    // il calendario si carica insieme al rifugio, ma un suo errore non nasconde il rifugio
    val nights: Section<List<NightAvailability>> = Section.Loading,
    // un di più: senza (errore o niente da mostrare) la pagina resta com'è, senza la sezione
    val photos: List<MediaResponse> = emptyList(),
    val nearbyTrails: List<TrailSummaryResponse> = emptyList(),
) {
    // niente nomi né posizioni nei log
    override fun toString() =
        "RefugeDetailUiState(refuge=${refuge::class.simpleName}, nights=${nights::class.simpleName}, photos=${photos.size}, trails=${nearbyTrails.size})"
}

/**
 * Dettaglio di un rifugio (M5.1): il rifugio con `getRefuge` e il calendario delle prossime notti con
 * `getRefugeAvailability`, in parallelo; poi le sue foto e i sentieri vicini (entro [TRAILS_NEAR_REFUGE_METERS]).
 * [today] è il giorno del telefono (iniettabile nei test).
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

    /** Riprova solo quello che è fallito: con il rifugio caricato basta il calendario. */
    fun retry() {
        val current = _state.value
        when {
            current.refuge is Section.Failed -> load()
            current.nights is Section.Failed -> loadNights()
        }
    }

    /**
     * Rilegge rifugio e calendario senza tornare al caricamento (M8.3: i posti cambiati dal gestore). Solo con il
     * rifugio già caricato: al primo ingresso c'è già [load]. Un errore lascia quello che si vede.
     */
    fun refresh() {
        if (_state.value.refuge !is Section.Loaded) return
        viewModelScope.launch {
            val refuge = async { refuges.get(refugeId) }
            val nights = async { refuges.availability(refugeId, from = today()) }
            refuge.await().onSuccess { found -> _state.update { it.copy(refuge = Section.Loaded(found)) } }
            nights.await().onSuccess { list -> _state.update { it.copy(nights = Section.Loaded(list)) } }
        }
    }

    private fun load() {
        _state.value = RefugeDetailUiState()
        viewModelScope.launch {
            val refuge = async { refuges.get(refugeId) }
            val nights = async { refuges.availability(refugeId, from = today()) }
            val loaded = refuge.await()
            _state.update { it.copy(refuge = loaded.toSection()) }
            loaded.onSuccess { found ->
                launch { media.photosOfRefuge(found.id).onSuccess { list -> _state.update { it.copy(photos = list) } } }
                launch {
                    trails.nearby(found.location.toGeoPoint(), TRAILS_NEAR_REFUGE_METERS)
                        .onSuccess { list -> _state.update { it.copy(nearbyTrails = list.take(MAX_TRAILS_NEAR_REFUGE)) } }
                }
            }
            _state.update { it.copy(nights = nights.await().toSection()) }
        }
    }

    private fun loadNights() {
        _state.update { it.copy(nights = Section.Loading) }
        viewModelScope.launch {
            val nights = refuges.availability(refugeId, from = today())
            _state.update { it.copy(nights = nights.toSection()) }
        }
    }
}

// "Sentieri da qui": quelli che passano entro 5 km dal rifugio, i più vicini
const val TRAILS_NEAR_REFUGE_METERS = 5_000.0
private const val MAX_TRAILS_NEAR_REFUGE = 5

private fun <T> Result<T>.toSection(): Section<T> = fold({ Section.Loaded(it) }, { Section.Failed(it.toUiMessage()) })

internal fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
