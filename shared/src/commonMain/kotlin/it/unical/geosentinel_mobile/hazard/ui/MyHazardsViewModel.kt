// ViewModel della schermata "Le mie segnalazioni": carica a pagine le segnalazioni dell'utente dal server.
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
 * ViewModel di "Le mie segnalazioni". I dati arrivano solo dalla rete, senza cache. Carica la prima pagina alla
 * creazione, le successive quando la lista arriva in fondo ([loadMore]) e tutto da capo quando l'utente trascina in
 * basso ([refresh]). Espone lo stato come StateFlow immutabile.
 */
class MyHazardsViewModel(private val hazards: HazardRepository) : ViewModel() {

    private val _state = MutableStateFlow(MyHazardsUiState(loading = true))
    val state: StateFlow<MyHazardsUiState> = _state.asStateFlow()

    /** Indice della prossima pagina da chiedere con loadMore (0 è la prima). */
    private var nextPage = 0

    /** Caricamento in corso, conservato per poterlo annullare quando si ricomincia da capo. */
    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    /** Chiede la pagina successiva, se non c'è già un caricamento in corso, un errore o la fine della lista. */
    fun loadMore() {
        val current = _state.value
        // I flag si controllano e si impostano qui, in modo sincrono prima del launch: così due chiamate ravvicinate
        // non chiedono due volte la stessa pagina.
        if (current.loading || current.loadingMore || current.refreshing || current.endReached || current.error != null) return
        _state.update { it.copy(loadingMore = true) }
        load(reset = false)
    }

    /** Ricarica la lista da capo (gesto di trascinamento verso il basso). */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        load(reset = true)
    }

    /** Azione "Riprova": richiede la prima pagina se la lista è vuota, altrimenti la pagina che non è arrivata. */
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

    /**
     * Carica una pagina: la prima se [reset] è true (sostituendo la lista), altrimenti la successiva (accodandola).
     * In caso di errore lascia la lista com'è e imposta il messaggio.
     */
    private fun load(reset: Boolean) {
        // Un caricamento da capo annulla quello di una pagina in corso, così le sue righe non si mescolano alla lista
        // nuova.
        if (reset) loadJob?.cancel()
        val page = if (reset) 0 else nextPage
        loadJob = viewModelScope.launch {
            val result = hazards.mine(page)
            result.onSuccess { nextPage = page + 1 }
            _state.update { state ->
                result.fold(
                    onSuccess = { paged ->
                        // La paginazione del server è a offset: se nel frattempo si aggiunge una segnalazione in
                        // cima, l'ultima della pagina precedente ricompare all'inizio della successiva. distinctBy
                        // elimina il doppione.
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
