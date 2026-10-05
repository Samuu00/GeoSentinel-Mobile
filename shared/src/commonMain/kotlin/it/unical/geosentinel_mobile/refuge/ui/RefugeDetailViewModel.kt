package it.unical.geosentinel_mobile.refuge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.refuge.data.NightAvailability
import it.unical.geosentinel_mobile.refuge.data.RefugeRepository
import it.unical.geosentinel_mobile.trail.ui.Section
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class RefugeDetailUiState(
    val refuge: Section<RefugeResponse> = Section.Loading,
    // il calendario si carica insieme al rifugio, ma un suo errore non nasconde il rifugio
    val nights: Section<List<NightAvailability>> = Section.Loading,
) {
    // niente nomi né posizioni nei log
    override fun toString() = "RefugeDetailUiState(refuge=${refuge::class.simpleName}, nights=${nights::class.simpleName})"
}

/**
 * Dettaglio di un rifugio (M5.1): nome e posti con `getRefuge`, e il calendario delle prossime notti con
 * `getRefugeAvailability`, in parallelo. [today] è il giorno del telefono (iniettabile nei test).
 */
class RefugeDetailViewModel(
    private val refugeId: String,
    private val refuges: RefugeRepository,
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

    private fun load() {
        _state.value = RefugeDetailUiState()
        viewModelScope.launch {
            val refuge = async { refuges.get(refugeId) }
            val nights = async { refuges.availability(refugeId, from = today()) }
            _state.update { it.copy(refuge = refuge.await().toSection()) }
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

private fun <T> Result<T>.toSection(): Section<T> = fold({ Section.Loaded(it) }, { Section.Failed(it.toUiMessage()) })

internal fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)
