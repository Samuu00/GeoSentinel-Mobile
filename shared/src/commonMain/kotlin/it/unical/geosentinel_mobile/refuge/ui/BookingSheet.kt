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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import it.unical.geosentinel_mobile.core.ui.contours
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

// Da quanti posti in giù una notte è "agli ultimi posti" (numero in ambra e la parola "ultimi")
internal const val FEW_BEDS = 3

// Oltre questa scala del testo le celle del calendario mostrano solo il giorno: i posti restano nella descrizione
private const val LargeTextScale = 1.3f

/**
 * Foglio "Prenota" (redesign 11/2026, docs/UI_REDESIGN_PLAN.md §3.3): sopra la pagina del rifugio, un compito solo.
 * Calendario a due settimane con i posti di ogni notte, persone, riepilogo e "Conferma"; poi la conferma nello stesso
 * foglio. Si chiude trascinando in giù o con "Annulla", non durante l'invio.
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
        // durante l'invio non si chiude trascinando: l'utente deve vedere com'è andata
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

@Composable
private fun BookingForm(refugeName: String, state: BookRefugeUiState, viewModel: BookRefugeViewModel, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // barra del foglio: Annulla a sinistra, titolo al centro (come i fogli di iOS)
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
    // perché il "+" si è fermato: la notte più piena del soggiorno o il limite per prenotazione
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
    // riepilogo vivo: lo screen reader lo legge a ogni cambio
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

// ---- Calendario ----

/**
 * Due settimane da lunedì a domenica, con le frecce per spostarsi. Ogni giorno è un pulsante: il primo tocco sceglie
 * l'arrivo, il secondo (più avanti) la partenza. I giorni passati e le notti senza posti per tutti sono spenti.
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
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.booking_calendar_prev))
        }
        Text(
            stringResource(Res.string.booking_range, start.day, stringResource(start.monthName()), end.day, stringResource(end.monthName())),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = { onPage(true) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.booking_calendar_next))
        }
    }
    // intestazione dei giorni: decorativa (ogni cella dice il suo giorno)
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
                // la fascia del soggiorno: piena nei giorni in mezzo, mezza sull'arrivo (a destra) e sulla partenza (a sinistra)
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

// − valore +, con pulsanti da 48 dp (§2) e il valore annunciato quando cambia
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

// ---- Conferma: il momento "di picco" del flusso ----

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

// La spunta che si disegna in 400 ms dentro il tondo muschio; con "Riduci movimento" compare già intera
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

// ---- Biglietto del soggiorno: conferma e "Prossimo soggiorno" nelle prenotazioni ----

/**
 * Il soggiorno come un biglietto: blocco della data d'arrivo, rifugio, date, notti e persone, su una card da 28 con le
 * curve di livello appena accennate. [note] (es. "Tra 3 giorni") in verde; [actions] sotto uno strappo tratteggiato.
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
    val nights = (checkOut.toEpochDays() - checkIn.toEpochDays()).toInt()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(card)
            .contours(over = card, strength = 0.06f),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
            DateBlock(checkIn)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(refugeName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${checkIn.shortLabel()} → ${checkOut.shortLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    pluralStringResource(Res.plurals.booking_nights_count, nights, nights) + " · " +
                        pluralStringResource(Res.plurals.booking_guests_count, guests, guests),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (actions != null) {
            val dash = colors.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 20.dp)) {
                drawLine(dash, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                content = actions,
            )
        }
    }
}

/** Il giorno grande (Barlow 22) e il mese in tre lettere, su un riquadro muschio: si legge la data senza leggere. */
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

// ---- Date ----

/** "sab 15 novembre" per gli screen reader: la cella mostra solo il numero. */
@Composable
internal fun LocalDate.longLabel(): String = "${stringResource(weekday())} $day ${stringResource(monthName())}"

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
