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

data class MyBookingsUiState(
    val items: List<BookingResponse> = emptyList(),
    // in coda nell'outbox, senza ancora una risposta: in cima come "in invio"
    val pending: List<PendingBooking> = emptyList(),
    // in coda e poi rifiutate dal server (o scadute): si mostrano finché l'utente non le chiude
    val rejected: List<RejectedBooking> = emptyList(),
    // i rifugi delle prenotazioni (la prenotazione ha solo l'id): nome, telefono e posizione; uno per rifugio
    val refuges: Map<String, RefugeResponse> = emptyMap(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: UiMessage? = null,
    // la prenotazione che si sta annullando, e l'errore dell'ultimo annullamento
    val cancelling: String? = null,
    val cancelError: UiMessage? = null,
) {
    val refugeNames: Map<String, String> get() = refuges.mapValues { it.value.name }

    // niente date né nomi nei log
    override fun toString() = "MyBookingsUiState(items=${items.size}, pending=${pending.size}, rejected=${rejected.size}, loading=$loading, error=${error != null})"
}

/**
 * Le prenotazioni divise per chi parte (redesign 11/2026): il prossimo soggiorno (in corso o il primo in arrivo), gli
 * altri in arrivo dal più vicino, poi le passate e le annullate dalla più recente.
 */
data class BookingSections(
    val next: BookingResponse?,
    val upcoming: List<BookingResponse>,
    val past: List<BookingResponse>,
)

internal fun List<BookingResponse>.sections(today: LocalDate): BookingSections {
    val active = filter { it.status == BookingResponse.Status.CONFIRMED && it.checkOut > today }.sortedBy { it.checkIn }
    val ids = active.map { it.id }.toSet()
    val past = filterNot { it.id in ids }.sortedByDescending { it.checkIn }
    return BookingSections(active.firstOrNull(), active.drop(1), past)
}

/**
 * "Le mie prenotazioni" (M5.2), nella scheda Rifugi. All'apertura la prima pagina e, insieme, il reinvio di quelle
 * rimaste in coda (che con poca rete può durare a lungo: la lista non lo aspetta); se ne parte qualcuna la prima pagina
 * si rilegge. Le altre pagine in fondo alla lista ([loadMore]). Solo rete per la lista; le "in invio" dall'outbox.
 */
class MyBookingsViewModel(
    private val bookings: BookingRepository,
    private val refuges: RefugeRepository,
    // l'utente loggato (anche senza profilo letto, dal token): si chiede quando serve, non una volta sola
    private val ownerId: suspend () -> String?,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val _state = MutableStateFlow(MyBookingsUiState(loading = true))
    val state: StateFlow<MyBookingsUiState> = _state.asStateFlow()

    private var nextPage = 0
    private var loadJob: Job? = null
    private val namesRequested = mutableSetOf<String>()

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

    fun dismissRejected(key: String) {
        viewModelScope.launch { bookings.dismissRejected(key) }
    }

    // In parallelo alla lista. Se una prenotazione in coda è partita, la prima pagina si rilegge per mostrarla
    private suspend fun resend(owner: String) {
        val booked = bookings.resendPending(owner).any { it is BookingOutcome.Booked }
        if (!booked) return
        // dopo il caricamento in corso, che può essere partito prima della risposta
        loadJob?.join()
        loadJob = viewModelScope.launch { loadPage(reset = true) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing || current.endReached || current.error != null) return
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch { loadPage(reset = false) }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadPage(reset = true) }
        viewModelScope.launch { ownerId()?.let { resend(it) } }
    }

    /**
     * Rilegge la prima pagina senza l'indicatore del "tira per aggiornare" (redesign 11/2026): le prenotazioni sono un
     * segmento della scheda Rifugi, il cui ViewModel vive a lungo; a ogni apertura del segmento la lista va riletta
     * (es. dopo una prenotazione dal foglio "Prenota").
     */
    fun reload() {
        if (_state.value.refreshing) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadPage(reset = true) }
    }

    fun retry() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        val reset = current.items.isEmpty()
        _state.update { if (reset) it.copy(loading = true, error = null) else it.copy(loadingMore = true, error = null) }
        loadJob = viewModelScope.launch { loadPage(reset) }
    }

    /** Le prenotazioni caricate, divise per gruppi rispetto a oggi. */
    fun sections(items: List<BookingResponse>): BookingSections = items.sections(today())

    /** Oggi per il telefono: la UI ci calcola "Tra 3 giorni". */
    fun today(): LocalDate = today.invoke()

    /** Si può annullare fino al giorno d'arrivo compreso (regola del server, che resta l'ultima parola). */
    fun canCancel(booking: BookingResponse): Boolean =
        booking.status == BookingResponse.Status.CONFIRMED && booking.checkIn >= today()

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

    private suspend fun loadPage(reset: Boolean) {
        val page = if (reset) 0 else nextPage
        val result = bookings.mine(page)
        result.onSuccess { nextPage = page + 1 }
        _state.update { state ->
            result.fold(
                onSuccess = { paged ->
                    // pagine a offset: una prenotazione nuova in cima sposta l'ultima nella pagina dopo
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
        // il server le dà dalla più lontana nel futuro: per trovare il prossimo soggiorno si va avanti finché non si
        // arriva al passato (di solito una o due pagine)
        val current = _state.value
        if (result.isSuccess && !current.endReached && current.items.lastOrNull()?.let { it.checkIn >= today() } == true) loadMore()
    }

    // un errore lascia il posto vuoto: la riga mostra "Rifugio" senza nome, non è un motivo per bloccare la lista
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
