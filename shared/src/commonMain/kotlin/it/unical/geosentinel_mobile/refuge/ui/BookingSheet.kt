// Foglio "Prenota" che si apre sopra la pagina del rifugio: calendario a due settimane con i posti liberi, numero
// di persone, riepilogo e conferma animata. Contiene anche il "biglietto" del soggiorno e il blocco della data,
// riusati in "Le mie prenotazioni", e alcune funzioni per scrivere le date in italiano.
package it.unical.geosentinel_mobile.refuge.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.booking_calendar_next
import geosentinel_mobile.shared.generated.resources.booking_calendar_prev
import geosentinel_mobile.shared.generated.resources.booking_confirm
import geosentinel_mobile.shared.generated.resources.booking_day_arrival
import geosentinel_mobile.shared.generated.resources.booking_day_departure
import geosentinel_mobile.shared.generated.resources.booking_day_in_stay
import geosentinel_mobile.shared.generated.resources.booking_done
import geosentinel_mobile.shared.generated.resources.booking_done_body
import geosentinel_mobile.shared.generated.resources.booking_done_title
import geosentinel_mobile.shared.generated.resources.booking_guests
import geosentinel_mobile.shared.generated.resources.booking_guests_count
import geosentinel_mobile.shared.generated.resources.booking_guests_limit
import geosentinel_mobile.shared.generated.resources.booking_less
import geosentinel_mobile.shared.generated.resources.booking_more
import geosentinel_mobile.shared.generated.resources.booking_nights_count
import geosentinel_mobile.shared.generated.resources.booking_page_failed
import geosentinel_mobile.shared.generated.resources.booking_pick_arrival
import geosentinel_mobile.shared.generated.resources.booking_pick_departure
import geosentinel_mobile.shared.generated.resources.booking_queued_body
import geosentinel_mobile.shared.generated.resources.booking_queued_title
import geosentinel_mobile.shared.generated.resources.booking_range
import geosentinel_mobile.shared.generated.resources.booking_see_bookings
import geosentinel_mobile.shared.generated.resources.booking_stay_limit
import geosentinel_mobile.shared.generated.resources.booking_title
import geosentinel_mobile.shared.generated.resources.month_1
import geosentinel_mobile.shared.generated.resources.month_10
import geosentinel_mobile.shared.generated.resources.month_11
import geosentinel_mobile.shared.generated.resources.month_12
import geosentinel_mobile.shared.generated.resources.month_2
import geosentinel_mobile.shared.generated.resources.month_3
import geosentinel_mobile.shared.generated.resources.month_4
import geosentinel_mobile.shared.generated.resources.month_5
import geosentinel_mobile.shared.generated.resources.month_6
import geosentinel_mobile.shared.generated.resources.month_7
import geosentinel_mobile.shared.generated.resources.month_8
import geosentinel_mobile.shared.generated.resources.month_9
import geosentinel_mobile.shared.generated.resources.refuge_free_beds
import geosentinel_mobile.shared.generated.resources.refuge_full
import geosentinel_mobile.shared.generated.resources.trail_retry
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.GroupCard
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.rememberReduceMotion
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.theme.warningTextColor
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.core.ui.cardShadow

/** Soglia dei posti liberi sotto la quale (compresa) una notte è "agli ultimi posti" e si evidenzia in ambra. */
internal const val FEW_BEDS = 3

/**
 * Scala del testo oltre la quale le celle del calendario mostrano solo il giorno, per non uscire dalla cella. I posti
 * restano comunque nella descrizione letta dallo screen reader.
 */
private const val LargeTextScale = 1.3f

/**
 * Foglio modale "Prenota", aperto sopra la pagina del rifugio per un solo compito. Mostra il form (calendario,
 * persone, riepilogo, "Conferma") e, dopo l'invio, la conferma nello stesso foglio con una dissolvenza.
 *
 * Si chiude trascinandolo in giù o con "Annulla", ma non durante l'invio. Le tre funzioni di chiusura si chiamano
 * dopo l'animazione che nasconde il foglio: [onDismiss] per l'annullamento, [onBooked] per "Fatto", [onOpenBookings]
 * per andare a "Le mie prenotazioni".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookingSheet(
    refugeName: String,
    viewModel: BookRefugeViewModel,
    onDismiss: () -> Unit,
    onBooked: () -> Unit,
    onOpenBookings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Durante l'invio il foglio non si chiude trascinando: l'utente deve vedere com'è andata.
        confirmValueChange = { it != SheetValue.Hidden || !viewModel.state.value.submitting },
    )
    val scope = rememberCoroutineScope()
    val close: (then: () -> Unit) -> Unit = { then -> scope.launch { sheetState.hide() }.invokeOnCompletion { then() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        AnimatedContent(
            targetState = state.done,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "booking",
        ) { done ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 16.dp),
            ) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                    if (done == null) {
                        BookingForm(refugeName = refugeName, state = state, viewModel = viewModel, onCancel = { close(onDismiss) })
                    } else {
                        BookingConfirmation(
                            refugeName = refugeName,
                            state = state,
                            booked = done == BookingDone.BOOKED,
                            onDone = { close(onBooked) },
                            onOpenBookings = { close(onOpenBookings) },
                        )
                    }
                }
            }
        }
    }
}

/** Form della prenotazione: barra con "Annulla", calendario, persone, riepilogo, errore e pulsante di conferma. */
@Composable
private fun BookingForm(refugeName: String, state: BookRefugeUiState, viewModel: BookRefugeViewModel, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Barra del foglio come nei fogli di iOS: "Annulla" a sinistra e titolo al centro.
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        TextButton(onClick = onCancel, enabled = !state.submitting, modifier = Modifier.align(Alignment.CenterStart).heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.auth_cancel), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            stringResource(Res.string.booking_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
    }
    Text(
        refugeName,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    )

    StayCalendar(state, onDay = viewModel::onDayTapped, onPage = viewModel::onPage)
    when {
        state.loadingPage -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp))
        state.pageError != null -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(Res.string.booking_page_failed),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::retryPage, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.trail_retry)) }
        }
        else -> Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(16.dp))
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.booking_guests), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Stepper(
                value = state.guests,
                canDecrease = state.guests > 1,
                canIncrease = state.guests < state.guestsCap,
                onChange = viewModel::onGuestsChange,
            )
        }
    }
    // Spiega perché il "+" non sale più: la notte più piena del soggiorno oppure il limite di persone per prenotazione.
    val tightest = state.tightestNight
    val limit = when {
        state.guests < state.guestsCap -> null
        tightest != null && tightest.freeBeds < state.maxGuests ->
            stringResource(Res.string.booking_stay_limit, tightest.freeBeds, tightest.date.shortLabel())
        state.maxGuests == MAX_BOOKING_GUESTS -> stringResource(Res.string.booking_guests_limit, MAX_BOOKING_GUESTS)
        else -> null
    }
    if (limit != null) {
        Text(limit, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }

    Spacer(Modifier.height(24.dp))
    // Riepilogo del soggiorno come regione "live": lo screen reader lo rilegge a ogni cambio.
    Text(
        stayLabel(state.arrival, state.departure, state.nights, state.guests),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(
        stringResource(if (state.pickingDeparture) Res.string.booking_pick_departure else Res.string.booking_pick_arrival),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
    )
    FormErrorBanner(state.error?.resolve())
    if (state.error != null) Spacer(Modifier.height(12.dp))
    PrimaryPillButton(stringResource(Res.string.booking_confirm), onClick = viewModel::submit, loading = state.submitting)
}

// Calendario.

/**
 * Calendario di due settimane, da lunedì a domenica, con le frecce per cambiare pagina. Ogni giorno è un pulsante:
 * il primo tocco sceglie l'arrivo, il secondo (su un giorno successivo) la partenza. I giorni passati e le notti
 * senza posti per tutte le persone sono disattivati. [onPage] riceve true per andare avanti.
 */
@Composable
private fun StayCalendar(state: BookRefugeUiState, onDay: (LocalDate) -> Unit, onPage: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val start = state.pageStart
    val end = start.plus(DatePeriod(days = CALENDAR_PAGE_DAYS - 1))
    val haptics = LocalHapticFeedback.current
    val compact = LocalDensity.current.fontScale > LargeTextScale

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onPage(false) }, enabled = start > mondayOf(today), modifier = Modifier.size(48.dp)) {
            Icon(AppIcons.ChevronLeft, contentDescription = stringResource(Res.string.booking_calendar_prev))
        }
        Text(
            stringResource(Res.string.booking_range, start.day, stringResource(start.monthName()), end.day, stringResource(end.monthName())),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = { onPage(true) }, modifier = Modifier.size(48.dp)) {
            Icon(AppIcons.ChevronRight, contentDescription = stringResource(Res.string.booking_calendar_next))
        }
    }
    // Intestazione con i nomi dei giorni, nascosta allo screen reader perché ogni cella legge già il suo giorno.
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).clearAndSetSemantics { }) {
        (0 until 7).forEach { offset ->
            Text(
                stringResource(start.plus(DatePeriod(days = offset)).weekday()),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    repeat(2) { week ->
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { offset ->
                val date = start.plus(DatePeriod(days = week * 7 + offset))
                DayCell(
                    date = date,
                    state = state,
                    today = today,
                    compact = compact,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onDay(date)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Cella di un giorno del calendario: il numero, cerchiato se è arrivo o partenza, la fascia colorata del soggiorno e
 * sotto i posti liberi (in ambra se sono pochi). Un giorno è attivo se non è passato e la notte ha posto, oppure se
 * può fare da partenza. Per lo screen reader la cella è un pulsante con una descrizione completa: data, posti e ruolo
 * nel soggiorno.
 */
@Composable
private fun DayCell(date: LocalDate, state: BookRefugeUiState, today: LocalDate, compact: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val free = state.freeBeds[date]
    val isArrival = date == state.arrival
    val isDeparture = date == state.departure
    val inStay = date > state.arrival && date < state.departure
    val span = (date.toEpochDays() - state.arrival.toEpochDays()).toInt()
    val canDepart = state.pickingDeparture && span in 1..MAX_BOOKING_NIGHTS &&
        (0 until span).all { state.nightFits(state.arrival.plus(DatePeriod(days = it))) }
    val enabled = date >= today && (state.nightFits(date) || canDepart) && !state.submitting
    val endpoint = isArrival || isDeparture
    val band = colors.primaryContainer

    val bedsLabel = when {
        free == null || date < today -> null
        free <= 0 -> stringResource(Res.string.refuge_full)
        else -> pluralStringResource(Res.plurals.refuge_free_beds, free, free)
    }
    val roleLabel = when {
        isArrival -> stringResource(Res.string.booking_day_arrival)
        isDeparture -> stringResource(Res.string.booking_day_departure)
        inStay -> stringResource(Res.string.booking_day_in_stay)
        else -> null
    }
    val description = listOfNotNull(date.longLabel(), bedsLabel, roleLabel).joinToString(", ")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                selected = endpoint
                if (!enabled) disabled()
            }
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                // Fascia del soggiorno: piena nei giorni in mezzo, solo la metà destra sull'arrivo e solo la metà
                // sinistra sulla partenza, così si unisce ai cerchi.
                .drawBehind {
                    val half = size.width / 2
                    when {
                        inStay -> drawRect(band)
                        isArrival -> drawRect(band, topLeft = Offset(half, 0f), size = Size(half, size.height))
                        isDeparture -> drawRect(band, size = Size(half, size.height))
                    }
                },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (endpoint) colors.primary else Color.Transparent),
            ) {
                Text(
                    date.day.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (endpoint || date == today) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        endpoint -> colors.onPrimary
                        inStay -> colors.onPrimaryContainer
                        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.45f)
                        else -> colors.onSurface
                    },
                )
            }
        }
        if (!compact && free != null && date >= today) {
            Text(
                if (free <= 0) "—" else free.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    free <= 0 -> colors.onSurfaceVariant
                    free <= FEW_BEDS -> warningTextColor()
                    else -> colors.primary
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Contatore con i pulsanti meno e più da 48 dp (il bersaglio minimo) e il valore al centro, che lo screen reader
 * annuncia quando cambia.
 */
@Composable
private fun Stepper(value: Int, canDecrease: Boolean, canIncrease: Boolean, onChange: (Int) -> Unit) {
    val less = stringResource(Res.string.booking_less)
    val more = stringResource(Res.string.booking_more)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = canDecrease,
            modifier = Modifier.size(48.dp).semantics { contentDescription = less },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = canIncrease,
            modifier = Modifier.size(48.dp).semantics { contentDescription = more },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

// Conferma.

/**
 * Schermata di conferma nel foglio, con la spunta animata, una vibrazione di conferma e il biglietto del soggiorno.
 * [booked] distingue la prenotazione creata da quella rimasta in coda, che ha un testo diverso.
 */
@Composable
private fun BookingConfirmation(refugeName: String, state: BookRefugeUiState, booked: Boolean, onDone: () -> Unit, onOpenBookings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        AnimatedCheck()
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(if (booked) Res.string.booking_done_title else Res.string.booking_queued_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (booked) Res.string.booking_done_body else Res.string.booking_queued_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
        StayTicket(refugeName, state.arrival, state.departure, state.guests)
        Spacer(Modifier.height(24.dp))
        PrimaryPillButton(stringResource(Res.string.booking_done), onClick = onDone)
        TextButton(onClick = onOpenBookings, modifier = Modifier.heightIn(min = 48.dp).padding(top = 4.dp)) {
            Text(stringResource(Res.string.booking_see_bookings), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Spunta che si disegna in 400 ms dentro un tondo colorato. Se l'utente ha attivato "Riduci movimento" compare già
 * intera, senza animazione.
 */
@Composable
private fun AnimatedCheck() {
    val colors = MaterialTheme.colorScheme
    val still = rememberReduceMotion()
    val progress = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(400)) }
    Box(Modifier.size(88.dp).clip(CircleShape).background(colors.primaryContainer).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            val check = Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.54f)
                lineTo(size.width * 0.42f, size.height * 0.76f)
                lineTo(size.width * 0.84f, size.height * 0.28f)
            }
            val measure = PathMeasure().apply { setPath(check, false) }
            val drawn = Path()
            measure.getSegment(0f, measure.length * progress.value, drawn, true)
            drawPath(drawn, colors.onPrimaryContainer, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// Biglietto del soggiorno, usato nella conferma e in "Prossimo soggiorno" delle prenotazioni.

/**
 * Il soggiorno disegnato come un biglietto: in una fascia colorata l'eventuale [note] in maiuscolo (per esempio
 * "Tra 3 giorni"), il nome del rifugio e la riga con date, notti e persone. Se ci sono [actions], sotto compare una
 * perforazione tratteggiata e poi la riga delle azioni; senza azioni il biglietto finisce con la fascia.
 */
@Composable
internal fun StayTicket(
    refugeName: String,
    checkIn: LocalDate,
    checkOut: LocalDate,
    guests: Int,
    modifier: Modifier = Modifier,
    note: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val card = colors.surfaceContainerLowest
    val page = colors.background
    val nights = (checkOut.toEpochDays() - checkIn.toEpochDays()).toInt()
    val shape = RoundedCornerShape(26.dp)
    Column(modifier.fillMaxWidth().cardShadow(shape).clip(shape).background(card)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.primaryContainer)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)
                .semantics(mergeDescendants = true) { },
        ) {
            if (note != null) {
                Text(note.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer)
                Spacer(Modifier.height(6.dp))
            }
            Text(refugeName, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Text(
                "${checkIn.shortLabel()} → ${checkOut.shortLabel()} · " +
                    pluralStringResource(Res.plurals.booking_nights_count, nights, nights) + " · " +
                    pluralStringResource(Res.plurals.booking_guests_count, guests, guests),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // Perforazione: due mezzi cerchi nel colore della pagina sui bordi, che sembrano tacche, e un tratteggio in
        // mezzo.
        if (actions != null) {
            val dash = colors.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val r = 8.dp.toPx()
                drawCircle(page, r, Offset(0f, size.height / 2))
                drawCircle(page, r, Offset(size.width, size.height / 2))
                drawLine(
                    dash,
                    Offset(r * 2, size.height / 2),
                    Offset(size.width - r * 2, size.height / 2),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                content = actions,
            )
        }
    }
}

/**
 * Blocco della data: il giorno in grande e il mese in tre lettere su un riquadro colorato, per riconoscere la data a
 * colpo d'occhio. Con [muted] usa colori spenti, per le prenotazioni passate o annullate.
 */
@Composable
internal fun DateBlock(date: LocalDate, muted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (muted) colors.surfaceContainerHighest else colors.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        val content = if (muted) colors.onSurfaceVariant else colors.onPrimaryContainer
        Text(date.day.toString(), style = MaterialTheme.typography.headlineSmall, color = content)
        Text(stringResource(date.monthName()).take(3).uppercase(), style = MaterialTheme.typography.labelSmall, color = content)
    }
}

// Date in italiano.

/** Data estesa nella forma giorno della settimana, numero e mese, letta dallo screen reader al posto del solo numero. */
@Composable
internal fun LocalDate.longLabel(): String = "${stringResource(weekday())} $day ${stringResource(monthName())}"

/** Nome del mese della data, dalle risorse di testo. */
internal fun LocalDate.monthName(): StringResource = when (month.number) {
    1 -> Res.string.month_1
    2 -> Res.string.month_2
    3 -> Res.string.month_3
    4 -> Res.string.month_4
    5 -> Res.string.month_5
    6 -> Res.string.month_6
    7 -> Res.string.month_7
    8 -> Res.string.month_8
    9 -> Res.string.month_9
    10 -> Res.string.month_10
    11 -> Res.string.month_11
    else -> Res.string.month_12
}
