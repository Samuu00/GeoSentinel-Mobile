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
import it.unical.geosentinel_mobile.core.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

// Le notti di una prenotazione: il server ne accetta al massimo 14 (come il calendario del dettaglio)
const val MAX_BOOKING_NIGHTS = AVAILABILITY_NIGHTS

/** Com'è finito l'invio: la schermata passa alla conferma. */
enum class BookingDone { BOOKED, QUEUED }

data class BookRefugeUiState(
    val arrival: LocalDate,
    val nights: Int = 1,
    val guests: Int = 1,
    val submitting: Boolean = false,
    val done: BookingDone? = null,
    val error: UiMessage? = null,
) {
    val departure: LocalDate get() = arrival.plus(DatePeriod(days = nights))

    // niente date nei log
    override fun toString() = "BookRefugeUiState(nights=$nights, guests=$guests, submitting=$submitting, done=$done, error=${error != null})"
}

/**
 * Form "Prenota" (M5.2): arrivo, notti (1–14) e persone (1–posti del rifugio). L'invio passa dall'outbox
 * ([BookingRepository.book]): senza risposta la prenotazione resta "in invio" e parte da sola più tardi.
 * [ownerId]: l'id dell'utente loggato (dal profilo o, senza rete, dal token; null da ospite).
 */
class BookRefugeViewModel(
    private val refugeId: String,
    private val capacity: Int,
    private val bookings: BookingRepository,
    private val ownerId: suspend () -> String?,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val _state = MutableStateFlow(BookRefugeUiState(arrival = today()))
    val state: StateFlow<BookRefugeUiState> = _state.asStateFlow()

    val maxGuests: Int get() = capacity.coerceAtLeast(1)

    fun onArrivalChange(date: LocalDate) = edit { copy(arrival = date) }

    fun onNightsChange(value: Int) = edit { copy(nights = value.coerceIn(1, MAX_BOOKING_NIGHTS)) }

    fun onGuestsChange(value: Int) = edit { copy(guests = value.coerceIn(1, maxGuests)) }

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
                    // capacity-exceeded, date non valide...: il form resta compilato, si cambia e si riprova
                    is BookingOutcome.Rejected -> state.copy(submitting = false, error = outcome.cause.toUiMessage())
                }
            }
        }
    }

    private fun edit(change: BookRefugeUiState.() -> BookRefugeUiState) {
        if (_state.value.submitting || _state.value.done != null) return
        _state.update { it.change().copy(error = null) }
    }
}
