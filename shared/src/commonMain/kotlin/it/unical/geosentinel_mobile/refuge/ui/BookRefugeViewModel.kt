// ViewModel del foglio "Prenota" di un rifugio: calendario con i posti liberi per notte, scelta di arrivo,
// partenza e numero di persone, invio della prenotazione tramite l'outbox.
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

/** Numero massimo di notti di una prenotazione: il server ne accetta al massimo 14, come il calendario del dettaglio. */
const val MAX_BOOKING_NIGHTS = AVAILABILITY_NIGHTS

/** Numero massimo di persone in una prenotazione, secondo il contratto della richiesta di prenotazione. */
const val MAX_BOOKING_GUESTS = 20

/** Giorni di una pagina del calendario del foglio "Prenota": due settimane, da lunedì a domenica. */
const val CALENDAR_PAGE_DAYS = 14

/**
 * Esito dell'invio che fa passare il foglio alla conferma: BOOKED se la prenotazione è stata creata, QUEUED se è
 * rimasta in coda e partirà da sola più tardi.
 */
enum class BookingDone { BOOKED, QUEUED }

/** Stato del foglio "Prenota": soggiorno scelto, calendario caricato ed esito dell'invio. */
data class BookRefugeUiState(
    val arrival: LocalDate,
    val nights: Int = 1,
    val guests: Int = 1,
    /** Posti del rifugio, limitati a [MAX_BOOKING_GUESTS]. */
    val maxGuests: Int = 1,
    /** Lunedì della pagina del calendario mostrata. */
    val pageStart: LocalDate = arrival,
    /** Posti liberi delle notti già lette dal server, accumulati su tutte le pagine viste. */
    val freeBeds: Map<LocalDate, Int> = emptyMap(),
    val loadingPage: Boolean = false,
    val pageError: UiMessage? = null,
    /** Vero dopo aver scelto l'arrivo: il prossimo tocco su un giorno successivo sceglie la partenza. */
    val pickingDeparture: Boolean = false,
    val submitting: Boolean = false,
    val done: BookingDone? = null,
    /** Totale in centesimi restituito dal server con la prenotazione creata (null se senza prezzo o ancora in coda). */
    val bookedTotalCents: Int? = null,
    val error: UiMessage? = null,
) {
    /** Giorno di partenza, ricavato da arrivo e numero di notti. */
    val departure: LocalDate get() = arrival.plus(DatePeriod(days = nights))

    /**
     * Vero se nella notte di [date] ci sono posti per [guests] persone. Una notte non ancora letta dal server si
     * lascia scegliere: in quel caso decide il server all'invio.
     */
    fun nightFits(date: LocalDate, guests: Int = this.guests): Boolean = freeBeds[date]?.let { it >= guests } ?: true

    /** Notte del soggiorno con meno posti liberi, tra quelle di cui si conosce la disponibilità. */
    val tightestNight: NightAvailability?
        get() = (0 until nights).map { arrival.plus(DatePeriod(days = it)) }
            .mapNotNull { date -> freeBeds[date]?.let { NightAvailability(date, it) } }
            .minByOrNull { it.freeBeds }

    /** Persone al massimo per le notti scelte: i posti del rifugio o, se sono meno, quelli della notte più piena. */
    val guestsCap: Int get() = minOf(maxGuests, tightestNight?.freeBeds ?: maxGuests).coerceAtLeast(1)

    // toString scritto a mano: le date del soggiorno non devono finire nei log.
    override fun toString() = "BookRefugeUiState(nights=$nights, guests=$guests, submitting=$submitting, done=$done, error=${error != null})"
}

/**
 * ViewModel del foglio "Prenota". Il calendario mostra due settimane con i posti liberi di ogni notte; arrivo e
 * partenza si scelgono con due tocchi, poi il numero di persone (da 1 ai posti disponibili, al massimo 20) e infine
 * la conferma nello stesso foglio.
 *
 * L'invio passa dall'outbox ([BookingRepository.book]): se non arriva una risposta la prenotazione resta "in invio"
 * e parte da sola più tardi.
 *
 * @param capacity posti del rifugio.
 * @param ownerId restituisce l'id dell'utente loggato, preso dal profilo o, senza rete, dal token; null da ospite.
 * @param today giorno corrente del telefono, sostituibile nei test.
 * @param loadNights restituisce i posti liberi di `nights` notti a partire da `from` (di solito
 * `RefugeRepository.availability`).
 */
class BookRefugeViewModel(
    private val refugeId: String,
    capacity: Int,
    private val bookings: BookingRepository,
    private val ownerId: suspend () -> String?,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    private val loadNights: suspend (from: LocalDate, nights: Int) -> Result<List<NightAvailability>> = { _, _ -> Result.success(emptyList()) },
) : ViewModel() {

    /** Posti del rifugio limitati a [MAX_BOOKING_GUESTS]. */
    val maxGuests: Int = capacity.coerceIn(1, MAX_BOOKING_GUESTS)

    private val _state = MutableStateFlow(BookRefugeUiState(arrival = today(), maxGuests = maxGuests, pageStart = mondayOf(today())))
    val state: StateFlow<BookRefugeUiState> = _state.asStateFlow()

    private var pageJob: Job? = null
    // Vero se il foglio è stato aperto da "Prenota" senza un giorno: appena si leggono i posti, l'arrivo diventa
    // la prima notte libera.
    private var pickFirstFree = false

    /**
     * Prepara il foglio all'apertura. Con [arrival] (un giorno toccato nella pagina del rifugio) l'arrivo è quel giorno
     * e il tocco successivo sceglie la partenza; senza, l'arrivo diventa la prima notte libera. Toglie l'eventuale
     * conferma di una prenotazione precedente.
     */
    fun open(arrival: LocalDate?) {
        if (_state.value.submitting) return
        val start = arrival?.takeIf { it >= today() } ?: today()
        pickFirstFree = arrival == null
        _state.update {
            it.copy(arrival = start, nights = 1, pickingDeparture = true, pageStart = mondayOf(start), done = null, bookedTotalCents = null, error = null)
        }
        loadPage(mondayOf(start))
    }

    /**
     * Gestisce il tocco su un giorno del calendario: il primo tocco sceglie l'arrivo, il secondo la partenza. Un
     * giorno prima dell'arrivo, o un terzo tocco, ricomincia dall'arrivo. La partenza si accetta solo se il soggiorno
     * non supera [MAX_BOOKING_NIGHTS] e tutte le notti hanno posto; i giorni passati si ignorano.
     */
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

    /** Sposta il calendario di due settimane avanti o indietro, senza mai mostrare una pagina tutta nel passato. */
    fun onPage(forward: Boolean) {
        val current = _state.value
        val first = mondayOf(today())
        val next = current.pageStart.plus(DatePeriod(days = if (forward) CALENDAR_PAGE_DAYS else -CALENDAR_PAGE_DAYS))
        if (next < first) return
        _state.update { it.copy(pageStart = next) }
        loadPage(next)
    }

    /** Riprova a caricare la pagina mostrata dopo un errore. */
    fun retryPage() = loadPage(_state.value.pageStart)

    /** Imposta il giorno di arrivo. */
    fun onArrivalChange(date: LocalDate) = edit { copy(arrival = date) }

    /** Imposta il numero di notti, tra 1 e [MAX_BOOKING_NIGHTS]. */
    fun onNightsChange(value: Int) = edit { copy(nights = value.coerceIn(1, MAX_BOOKING_NIGHTS)) }

    /** Imposta il numero di persone, tra 1 e il massimo consentito per le notti scelte. */
    fun onGuestsChange(value: Int) = edit { copy(guests = value.coerceIn(1, guestsCap)) }

    /** Invia la prenotazione tramite l'outbox e porta il foglio alla conferma, o mostra l'errore. */
    fun submit() {
        val current = _state.value
        // Protezione dal doppio tocco: ogni invio genera una chiave nuova, quindi un secondo invio creerebbe una
        // seconda prenotazione.
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
                    is BookingOutcome.Booked -> state.copy(submitting = false, done = BookingDone.BOOKED, bookedTotalCents = outcome.booking.totalPriceCents)
                    BookingOutcome.Queued -> state.copy(submitting = false, done = BookingDone.QUEUED)
                    // Rifiuto (per esempio posti esauriti o date non valide): le scelte restano, così l'utente le può
                    // cambiare e riprovare.
                    is BookingOutcome.Rejected -> state.copy(submitting = false, error = outcome.cause.toUiMessage())
                }
            }
        }
    }

    /**
     * Carica i posti liberi della pagina che parte da [start]. Chiede solo le notti da oggi in poi, perché il server
     * rifiuta date passate, e non fa la richiesta se le notti sono già tutte note.
     */
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

    /** Se il foglio era aperto senza un giorno, sposta l'arrivo sulla prima notte libera tra quelle note. */
    private fun choosePickedFirstFree() {
        if (!pickFirstFree) return
        pickFirstFree = false
        val current = _state.value
        val first = current.freeBeds.keys.filter { it >= today() && current.nightFits(it) }.minOrNull() ?: return
        if (first != current.arrival) {
            _state.update { it.copy(arrival = first, nights = 1, pickingDeparture = true, pageStart = mondayOf(first)) }
        }
    }

    /** Applica [change] allo stato, a meno che ci sia un invio in corso o la conferma sia già mostrata. */
    private fun edit(change: BookRefugeUiState.() -> BookRefugeUiState) {
        if (_state.value.submitting || _state.value.done != null) return
        _state.update { it.change().copy(error = null) }
    }
}

/** Lunedì della settimana di [date], da cui partono le pagine del calendario. */
internal fun mondayOf(date: LocalDate): LocalDate = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))
