package it.unical.geosentinel_mobile.refuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.booking_cancel
import geosentinel_mobile.shared.generated.resources.booking_cancel_body
import geosentinel_mobile.shared.generated.resources.booking_cancel_title
import geosentinel_mobile.shared.generated.resources.booking_guests_count
import geosentinel_mobile.shared.generated.resources.booking_keep
import geosentinel_mobile.shared.generated.resources.booking_nights_count
import geosentinel_mobile.shared.generated.resources.booking_status_cancelled
import geosentinel_mobile.shared.generated.resources.booking_status_confirmed
import geosentinel_mobile.shared.generated.resources.booking_rejected_expired
import geosentinel_mobile.shared.generated.resources.booking_rejected_ok
import geosentinel_mobile.shared.generated.resources.booking_status_expired
import geosentinel_mobile.shared.generated.resources.booking_status_pending
import geosentinel_mobile.shared.generated.resources.booking_status_rejected
import geosentinel_mobile.shared.generated.resources.booking_status_unknown
import geosentinel_mobile.shared.generated.resources.my_bookings_empty
import geosentinel_mobile.shared.generated.resources.my_bookings_refuge_unknown
import geosentinel_mobile.shared.generated.resources.my_bookings_title
import geosentinel_mobile.shared.generated.resources.trail_retry
import it.unical.geosentinel_mobile.api.model.BookingResponse
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.PageHeader
import it.unical.geosentinel_mobile.core.ui.groupedItem
import it.unical.geosentinel_mobile.core.ui.PageHeaderHeight
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.uiMessage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val ContentMaxWidth = 600.dp
private val CardShape = RoundedCornerShape(20.dp)
private const val PrefetchDistance = 3

/**
 * "Le mie prenotazioni" (M5.2), spinta dalla scheda Rifugi: in cima quelle "in invio" (in coda nell'outbox), poi quelle
 * del server dalla più lontana nel futuro, con lo stato in parole. Si annulla con una conferma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<MyBookingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - PrefetchDistance
        }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") { PageHeader(title = stringResource(Res.string.my_bookings_title)) }
                item(key = "group-gap") { Spacer(Modifier.height(8.dp)) }
                if (state.cancelError != null) {
                    item(key = "cancel-error") {
                        Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
                            FormErrorBanner(state.cancelError?.resolve())
                        }
                    }
                }
                // in coda e poi rifiutate (o scadute): l'utente le aveva viste "in invio", qui sa che non ci sono
                items(state.rejected, key = { "rejected-" + it.key }) { rejected ->
                    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BookingCard(
                            refugeName = state.refugeNames[rejected.payload.refugeId],
                            stay = stayLabel(rejected.payload.checkIn, rejected.payload.checkOut, null, rejected.payload.guests),
                            status = if (rejected.error == null) Res.string.booking_status_expired else Res.string.booking_status_rejected,
                            statusColors = MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        ) {
                            Text(
                                rejected.error?.uiMessage()?.resolve() ?: stringResource(Res.string.booking_rejected_expired),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            TextButton(onClick = { viewModel.dismissRejected(rejected.key) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(Res.string.booking_rejected_ok))
                            }
                        }
                    }
                }
                items(state.pending, key = { "pending-" + it.key }) { pending ->
                    BookingCard(
                        refugeName = state.refugeNames[pending.payload.refugeId],
                        stay = stayLabel(pending.payload.checkIn, pending.payload.checkOut, null, pending.payload.guests),
                        status = Res.string.booking_status_pending,
                        statusColors = MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface,
                    )
                }
                when {
                    state.items.isEmpty() && state.loading -> item(key = "loading") { CenteredProgress() }
                    state.items.isEmpty() && state.error != null -> item(key = "error") {
                        Message(state.error!!.resolve(), isError = true) {
                            PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = viewModel::retry)
                        }
                    }
                    state.items.isEmpty() && state.pending.isEmpty() && state.rejected.isEmpty() -> item(key = "empty") { Message(stringResource(Res.string.my_bookings_empty)) }
                    else -> {
                        // le prenotazioni in un gruppo solo; in coda e rifiutate restano card a sé, sopra
                        itemsIndexed(state.items, key = { _, booking -> booking.id }) { index, booking ->
                            val (status, colors) = booking.status.presentation()
                            BookingCard(
                                first = index == 0,
                                last = index == state.items.lastIndex,
                                refugeName = state.refugeNames[booking.refugeId],
                                stay = stayLabel(booking.checkIn, booking.checkOut, null, booking.guests),
                                status = status,
                                statusColors = colors,
                                onCancel = if (viewModel.canCancel(booking)) ({ confirming = booking.id }) else null,
                                cancelling = state.cancelling == booking.id,
                            )
                        }
                        when {
                            state.loadingMore -> item(key = "more") { CenteredProgress(small = true) }
                            state.error != null -> item(key = "more-error") {
                                Message(state.error!!.resolve(), isError = true) {
                                    TextButton(onClick = viewModel::retry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.trail_retry)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        BackButton(onBack)
    }

    confirming?.let { id ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(Res.string.booking_cancel_title)) },
            text = { Text(stringResource(Res.string.booking_cancel_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    viewModel.cancel(id)
                }) { Text(stringResource(Res.string.booking_cancel)) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(Res.string.booking_keep)) } },
        )
    }
}

/** "sab 10/10 → lun 12/10 · 2 notti · 2 persone". [nights] null: si calcola dalle date. */
@Composable
internal fun stayLabel(checkIn: LocalDate, checkOut: LocalDate, nights: Int?, guests: Int): String {
    val count = nights ?: (checkOut.toEpochDays() - checkIn.toEpochDays()).toInt()
    return "${checkIn.shortLabel()} → ${checkOut.shortLabel()} · " +
        pluralStringResource(Res.plurals.booking_nights_count, count, count) + " · " +
        pluralStringResource(Res.plurals.booking_guests_count, guests, guests)
}

// Lo stato è sempre scritto; il colore lo accompagna e basta (niente rosso: "Annullata" non è un'emergenza)
@Composable
private fun BookingResponse.Status.presentation(): Pair<StringResource, Pair<Color, Color>> {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        BookingResponse.Status.CONFIRMED -> Res.string.booking_status_confirmed to (colors.primaryContainer to colors.onPrimaryContainer)
        BookingResponse.Status.CANCELLED -> Res.string.booking_status_cancelled to (colors.surfaceContainerHigh to colors.onSurfaceVariant)
        BookingResponse.Status.unknown_default_open_api -> Res.string.booking_status_unknown to (colors.surfaceContainerHigh to colors.onSurfaceVariant)
    }
}

@Composable
private fun BookingCard(
    refugeName: String?,
    stay: String,
    status: StringResource,
    statusColors: Pair<Color, Color>,
    onCancel: (() -> Unit)? = null,
    cancelling: Boolean = false,
    // posizione nel gruppo: da sola (in coda, rifiutata) è una card intera
    first: Boolean = true,
    last: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = if (last) 10.dp else 0.dp)
            .groupedItem(first, last),
    ) {
        if (!first) GroupDivider()
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (onCancel != null) 4.dp else 14.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) { }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        refugeName ?: stringResource(Res.string.my_bookings_refuge_unknown),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Text(
                        stringResource(status),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColors.second,
                        modifier = Modifier.clip(CircleShape).background(statusColors.first).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Text(stay, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (onCancel != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (cancelling) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    TextButton(onClick = onCancel, enabled = !cancelling, modifier = Modifier.heightIn(min = 48.dp)) {
                        // prugna come gli altri errori del tema: annullare non è un'emergenza (il rosso resta all'SOS)
                        Text(stringResource(Res.string.booking_cancel), color = colors.error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredProgress(small: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = if (small) 12.dp else 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(if (small) 24.dp else 36.dp), strokeWidth = if (small) 2.dp else 3.dp)
    }
}

@Composable
private fun Message(text: String, isError: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}
