package it.unical.geosentinel_mobile.rescue.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.rescue_status_active
import geosentinel_mobile.shared.generated.resources.rescue_status_cancelled
import geosentinel_mobile.shared.generated.resources.rescue_status_dispatched
import geosentinel_mobile.shared.generated.resources.rescue_status_resolved
import geosentinel_mobile.shared.generated.resources.rescue_status_unknown
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.rescue.data.ALERTS_PAGE_SIZE
import it.unical.geosentinel_mobile.rescue.data.RescueRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/** La scheda Allerte (M7.2): allerte aperte, una pagina alla volta, come "Le mie segnalazioni". */
data class RescueAlertsUiState(
    val items: List<SosAlertResponse> = emptyList(),
    // prima pagina (all'apertura e con "Riprova" dallo stato d'errore)
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    // trascinando in basso
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: UiMessage? = null,
) {
    // niente posizioni né note mediche nei log
    override fun toString() =
        "RescueAlertsUiState(items=${items.size}, loading=$loading, loadingMore=$loadingMore, refreshing=$refreshing, endReached=$endReached, error=$error)"
}

/**
 * Solo rete, niente cache: un'allerta vecchia mostrata come aperta inganna. La prima pagina all'apertura, le altre in
 * fondo ([loadMore]), da capo trascinando in basso ([refresh]) o in silenzio con la schermata aperta e a ogni push di un
 * SOS nuovo ([poll]). Un caricamento alla volta: quello nuovo annulla quello in corso, così due risposte non si mescolano.
 */
class RescueAlertsListViewModel(private val rescue: RescueRepository) : ViewModel() {

    private val _state = MutableStateFlow(RescueAlertsUiState(loading = true))
    val state: StateFlow<RescueAlertsUiState> = _state.asStateFlow()

    // pagine da ALERTS_PAGE_SIZE già caricate: la prossima da chiedere con loadMore
    private var nextPage = 0
    private var loadJob: Job? = null

    init {
        load(Mode.Reset)
    }

    fun loadMore() {
        val current = _state.value
        // i flag si controllano e si alzano prima del launch: due chiamate di fila non chiedono due volte la pagina
        if (current.loading || current.loadingMore || current.refreshing || current.endReached || current.error != null) return
        _state.update { it.copy(loadingMore = true) }
        load(Mode.NextPage)
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        load(Mode.Reset)
    }

    /**
     * Rilegge in silenzio le allerte già a schermo (la stessa quantità, in una richiesta sola): la lista non si accorcia
     * e non salta in cima. Un errore non si mostra se la lista c'è già: si riprova al giro dopo.
     * Con un caricamento visibile in corso basta quello.
     */
    fun poll() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        load(Mode.Poll)
    }

    fun retry() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        if (current.items.isEmpty()) {
            _state.update { it.copy(loading = true, error = null) }
            load(Mode.Reset)
        } else {
            _state.update { it.copy(loadingMore = true, error = null) }
            load(Mode.NextPage)
        }
    }

    private enum class Mode { Reset, NextPage, Poll }

    private fun load(mode: Mode) {
        // un caricamento alla volta: senza questo un poll e una pagina in volo insieme lasciavano un buco nella lista
        loadJob?.cancel()
        val page = if (mode == Mode.NextPage) nextPage else 0
        // ponytail: oltre 100 allerte (il massimo del server per pagina) il poll rilegge solo le prime 100
        val size = if (mode == Mode.Poll) (nextPage.coerceAtLeast(1) * ALERTS_PAGE_SIZE).coerceAtMost(MAX_PAGE_SIZE) else ALERTS_PAGE_SIZE
        loadJob = viewModelScope.launch {
            val result = rescue.active(page, size)
            result.onSuccess { nextPage = (page * size + size) / ALERTS_PAGE_SIZE }
            _state.update { state ->
                result.fold(
                    onSuccess = { paged ->
                        // pagine a offset: un'allerta chiusa nel frattempo sposta le altre indietro, distinctBy toglie i doppioni
                        val items = if (mode == Mode.NextPage) (state.items + paged.content).distinctBy { it.id } else paged.content
                        state.copy(
                            items = items,
                            endReached = page + 1 >= paged.page.totalPages || paged.content.isEmpty(),
                            loading = false,
                            loadingMore = false,
                            refreshing = false,
                            error = null,
                        )
                    },
                    onFailure = { failure ->
                        if (mode == Mode.Poll && state.items.isNotEmpty()) {
                            state
                        } else {
                            state.copy(loading = false, loadingMore = false, refreshing = false, error = failure.toUiMessage())
                        }
                    },
                )
            }
        }
    }

    private companion object {
        // spring.data.web.pageable.max-page-size del backend
        const val MAX_PAGE_SIZE = 100
    }
}

internal fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)

// Lo stato in parole, non solo con il colore (§2); un valore nuovo del server non fa crashare l'app
fun SosAlertResponse.Status.label(): StringResource = when (this) {
    SosAlertResponse.Status.ACTIVE -> Res.string.rescue_status_active
    SosAlertResponse.Status.DISPATCHED -> Res.string.rescue_status_dispatched
    SosAlertResponse.Status.RESOLVED -> Res.string.rescue_status_resolved
    SosAlertResponse.Status.CANCELLED -> Res.string.rescue_status_cancelled
    SosAlertResponse.Status.unknown_default_open_api -> Res.string.rescue_status_unknown
}
