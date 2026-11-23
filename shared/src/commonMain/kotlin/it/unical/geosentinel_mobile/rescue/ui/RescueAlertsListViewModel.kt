// ViewModel della scheda Allerte degli operatori del soccorso: lista paginata delle allerte SOS aperte, con
// aggiornamento manuale, periodico e a ogni notifica di un nuovo SOS.
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

/** Stato della scheda Allerte: le allerte aperte caricate una pagina alla volta, come in "Le mie segnalazioni". */
data class RescueAlertsUiState(
    val items: List<SosAlertResponse> = emptyList(),
    /** Caricamento della prima pagina, all'apertura o con "Riprova" dallo stato d'errore. */
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** Aggiornamento avviato trascinando la lista verso il basso. */
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: UiMessage? = null,
) {
    // toString scritto a mano: posizioni e note mediche non devono finire nei log.
    override fun toString() =
        "RescueAlertsUiState(items=${items.size}, loading=$loading, loadingMore=$loadingMore, refreshing=$refreshing, endReached=$endReached, error=$error)"
}

/**
 * ViewModel della scheda Allerte. Lavora solo in rete, senza cache: un'allerta vecchia mostrata come ancora aperta
 * trarrebbe in inganno l'operatore. Carica la prima pagina all'apertura, le altre arrivando in fondo ([loadMore]),
 * di nuovo da capo trascinando in basso ([refresh]) o in silenzio con la schermata aperta e a ogni notifica di un
 * nuovo SOS ([poll]). Si esegue un caricamento alla volta: quello nuovo annulla quello in corso, così due risposte
 * non si mescolano.
 */
class RescueAlertsListViewModel(private val rescue: RescueRepository) : ViewModel() {

    private val _state = MutableStateFlow(RescueAlertsUiState(loading = true))
    val state: StateFlow<RescueAlertsUiState> = _state.asStateFlow()

    // Numero di pagine da ALERTS_PAGE_SIZE già caricate, cioè la prossima pagina da chiedere con loadMore.
    private var nextPage = 0
    private var loadJob: Job? = null

    init {
        load(Mode.Reset)
    }

    /** Carica la pagina successiva, se non c'è già un caricamento, un errore o la fine della lista. */
    fun loadMore() {
        val current = _state.value
        // I flag si controllano e si alzano prima di avviare la coroutine: così due chiamate di fila non chiedono due
        // volte la stessa pagina.
        if (current.loading || current.loadingMore || current.refreshing || current.endReached || current.error != null) return
        _state.update { it.copy(loadingMore = true) }
        load(Mode.NextPage)
    }

    /** Ricarica la lista da capo, mostrando l'indicatore del "tira per aggiornare". */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        load(Mode.Reset)
    }

    /**
     * Rilegge in silenzio le allerte già a schermo, chiedendone lo stesso numero in una sola richiesta: così la lista
     * non si accorcia e non salta in cima. Se la lista c'è già un errore non si mostra e si riprova al giro
     * successivo. Se è già in corso un caricamento visibile non fa nulla.
     */
    fun poll() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        load(Mode.Poll)
    }

    /** Riprova dopo un errore: la prima pagina se la lista è vuota, altrimenti la successiva. */
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

    /** Tipo di caricamento: da capo, pagina successiva o rilettura silenziosa. */
    private enum class Mode { Reset, NextPage, Poll }

    /** Esegue un caricamento nel modo [mode], annullando quello eventualmente in corso. */
    private fun load(mode: Mode) {
        // Un caricamento alla volta: una rilettura e una pagina successiva in corso insieme potrebbero lasciare un
        // buco nella lista.
        loadJob?.cancel()
        val page = if (mode == Mode.NextPage) nextPage else 0
        // Limite noto: il server accetta al massimo 100 elementi per pagina, quindi con più di 100 allerte la
        // rilettura silenziosa aggiorna solo le prime 100.
        val size = if (mode == Mode.Poll) (nextPage.coerceAtLeast(1) * ALERTS_PAGE_SIZE).coerceAtMost(MAX_PAGE_SIZE) else ALERTS_PAGE_SIZE
        loadJob = viewModelScope.launch {
            val result = rescue.active(page, size)
            result.onSuccess { nextPage = (page * size + size) / ALERTS_PAGE_SIZE }
            _state.update { state ->
                result.fold(
                    onSuccess = { paged ->
                        // Le pagine sono a offset: un'allerta chiusa nel frattempo sposta le altre indietro, e
                        // distinctBy toglie i doppioni che ne derivano.
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
        /** Dimensione massima di pagina accettata dal server (configurazione della paginazione del backend). */
        const val MAX_PAGE_SIZE = 100
    }
}

/** Messaggio per l'utente da un errore: quello del server o quello generico. */
internal fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)

/**
 * Stato dell'allerta scritto a parole, perché il colore da solo non basta per l'accessibilità. Un valore che l'app
 * non conosce ancora ha un'etichetta generica invece di far crashare l'app.
 */
fun SosAlertResponse.Status.label(): StringResource = when (this) {
    SosAlertResponse.Status.ACTIVE -> Res.string.rescue_status_active
    SosAlertResponse.Status.DISPATCHED -> Res.string.rescue_status_dispatched
    SosAlertResponse.Status.RESOLVED -> Res.string.rescue_status_resolved
    SosAlertResponse.Status.CANCELLED -> Res.string.rescue_status_cancelled
    SosAlertResponse.Status.unknown_default_open_api -> Res.string.rescue_status_unknown
}
