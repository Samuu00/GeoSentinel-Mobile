package it.unical.geosentinel_mobile.refuge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.booking_arrival_past
import geosentinel_mobile.shared.generated.resources.booking_no_profile
import it.unical.geosentinel_mobile.refuge.data.AVAILABILITY_NIGHTS
import it.unical.geosentinel_mobile.refuge.data.BookingOutcome
import it.unical.geosentinel_mobile.refuge.data.BookingPayload
import it.unical.geosentinel_mobile.refuge.data.BookingRepository
import it.unical.geosentinel_mobile.refuge.data.NightAvailability
import it.unical.geosentinel_mobile.core.ui.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

// Le notti di una prenotazione: il server ne accetta al massimo 14 (come il calendario del dettaglio)
const val MAX_BOOKING_NIGHTS = AVAILABILITY_NIGHTS

// Le persone di una prenotazione: il contratto (CreateBookingRequest.guests) ne accetta al massimo 20
const val MAX_BOOKING_GUESTS = 20

// Una pagina del calendario del foglio "Prenota": due settimane, da lunedì a domenica
const val CALENDAR_PAGE_DAYS = 14

/** Com'è finito l'invio: il foglio passa alla conferma. */
enum class BookingDone { BOOKED, QUEUED }

data class BookRefugeUiState(
    val arrival: LocalDate,
    val nights: Int = 1,
    val guests: Int = 1,
    // i posti del rifugio, al massimo MAX_BOOKING_GUESTS
    val maxGuests: Int = 1,
    // calendario: il lunedì della pagina mostrata e i posti liberi delle notti già lette (tutte le pagine viste)
    val pageStart: LocalDate = arrival,
    val freeBeds: Map<LocalDate, Int> = emptyMap(),
    val loadingPage: Boolean = false,
    val pageError: UiMessage? = null,
    // dopo l'arrivo il tocco successivo su un giorno più avanti sceglie la partenza
    val pickingDeparture: Boolean = false,
    val submitting: Boolean = false,
    val done: BookingDone? = null,
    val error: UiMessage? = null,
) {
    val departure: LocalDate get() = arrival.plus(DatePeriod(days = nights))

    /** Si può dormire qui la notte di [date] con [guests] persone? Una notte non ancora letta si lascia scegliere (decide il server). */
    fun nightFits(date: LocalDate, guests: Int = this.guests): Boolean = freeBeds[date]?.let { it >= guests } ?: true

    /** La notte del soggiorno con meno posti, se si conosce. */
    val tightestNight: NightAvailability?
        get() = (0 until nights).map { arrival.plus(DatePeriod(days = it)) }
            .mapNotNull { date -> freeBeds[date]?.let { NightAvailability(date, it) } }
            .minByOrNull { it.freeBeds }

    /** Quante persone al massimo per le notti scelte: i posti del rifugio o, se meno, quelli della notte più piena. */
    val guestsCap: Int get() = minOf(maxGuests, tightestNight?.freeBeds ?: maxGuests).coerceAtLeast(1)

    // niente date nei log
    override fun toString() = "BookRefugeUiState(nights=$nights, guests=$guests, submitting=$submitting, done=$done, error=${error != null})"
}

/**
 * Foglio "Prenota" (M5.2, rifatto con il redesign 11/2026): calendario a due settimane con i posti liberi di ogni notte,
 * arrivo e partenza con due tocchi, persone (1–posti, al massimo 20), poi la conferma nello stesso foglio. L'invio passa
 * dall'outbox ([BookingRepository.book]): senza risposta la prenotazione resta "in invio" e parte da sola più tardi.
 * [ownerId]: l'id dell'utente loggato (dal profilo o, senza rete, dal token; null da ospite).
 * [loadNights]: i posti liberi di `nights` notti da `from` (di solito `RefugeRepository.availability`).
 */
class BookRefugeViewModel(
    private val refugeId: String,
    capacity: Int,
    private val bookings: BookingRepository,
    private val ownerId: suspend () -> String?,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    private val loadNights: suspend (from: LocalDate, nights: Int) -> Result<List<NightAvailability>> = { _, _ -> Result.success(emptyList()) },
) : ViewModel() {

    val maxGuests: Int = capacity.coerceIn(1, MAX_BOOKING_GUESTS)

    private val _state = MutableStateFlow(BookRefugeUiState(arrival = today(), maxGuests = maxGuests, pageStart = mondayOf(today())))
    val state: StateFlow<BookRefugeUiState> = _state.asStateFlow()

    private var pageJob: Job? = null
    // aperto da "Prenota" senza un giorno: l'arrivo diventa la prima notte libera appena si leggono i posti
    private var pickFirstFree = false

    /**
     * Apre il foglio: con [arrival] (un giorno toccato nella pagina del rifugio) l'arrivo è quello e il tocco dopo sceglie
     * la partenza; senza, la prima notte libera. Toglie la conferma di una prenotazione precedente.
     */
    fun open(arrival: LocalDate?) {
        if (_state.value.submitting) return
        val start = arrival?.takeIf { it >= today() } ?: today()
        pickFirstFree = arrival == null
        _state.update {
            it.copy(arrival = start, nights = 1, pickingDeparture = true, pageStart = mondayOf(start), done = null, error = null)
        }
        loadPage(mondayOf(start))
    }

    /** Un giorno del calendario: arrivo, poi partenza; un giorno prima dell'arrivo (o un terzo tocco) ricomincia. */
    fun onDayTapped(date: LocalDate) {
        if (date < today()) return
        val current = _state.value
        if (current.submitting || current.done != null) return
        pickFirstFree = false
        if (current.pickingDeparture && date > current.arrival) {
            val nights = (date.toEpochDays() - current.arrival.toEpochDays()).toInt()
            val fits = (0 until nights).all { current.nightFits(current.arrival.plus(DatePeriod(days = it))) }
            if (nights <= MAX_BOOKING_NIGHTS && fits) {
                edit { copy(nights = nights, pickingDeparture = false) }
                return
            }
        }
        if (current.nightFits(date)) edit { copy(arrival = date, nights = 1, pickingDeparture = true) }
    }

    /** Due settimane avanti o indietro (mai interamente nel passato). */
    fun onPage(forward: Boolean) {
        val current = _state.value
        val first = mondayOf(today())
        val next = current.pageStart.plus(DatePeriod(days = if (forward) CALENDAR_PAGE_DAYS else -CALENDAR_PAGE_DAYS))
        if (next < first) return
        _state.update { it.copy(pageStart = next) }
        loadPage(next)
    }

    fun retryPage() = loadPage(_state.value.pageStart)

    fun onArrivalChange(date: LocalDate) = edit { copy(arrival = date) }

    fun onNightsChange(value: Int) = edit { copy(nights = value.coerceIn(1, MAX_BOOKING_NIGHTS)) }

    fun onGuestsChange(value: Int) = edit { copy(guests = value.coerceIn(1, guestsCap)) }

    fun submit() {
        val current = _state.value
        // doppio tocco: una sola prenotazione (la seconda chiave creerebbe una seconda prenotazione)
        if (current.submitting || current.done != null) return
        if (current.arrival < today()) {
            _state.update { it.copy(error = UiMessage(Res.string.booking_arrival_past)) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val owner = ownerId()
            if (owner == null) {
                _state.update { it.copy(submitting = false, error = UiMessage(Res.string.booking_no_profile)) }
                return@launch
            }
            val outcome = bookings.book(owner, BookingPayload(refugeId, current.arrival, current.departure, current.guests))
            _state.update { state ->
                when (outcome) {
                    is BookingOutcome.Booked -> state.copy(submitting = false, done = BookingDone.BOOKED)
                    BookingOutcome.Queued -> state.copy(submitting = false, done = BookingDone.QUEUED)
                    // capacity-exceeded, date non valide...: le scelte restano, si cambia e si riprova
                    is BookingOutcome.Rejected -> state.copy(submitting = false, error = outcome.cause.toUiMessage())
                }
            }
        }
    }

    // Le notti della pagina che parte da [start]: solo da oggi in poi (il server rifiuta il passato) e solo se mancano
    private fun loadPage(start: LocalDate) {
        val from = maxOf(start, today())
        val count = (start.toEpochDays() + CALENDAR_PAGE_DAYS - from.toEpochDays()).toInt()
        if (count <= 0) return
        val days = (0 until count).map { from.plus(DatePeriod(days = it)) }
        if (days.all { it in _state.value.freeBeds }) {
            choosePickedFirstFree()
            return
        }
        pageJob?.cancel()
        _state.update { it.copy(loadingPage = true, pageError = null) }
        pageJob = viewModelScope.launch {
            loadNights(from, count).fold(
                onSuccess = { list ->
                    _state.update { state -> state.copy(freeBeds = state.freeBeds + list.associate { it.date to it.freeBeds }, loadingPage = false) }
                    choosePickedFirstFree()
                },
                onFailure = { error -> _state.update { it.copy(loadingPage = false, pageError = error.toUiMessage()) } },
            )
        }
    }

    private fun choosePickedFirstFree() {
        if (!pickFirstFree) return
        pickFirstFree = false
        val current = _state.value
        val first = current.freeBeds.keys.filter { it >= today() && current.nightFits(it) }.minOrNull() ?: return
        if (first != current.arrival) {
            _state.update { it.copy(arrival = first, nights = 1, pickingDeparture = true, pageStart = mondayOf(first)) }
        }
    }

    private fun edit(change: BookRefugeUiState.() -> BookRefugeUiState) {
        if (_state.value.submitting || _state.value.done != null) return
        _state.update { it.change().copy(error = null) }
    }
}

/** Il lunedì della settimana di [date]: le pagine del calendario partono da lì. */
internal fun mondayOf(date: LocalDate): LocalDate = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))
