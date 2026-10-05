package it.unical.geosentinel_mobile.hazard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Le mie segnalazioni" (M4.3): solo rete, niente cache. La prima pagina all'apertura, le altre quando la lista arriva
 * in fondo ([loadMore]), da capo trascinando in basso ([refresh]).
 */
class MyHazardsViewModel(private val hazards: HazardRepository) : ViewModel() {

    private val _state = MutableStateFlow(MyHazardsUiState(loading = true))
    val state: StateFlow<MyHazardsUiState> = _state.asStateFlow()

    // la pagina da chiedere con loadMore (0 = la prima)
    private var nextPage = 0
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        // i flag si controllano e si alzano qui, prima del launch: due chiamate di fila non chiedono due volte la pagina
        if (current.loading || current.loadingMore || current.refreshing || current.endReached || current.error != null) return
        _state.update { it.copy(loadingMore = true) }
        load(reset = false)
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        load(reset = true)
    }

    /** "Riprova": la prima pagina se la lista è vuota, altrimenti quella che non è arrivata. */
    fun retry() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        if (current.items.isEmpty()) {
            _state.update { it.copy(loading = true, error = null) }
            load(reset = true)
        } else {
            _state.update { it.copy(loadingMore = true, error = null) }
            load(reset = false)
        }
    }

    private fun load(reset: Boolean) {
        // da capo vince su una pagina in corso: le sue righe non si mescolano alla lista nuova
        if (reset) loadJob?.cancel()
        val page = if (reset) 0 else nextPage
        loadJob = viewModelScope.launch {
            val result = hazards.mine(page)
            result.onSuccess { nextPage = page + 1 }
            _state.update { state ->
                result.fold(
                    onSuccess = { paged ->
                        // le pagine sono a offset: se nel frattempo ne arriva una nuova in cima, l'ultima di prima
                        // ricompare nella pagina dopo. distinctBy toglie il doppione
                        val items = if (reset) paged.content else (state.items + paged.content).distinctBy { it.id }
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
                        state.copy(
                            loading = false,
                            loadingMore = false,
                            refreshing = false,
                            error = (failure as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic),
                        )
                    },
                )
            }
        }
    }
}
