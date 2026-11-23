// ViewModel di "Le mie prenotazioni": lista paginata delle prenotazioni dal server, prenotazioni ancora in coda o
// rifiutate lette dall'outbox, nomi dei rifugi e annullamento.
package it.unical.geosentinel_mobile.refuge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.api.model.BookingResponse
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.refuge.data.BookingOutcome
import it.unical.geosentinel_mobile.refuge.data.BookingRepository
import it.unical.geosentinel_mobile.refuge.data.PendingBooking
import it.unical.geosentinel_mobile.refuge.data.RefugeRepository
import it.unical.geosentinel_mobile.refuge.data.RejectedBooking
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Stato della lista delle prenotazioni dell'utente. */
data class MyBookingsUiState(
    val items: List<BookingResponse> = emptyList(),
    /** Prenotazioni in coda nell'outbox, senza ancora una risposta: si mostrano in cima come "in invio". */
    val pending: List<PendingBooking> = emptyList(),
    /** Prenotazioni passate dalla coda e poi rifiutate dal server, o scadute: restano visibili finché l'utente non le chiude. */
    val rejected: List<RejectedBooking> = emptyList(),
    /** Rifugi delle prenotazioni per id, con nome, telefono e posizione: la prenotazione contiene solo l'id del rifugio. */
    val refuges: Map<String, RefugeResponse> = emptyMap(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: UiMessage? = null,
    /** Id della prenotazione che si sta annullando. */
    val cancelling: String? = null,
    /** Errore dell'ultimo annullamento. */
    val cancelError: UiMessage? = null,
) {
    /** Nomi dei rifugi già noti, per id. */
    val refugeNames: Map<String, String> get() = refuges.mapValues { it.value.name }

    // toString scritto a mano: date e nomi non devono finire nei log.
    override fun toString() = "MyBookingsUiState(items=${items.size}, pending=${pending.size}, rejected=${rejected.size}, loading=$loading, error=${error != null})"
}

/**
 * Prenotazioni divise in gruppi per la schermata: [next] è il prossimo soggiorno (in corso o il primo in arrivo),
 * [upcoming] gli altri in arrivo dal più vicino, [past] le passate e le annullate dalla più recente.
 */
data class BookingSections(
    val next: BookingResponse?,
    val upcoming: List<BookingResponse>,
    val past: List<BookingResponse>,
)

/**
 * Divide le prenotazioni nei gruppi di [BookingSections] rispetto a [today]. Un soggiorno confermato è attivo finché
 * il giorno di partenza è successivo a oggi; tutto il resto finisce tra le passate.
 */
internal fun List<BookingResponse>.sections(today: LocalDate): BookingSections {
    val active = filter { it.status == BookingResponse.Status.CONFIRMED && it.checkOut > today }.sortedBy { it.checkIn }
    val ids = active.map { it.id }.toSet()
    val past = filterNot { it.id in ids }.sortedByDescending { it.checkIn }
    return BookingSections(active.firstOrNull(), active.drop(1), past)
}

/**
 * ViewModel di "Le mie prenotazioni", un segmento della scheda Rifugi.
 *
 * All'apertura carica la prima pagina e, in parallelo, rimanda le prenotazioni rimaste in coda. Il reinvio con poca
 * rete può durare a lungo, quindi la lista non lo aspetta; se qualche prenotazione parte, la prima pagina si rilegge.
 * Le pagine successive si caricano arrivando in fondo alla lista ([loadMore]). La lista viene solo dalla rete; le
 * prenotazioni "in invio" e quelle rifiutate vengono dall'outbox.
 *
 * @param ownerId restituisce l'id dell'utente loggato (anche dal token, senza profilo). Si chiede ogni volta che
 * serve, non una volta sola.
 * @param today giorno corrente del telefono, sostituibile nei test.
 */
class MyBookingsViewModel(
    private val bookings: BookingRepository,
    private val refuges: RefugeRepository,
    private val ownerId: suspend () -> String?,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val _state = MutableStateFlow(MyBookingsUiState(loading = true))
    val state: StateFlow<MyBookingsUiState> = _state.asStateFlow()

    // Prossima pagina da chiedere, job del caricamento in corso e rifugi di cui si è già chiesto il nome.
    private var nextPage = 0
    private var loadJob: Job? = null
    private val namesRequested = mutableSetOf<String>()

    // Osserva le prenotazioni in coda e rifiutate dell'utente, rimanda quelle in coda e carica la prima pagina.
    init {
        viewModelScope.launch {
            val owner = ownerId() ?: return@launch
            launch {
                bookings.observePending(owner).collect { pending ->
                    _state.update { it.copy(pending = pending) }
                    loadNames(pending.map { it.payload.refugeId })
                }
            }
            launch {
                bookings.observeRejected(owner).collect { rejected ->
                    _state.update { it.copy(rejected = rejected) }
                    loadNames(rejected.map { it.payload.refugeId })
                }
            }
            resend(owner)
        }
        loadJob = viewModelScope.launch { loadPage(reset = true) }
    }

    /** Chiude l'avviso di una prenotazione rifiutata. */
    fun dismissRejected(key: String) {
        viewModelScope.launch { bookings.dismissRejected(key) }
    }

    /** Rimanda le prenotazioni in coda di [owner]; se almeno una è stata creata, rilegge la prima pagina per mostrarla. */
    private suspend fun resend(owner: String) {
        val booked = bookings.resendPending(owner).any { it is BookingOutcome.Booked }
        if (!booked) return
        // Si aspetta il caricamento in corso, che potrebbe essere partito prima che la prenotazione esistesse.
        loadJob?.join()
        loadJob = viewModelScope.launch { loadPage(reset = true) }
    }

    /** Carica la pagina successiva, se non c'è già un caricamento, un errore o la fine della lista. */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing || current.endReached || current.error != null) return
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch { loadPage(reset = false) }
    }

    /** "Tira per aggiornare": rilegge la prima pagina e riprova a inviare le prenotazioni in coda. */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadPage(reset = true) }
        viewModelScope.launch { ownerId()?.let { resend(it) } }
    }

    /**
     * Rilegge la prima pagina senza mostrare l'indicatore del "tira per aggiornare". Le prenotazioni sono un segmento
     * della scheda Rifugi, il cui ViewModel resta vivo a lungo: a ogni apertura del segmento la lista va riletta, per
     * esempio dopo una prenotazione fatta dal foglio "Prenota".
     */
    fun reload() {
        if (_state.value.refreshing) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadPage(reset = true) }
    }

    /** Riprova dopo un errore: la prima pagina se la lista è vuota, altrimenti la successiva. */
    fun retry() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        val reset = current.items.isEmpty()
        _state.update { if (reset) it.copy(loading = true, error = null) else it.copy(loadingMore = true, error = null) }
        loadJob = viewModelScope.launch { loadPage(reset) }
    }

    /** Le prenotazioni caricate, divise per gruppi rispetto a oggi. */
    fun sections(items: List<BookingResponse>): BookingSections = items.sections(today())

    /** Giorno corrente del telefono, usato dalla UI per testi come "Tra 3 giorni". */
    fun today(): LocalDate = today.invoke()

    /** Vero se la prenotazione si può annullare: confermata e fino al giorno d'arrivo compreso. È la regola del server, che decide comunque. */
    fun canCancel(booking: BookingResponse): Boolean =
        booking.status == BookingResponse.Status.CONFIRMED && booking.checkIn >= today()

    /** Annulla la prenotazione [bookingId]; se riesce, la segna come annullata nella lista senza ricaricarla. */
    fun cancel(bookingId: String) {
        if (_state.value.cancelling != null) return
        _state.update { it.copy(cancelling = bookingId, cancelError = null) }
        viewModelScope.launch {
            val result = bookings.cancel(bookingId)
            _state.update { state ->
                result.fold(
                    onSuccess = {
                        val items = state.items.map { if (it.id == bookingId) it.copy(status = BookingResponse.Status.CANCELLED) else it }
                        state.copy(items = items, cancelling = null)
                    },
                    onFailure = { state.copy(cancelling = null, cancelError = it.toUiMessage()) },
                )
            }
        }
    }

    /** Carica una pagina: la prima se [reset] è vero (sostituendo la lista), altrimenti la successiva. */
    private suspend fun loadPage(reset: Boolean) {
        val page = if (reset) 0 else nextPage
        val result = bookings.mine(page)
        result.onSuccess { nextPage = page + 1 }
        _state.update { state ->
            result.fold(
                onSuccess = { paged ->
                    // Le pagine sono a offset: una prenotazione nuova in cima sposta l'ultima della pagina in quella
                    // successiva, che quindi può contenere un duplicato da scartare.
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
                onFailure = { state.copy(loading = false, loadingMore = false, refreshing = false, error = it.toUiMessage()) },
            )
        }
        result.onSuccess { paged -> loadNames(paged.content.map { it.refugeId }) }
        // Il server ordina dalla prenotazione più lontana nel futuro: per trovare il prossimo soggiorno si caricano
        // altre pagine finché non si arriva a una prenotazione passata (di solito bastano una o due pagine).
        val current = _state.value
        if (result.isSuccess && !current.endReached && current.items.lastOrNull()?.let { it.checkIn >= today() } == true) loadMore()
    }

    /**
     * Chiede i dati dei rifugi [ids] non ancora richiesti. Un errore non blocca la lista: la riga mostra "Rifugio"
     * senza nome, e il rifugio si potrà richiedere di nuovo.
     */
    private fun loadNames(ids: List<String>) {
        ids.distinct().filter { namesRequested.add(it) }.forEach { id ->
            viewModelScope.launch {
                refuges.get(id)
                    .onSuccess { refuge -> _state.update { it.copy(refuges = it.refuges + (id to refuge)) } }
                    .onFailure { namesRequested.remove(id) }
            }
        }
    }
}
