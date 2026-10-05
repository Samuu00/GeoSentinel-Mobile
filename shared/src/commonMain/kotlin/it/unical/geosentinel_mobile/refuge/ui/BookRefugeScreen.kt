package it.unical.geosentinel_mobile.refuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.booking_arrival
import geosentinel_mobile.shared.generated.resources.booking_arrival_change
import geosentinel_mobile.shared.generated.resources.booking_body
import geosentinel_mobile.shared.generated.resources.booking_date_ok
import geosentinel_mobile.shared.generated.resources.booking_departure
import geosentinel_mobile.shared.generated.resources.booking_done
import geosentinel_mobile.shared.generated.resources.booking_done_body
import geosentinel_mobile.shared.generated.resources.booking_done_title
import geosentinel_mobile.shared.generated.resources.booking_guests
import geosentinel_mobile.shared.generated.resources.booking_guests_max
import geosentinel_mobile.shared.generated.resources.booking_less
import geosentinel_mobile.shared.generated.resources.booking_more
import geosentinel_mobile.shared.generated.resources.booking_nights
import geosentinel_mobile.shared.generated.resources.booking_nights_max
import geosentinel_mobile.shared.generated.resources.booking_queued_body
import geosentinel_mobile.shared.generated.resources.booking_queued_title
import geosentinel_mobile.shared.generated.resources.booking_send
import geosentinel_mobile.shared.generated.resources.booking_title
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.Instant

private val FieldShape = RoundedCornerShape(20.dp)

/**
 * "Prenota" (M5.2), spinto dal dettaglio del rifugio: arrivo, notti e persone, poi la conferma. Se il server non risponde
 * la prenotazione resta in coda e la conferma lo dice ("in invio"); se rifiuta (posti finiti) il form resta compilato.
 */
@Composable
fun BookRefugeScreen(refugeId: String, refugeName: String, capacity: Int, onBack: () -> Unit) {
    val viewModel = koinViewModel<BookRefugeViewModel> { parametersOf(refugeId, capacity) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    state.done?.let { done ->
        val booked = done == BookingDone.BOOKED
        FormScaffold(
            onBack = onBack,
            title = if (booked) Res.string.booking_done_title else Res.string.booking_queued_title,
            body = if (booked) Res.string.booking_done_body else Res.string.booking_queued_body,
            actions = { PrimaryPillButton(stringResource(Res.string.booking_done), onClick = onBack) },
        ) {
            Summary(refugeName, state)
        }
        return
    }

    var pickingDate by rememberSaveable { mutableStateOf(false) }
    FormScaffold(
        onBack = onBack,
        title = Res.string.booking_title,
        body = Res.string.booking_body,
        actions = { PrimaryPillButton(stringResource(Res.string.booking_send), onClick = viewModel::submit, loading = state.submitting) },
    ) {
        Text(refugeName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
        FieldCard(Res.string.booking_arrival) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.arrival.shortLabel(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { pickingDate = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.booking_arrival_change), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        FieldCard(Res.string.booking_nights) {
            Stepper(
                value = state.nights,
                canDecrease = state.nights > 1,
                canIncrease = state.nights < MAX_BOOKING_NIGHTS,
                onChange = viewModel::onNightsChange,
            )
            Text(
                stringResource(Res.string.booking_departure, state.departure.shortLabel()) + " · " +
                    stringResource(Res.string.booking_nights_max, MAX_BOOKING_NIGHTS),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FieldCard(Res.string.booking_guests) {
            Stepper(
                value = state.guests,
                canDecrease = state.guests > 1,
                canIncrease = state.guests < viewModel.maxGuests,
                onChange = viewModel::onGuestsChange,
            )
            Text(
                stringResource(Res.string.booking_guests_max, viewModel.maxGuests),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FormErrorBanner(state.error?.resolve())
    }

    if (pickingDate) {
        ArrivalPicker(
            selected = state.arrival,
            onPick = {
                viewModel.onArrivalChange(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

// Riepilogo nella conferma: rifugio, date e persone
@Composable
private fun Summary(refugeName: String, state: BookRefugeUiState) {
    Column(
        Modifier.fillMaxWidth().clip(FieldShape).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FieldShape).padding(16.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Text(refugeName, style = MaterialTheme.typography.titleMedium)
        Text(
            stayLabel(state.arrival, state.departure, state.nights, state.guests),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// Un campo del form come le card di FormControls: titolo sopra, contenuto in una card con il filo di bordo
@Composable
private fun FieldCard(title: StringResource, content: @Composable () -> Unit) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
        Column(
            Modifier.fillMaxWidth().clip(FieldShape).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FieldShape).padding(horizontal = 16.dp, vertical = 10.dp),
        ) { content() }
    }
}

// − valore +, con pulsanti da 48 dp (§2) e il valore annunciato quando cambia
@Composable
private fun Stepper(value: Int, canDecrease: Boolean, canIncrease: Boolean, onChange: (Int) -> Unit) {
    val less = stringResource(Res.string.booking_less)
    val more = stringResource(Res.string.booking_more)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = canDecrease,
            modifier = Modifier.size(48.dp).semantics { contentDescription = less },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.widthIn(min = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = canIncrease,
            modifier = Modifier.size(48.dp).semantics { contentDescription = more },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

// Selettore di sistema di Material 3: solo da oggi in poi (il server rifiuta un arrivo nel passato).
// Il DatePicker lavora in millisecondi UTC a mezzanotte: la conversione passa sempre da UTC
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrivalPicker(selected: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val todayMillis = today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) } ?: onDismiss()
            }) { Text(stringResource(Res.string.booking_date_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.auth_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
