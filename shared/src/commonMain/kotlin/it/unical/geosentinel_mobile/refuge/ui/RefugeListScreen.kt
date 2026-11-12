package it.unical.geosentinel_mobile.refuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.booking_cancel
import geosentinel_mobile.shared.generated.resources.booking_cancel_body
import geosentinel_mobile.shared.generated.resources.booking_cancel_title
import geosentinel_mobile.shared.generated.resources.booking_guests_count
import geosentinel_mobile.shared.generated.resources.booking_keep
import geosentinel_mobile.shared.generated.resources.booking_nights_count
import geosentinel_mobile.shared.generated.resources.booking_rejected_expired
import geosentinel_mobile.shared.generated.resources.booking_rejected_ok
import geosentinel_mobile.shared.generated.resources.booking_status_cancelled
import geosentinel_mobile.shared.generated.resources.booking_status_pending
import geosentinel_mobile.shared.generated.resources.booking_status_unknown
import geosentinel_mobile.shared.generated.resources.my_bookings_empty_body
import geosentinel_mobile.shared.generated.resources.my_bookings_empty_title
import geosentinel_mobile.shared.generated.resources.my_bookings_explore
import geosentinel_mobile.shared.generated.resources.my_bookings_in_days
import geosentinel_mobile.shared.generated.resources.my_bookings_in_progress
import geosentinel_mobile.shared.generated.resources.my_bookings_loading
import geosentinel_mobile.shared.generated.resources.my_bookings_next_title
import geosentinel_mobile.shared.generated.resources.my_bookings_past_title
import geosentinel_mobile.shared.generated.resources.my_bookings_refuge_unknown
import geosentinel_mobile.shared.generated.resources.my_bookings_rejected_title
import geosentinel_mobile.shared.generated.resources.my_bookings_today
import geosentinel_mobile.shared.generated.resources.my_bookings_tomorrow
import geosentinel_mobile.shared.generated.resources.my_bookings_upcoming_title
import geosentinel_mobile.shared.generated.resources.refuge_altitude
import geosentinel_mobile.shared.generated.resources.refuge_beds
import geosentinel_mobile.shared.generated.resources.refuge_call
import geosentinel_mobile.shared.generated.resources.refuge_directions
import geosentinel_mobile.shared.generated.resources.refuge_open
import geosentinel_mobile.shared.generated.resources.refuges_empty_body
import geosentinel_mobile.shared.generated.resources.refuges_empty_title
import geosentinel_mobile.shared.generated.resources.refuges_loading
import geosentinel_mobile.shared.generated.resources.refuges_near_area
import geosentinel_mobile.shared.generated.resources.refuges_near_me
import geosentinel_mobile.shared.generated.resources.refuges_tab_bookings
import geosentinel_mobile.shared.generated.resources.refuges_tab_bookings_badge
import geosentinel_mobile.shared.generated.resources.refuges_tab_explore
import geosentinel_mobile.shared.generated.resources.refuges_title
import it.unical.geosentinel_mobile.api.model.BookingResponse
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.distanceTo
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.ui.EmptyState
import it.unical.geosentinel_mobile.core.ui.ErrorState
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.GroupCard
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.GroupRow
import it.unical.geosentinel_mobile.core.ui.LargeTitlePage
import it.unical.geosentinel_mobile.core.ui.LoadingMore
import it.unical.geosentinel_mobile.core.ui.MetaLine
import it.unical.geosentinel_mobile.core.ui.PageMaxWidth
import it.unical.geosentinel_mobile.core.ui.SectionHeader
import it.unical.geosentinel_mobile.core.ui.SegmentedControl
import it.unical.geosentinel_mobile.core.ui.SkeletonRows
import it.unical.geosentinel_mobile.core.ui.TonalPillButton
import it.unical.geosentinel_mobile.core.ui.contours
import it.unical.geosentinel_mobile.core.ui.directionsUri
import it.unical.geosentinel_mobile.core.ui.groupedItem
import it.unical.geosentinel_mobile.core.ui.rememberDialer
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.hazard.ui.distanceLabel
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.media.ui.photoRequest
import it.unical.geosentinel_mobile.media.ui.rememberSignedUrl
import it.unical.geosentinel_mobile.refuge.data.PendingBooking
import it.unical.geosentinel_mobile.refuge.data.RejectedBooking
import it.unical.geosentinel_mobile.trail.ui.DefaultArea
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberLocationState
import kotlin.time.Duration.Companion.seconds

// Quanto si aspetta la posizione (con il permesso già dato) prima di ripiegare sull'area di partenza
private val FixWait = 3.seconds

/** Segmenti della scheda Rifugi. */
const val REFUGES_EXPLORE = 0
const val REFUGES_BOOKINGS = 1

/**
 * Scheda Rifugi (M5.1 e M5.2, rifatta con il redesign 11/2026, docs/UI_REDESIGN_PLAN.md §3.1 e §3.4), con due viste:
 * - Esplora: i rifugi entro 50 km, dal più vicino, con la foto. Con il permesso della posizione già dato si parte da
 *   lì; altrimenti dall'area di partenza della mappa, senza chiedere il permesso (lo chiede la mappa, quando serve);
 * - Prenotazioni: il prossimo soggiorno come un biglietto, poi in arrivo e passate. Un tocco su una prenotazione apre
 *   un foglio con le azioni (rifugio, indicazioni, chiamata, annulla).
 * [segment] lo tiene App, così "Vedi le prenotazioni" (dal foglio Prenota o dal Profilo) apre la vista giusta.
 */
@Composable
fun RefugeListScreen(segment: Int, onSegment: (Int) -> Unit, onOpenRefuge: (String) -> Unit) {
    val listViewModel = koinViewModel<RefugeListViewModel>()
    val bookingsViewModel = koinViewModel<MyBookingsViewModel>()
    val list by listViewModel.state.collectAsStateWithLifecycle()
    val covers by listViewModel.covers.collectAsStateWithLifecycle()
    val bookings by bookingsViewModel.state.collectAsStateWithLifecycle()
    val location = rememberLocationState()

    // a ogni ingresso nella scheda: i posti e la posizione possono essere cambiati
    LaunchedEffect(location.permission is LocationPermission.Granted) {
        val fix = if (location.permission is LocationPermission.Granted) {
            withTimeoutOrNull(FixWait) { snapshotFlow { location.lastLocation }.filterNotNull().first() }
        } else {
            null
        }
        if (fix != null) listViewModel.load(fix.position.toGeoPoint(), nearMe = true) else listViewModel.load(DefaultArea, nearMe = false)
    }

    // a ogni apertura delle prenotazioni (anche tornando dal foglio "Prenota" di un rifugio) la lista si rilegge:
    // il ViewModel vive con la scheda e non saprebbe della prenotazione nuova
    LaunchedEffect(segment) { if (segment == REFUGES_BOOKINGS) bookingsViewModel.reload() }

    // la prenotazione di cui è aperto il foglio delle azioni, e quella da annullare (dialogo di conferma)
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }

    // in invio o rifiutate: un pallino sul segmento, perché sono cose da guardare
    val attention = bookings.pending.size + bookings.rejected.size
    val badgeLabel = pluralStringResource(Res.plurals.refuges_tab_bookings_badge, attention, attention)
    val badges = if (attention > 0) mapOf(REFUGES_BOOKINGS to badgeLabel) else emptyMap()

    LargeTitlePage(
        title = stringResource(Res.string.refuges_title),
        below = {
            SegmentedControl(
                options = listOf(stringResource(Res.string.refuges_tab_explore), stringResource(Res.string.refuges_tab_bookings)),
                selected = segment,
                onSelect = onSegment,
                badges = badges,
            )
        },
        refreshing = segment == REFUGES_BOOKINGS && bookings.refreshing,
        onRefresh = if (segment == REFUGES_BOOKINGS) bookingsViewModel::refresh else null,
    ) {
        if (segment == REFUGES_EXPLORE) {
            exploreItems(list, covers, onRetry = listViewModel::retry, onCover = listViewModel::loadCover, onOpenRefuge = onOpenRefuge)
        } else {
            bookingItems(
                state = bookings,
                viewModel = bookingsViewModel,
                onExplore = { onSegment(REFUGES_EXPLORE) },
                onOpen = { actionsFor = it },
            )
        }
    }

    val opened = actionsFor?.let { id -> bookings.items.firstOrNull { it.id == id } }
    if (opened != null) {
        BookingActionsSheet(
            booking = opened,
            refuge = bookings.refuges[opened.refugeId],
            canCancel = bookingsViewModel.canCancel(opened),
            onOpenRefuge = {
                actionsFor = null
                onOpenRefuge(opened.refugeId)
            },
            // un foglio alla volta: il dialogo di conferma arriva dopo che il foglio si è chiuso
            onCancel = {
                actionsFor = null
                confirming = opened.id
            },
            onDismiss = { actionsFor = null },
        )
    }
    confirming?.let { id ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(Res.string.booking_cancel_title)) },
            text = { Text(stringResource(Res.string.booking_cancel_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    bookingsViewModel.cancel(id)
                }) { Text(stringResource(Res.string.booking_cancel), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(Res.string.booking_keep)) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Esplora
// ---------------------------------------------------------------------------------------------------------------------

private fun LazyListScope.exploreItems(
    list: RefugeList,
    covers: Map<String, MediaResponse?>,
    onRetry: () -> Unit,
    onCover: (String) -> Unit,
    onOpenRefuge: (String) -> Unit,
) {
    when (list) {
        RefugeList.Loading -> item(key = "loading") { SkeletonRows(loadingLabel = stringResource(Res.string.refuges_loading)) }
        is RefugeList.Failed -> item(key = "error") { ErrorState(list.message.resolve(), onRetry = onRetry) }
        is RefugeList.Loaded -> if (list.refuges.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(Res.string.refuges_empty_title), stringResource(Res.string.refuges_empty_body)) }
        } else {
            item(key = "where") {
                Text(
                    stringResource(if (list.nearMe) Res.string.refuges_near_me else Res.string.refuges_near_area),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
                )
            }
            // una card per item (lazy: le foto si chiedono solo per le card che si vedono)
            itemsIndexed(list.refuges, key = { _, refuge -> "refuge-" + refuge.id }) { _, refuge ->
                LaunchedEffect(refuge.id) { onCover(refuge.id) }
                RefugeCard(
                    refuge,
                    from = list.from,
                    cover = covers[refuge.id],
                    coverKnown = refuge.id in covers,
                    modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                ) { onOpenRefuge(refuge.id) }
            }
        }
    }
}

// Un rifugio come una cartolina (terza passata): la foto grande, sotto il nome, quota · posti e la distanza in verde;
// tutta la card apre il dettaglio. Niente bordo né card intorno al testo: la foto fa da oggetto, come nelle guide di
// Apple Maps
@Composable
private fun RefugeCard(
    refuge: RefugeResponse,
    from: GeoPoint,
    cover: MediaResponse?,
    coverKnown: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = stringResource(Res.string.refuge_open), role = Role.Button, onClick = onClick),
    ) {
        Cover(cover, loading = !coverKnown)
        Row(Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(refuge.name, style = MaterialTheme.typography.titleMedium)
                MetaLine(
                    listOf(
                        refuge.altitudeM?.let { stringResource(Res.string.refuge_altitude, it) },
                        pluralStringResource(Res.plurals.refuge_beds, refuge.capacity, refuge.capacity),
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                distanceLabel(from.distanceTo(refuge.location.toGeoPoint())),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }
    }
}

// La foto del rifugio a tutta larghezza (16:10, raggio 20) o, senza foto, una tessera muschio con le curve di livello.
// Decorativa: il nome è scritto sotto
@Composable
private fun Cover(cover: MediaResponse?, loading: Boolean) {
    val fill = MaterialTheme.colorScheme.primaryContainer
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(MaterialTheme.shapes.medium)
            .background(fill)
            .clearAndSetSemantics { },
    ) {
        if (cover == null) {
            if (!loading) Box(Modifier.matchParentSize().contours(over = fill, strength = 0.2f, cell = 8.dp))
        } else {
            val media = koinInject<MediaRepository>()
            val signed = rememberSignedUrl(media, cover.id)
            val scope = rememberCoroutineScope()
            signed.url?.let { url ->
                AsyncImage(
                    model = photoRequest(url, cover.id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { scope.launch { signed.onDownloadFailed() } },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Prenotazioni
// ---------------------------------------------------------------------------------------------------------------------

private fun LazyListScope.bookingItems(
    state: MyBookingsUiState,
    viewModel: MyBookingsViewModel,
    onExplore: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val cancelError = state.cancelError
    if (cancelError != null) {
        item(key = "cancel-error") {
            Box(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                FormErrorBanner(cancelError.resolve())
            }
        }
    }
    // in coda e poi rifiutate (o scadute): l'utente le aveva viste "in invio", qui sa che non ci sono
    state.rejected.forEach { rejected ->
        item(key = "rejected-" + rejected.key) {
            RejectedNotice(rejected, state.refugeNames[rejected.payload.refugeId]) { viewModel.dismissRejected(rejected.key) }
        }
    }
    val error = state.error
    when {
        state.items.isEmpty() && state.loading -> item(key = "loading") {
            SkeletonRows(count = 3, thumb = false, loadingLabel = stringResource(Res.string.my_bookings_loading))
        }
        state.items.isEmpty() && error != null -> item(key = "error") { ErrorState(error.resolve(), onRetry = viewModel::retry) }
        state.items.isEmpty() && state.pending.isEmpty() && state.rejected.isEmpty() -> item(key = "empty") {
            EmptyState(
                stringResource(Res.string.my_bookings_empty_title),
                stringResource(Res.string.my_bookings_empty_body),
            ) { TonalPillButton(stringResource(Res.string.my_bookings_explore), onClick = onExplore) }
        }
        else -> {
            val sections = viewModel.sections(state.items)
            val today = viewModel.today()
            sections.next?.let { next ->
                item(key = "next-title") { SectionHeader(stringResource(Res.string.my_bookings_next_title)) }
                item(key = "next-" + next.id) { NextStay(next, state.refuges[next.refugeId], today) { onOpen(next.id) } }
            }
            // in arrivo: quelle in coda (in invio) in cima, poi le confermate dalla più vicina
            val upcomingRows = state.pending.map { BookingRowData.Pending(it) } + sections.upcoming.map { BookingRowData.Server(it) }
            if (upcomingRows.isNotEmpty()) {
                item(key = "upcoming-title") { SectionHeader(stringResource(Res.string.my_bookings_upcoming_title)) }
                bookingRows("upcoming", upcomingRows, state, onOpen)
            }
            if (sections.past.isNotEmpty()) {
                item(key = "past-title") { SectionHeader(stringResource(Res.string.my_bookings_past_title)) }
                bookingRows("past", sections.past.map { BookingRowData.Server(it) }, state, onOpen, muted = true)
            }
            when {
                state.loadingMore -> item(key = "more") { LoadingMore() }
                error != null -> item(key = "more-error") { ErrorState(error.resolve(), onRetry = viewModel::retry, compact = true) }
                // in fondo alla lista: la pagina dopo
                !state.endReached -> item(key = "more-trigger") {
                    LaunchedEffect(state.items.size) { viewModel.loadMore() }
                    Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}

// Una riga della lista: o una prenotazione del server, o una in coda nell'outbox (senza id: non si apre)
private sealed interface BookingRowData {
    data class Server(val booking: BookingResponse) : BookingRowData
    data class Pending(val pending: PendingBooking) : BookingRowData
}

private fun LazyListScope.bookingRows(
    group: String,
    rows: List<BookingRowData>,
    state: MyBookingsUiState,
    onOpen: (String) -> Unit,
    muted: Boolean = false,
) {
    itemsIndexed(rows, key = { _, row ->
        when (row) {
            is BookingRowData.Server -> "$group-" + row.booking.id
            is BookingRowData.Pending -> "$group-pending-" + row.pending.key
        }
    }) { index, row ->
        Box(
            Modifier
                .widthIn(max = PageMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .groupedItem(first = index == 0, last = index == rows.lastIndex),
        ) {
            Column {
                if (index > 0) GroupDivider(84.dp)
                when (row) {
                    is BookingRowData.Server -> BookingRow(
                        refugeName = state.refugeNames[row.booking.refugeId],
                        checkIn = row.booking.checkIn,
                        checkOut = row.booking.checkOut,
                        guests = row.booking.guests,
                        status = row.booking.status.label(),
                        muted = muted,
                        onClick = { onOpen(row.booking.id) },
                    )
                    is BookingRowData.Pending -> BookingRow(
                        refugeName = state.refugeNames[row.pending.payload.refugeId],
                        checkIn = row.pending.payload.checkIn,
                        checkOut = row.pending.payload.checkOut,
                        guests = row.pending.payload.guests,
                        status = stringResource(Res.string.booking_status_pending),
                        pending = true,
                        onClick = null,
                    )
                }
            }
        }
    }
}

// Blocco della data, rifugio, date · notti · persone; lo stato a destra solo quando non è quello atteso ("Confermata" no)
@Composable
private fun BookingRow(
    refugeName: String?,
    checkIn: LocalDate,
    checkOut: LocalDate,
    guests: Int,
    status: String?,
    onClick: (() -> Unit)?,
    muted: Boolean = false,
    pending: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        DateBlock(checkIn, muted = muted)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                refugeName ?: stringResource(Res.string.my_bookings_refuge_unknown),
                style = MaterialTheme.typography.titleMedium,
                color = if (muted) colors.onSurfaceVariant else colors.onSurface,
            )
            Text(
                stayLabel(checkIn, checkOut, null, guests),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            if (pending) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

// Il prossimo soggiorno come biglietto, con "Tra 3 giorni" e le due azioni del posto sotto lo strappo
@Composable
private fun NextStay(booking: BookingResponse, refuge: RefugeResponse?, today: LocalDate, onClick: () -> Unit) {
    val dial = rememberDialer()
    val uriHandler = LocalUriHandler.current
    val days = (booking.checkIn.toEpochDays() - today.toEpochDays()).toInt()
    val note = when {
        days < 0 -> stringResource(Res.string.my_bookings_in_progress)
        days == 0 -> stringResource(Res.string.my_bookings_today)
        days == 1 -> stringResource(Res.string.my_bookings_tomorrow)
        else -> pluralStringResource(Res.plurals.my_bookings_in_days, days, days)
    }
    Box(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
        StayTicket(
            refugeName = refuge?.name ?: stringResource(Res.string.my_bookings_refuge_unknown),
            checkIn = booking.checkIn,
            checkOut = booking.checkOut,
            guests = booking.guests,
            note = note,
            modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
            actions = if (refuge == null) null else {
                {
                    refuge.phone?.let { phone ->
                        TonalPillButton(stringResource(Res.string.refuge_call), onClick = { dial(phone) }, icon = Icons.Filled.Call, modifier = Modifier.weight(1f))
                    }
                    TonalPillButton(
                        stringResource(Res.string.refuge_directions),
                        onClick = { uriHandler.openUri(directionsUri(refuge.location.lat, refuge.location.lon)) },
                        icon = Icons.Filled.Place,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }
}

// Una prenotazione in coda rifiutata dal server (o scaduta): riquadro prugna con il motivo e "Ho capito"
@Composable
private fun RejectedNotice(rejected: RejectedBooking, refugeName: String?, onDismiss: () -> Unit) {
    val payload = rejected.payload
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(stringResource(Res.string.my_bookings_rejected_title), style = MaterialTheme.typography.titleMedium)
            Text(
                (refugeName ?: stringResource(Res.string.my_bookings_refuge_unknown)) + " · " +
                    stayLabel(payload.checkIn, payload.checkOut, null, payload.guests),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                rejected.error?.uiMessage()?.resolve() ?: stringResource(Res.string.booking_rejected_expired),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.booking_rejected_ok), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// Le azioni di una prenotazione in un foglio a metà altezza: il riepilogo, il rifugio, le indicazioni, la chiamata e,
// separato in fondo, l'annullamento (prugna, con conferma)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingActionsSheet(
    booking: BookingResponse,
    refuge: RefugeResponse?,
    canCancel: Boolean,
    onOpenRefuge: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val dial = rememberDialer()
    val uriHandler = LocalUriHandler.current
    val close: (() -> Unit) -> Unit = { then -> scope.launch { sheetState.hide() }.invokeOnCompletion { then() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                StayTicket(
                    refugeName = refuge?.name ?: stringResource(Res.string.my_bookings_refuge_unknown),
                    checkIn = booking.checkIn,
                    checkOut = booking.checkOut,
                    guests = booking.guests,
                    note = booking.status.label(),
                )
                Spacer(Modifier.height(16.dp))
                GroupCard {
                    GroupRow(stringResource(Res.string.refuge_open), icon = Icons.Filled.Home, onClick = { close(onOpenRefuge) })
                    if (refuge != null) {
                        GroupDivider(60.dp)
                        GroupRow(
                            stringResource(Res.string.refuge_directions),
                            icon = Icons.Filled.Place,
                            chevron = false,
                            onClick = { uriHandler.openUri(directionsUri(refuge.location.lat, refuge.location.lon)) },
                        )
                        refuge.phone?.let { phone ->
                            GroupDivider(60.dp)
                            GroupRow(stringResource(Res.string.refuge_call), icon = Icons.Filled.Call, chevron = false, onClick = { dial(phone) })
                        }
                    }
                }
                if (canCancel) {
                    Spacer(Modifier.height(16.dp))
                    GroupCard {
                        // prugna come gli altri errori del tema: annullare non è un'emergenza (il rosso resta all'SOS)
                        GroupRow(
                            stringResource(Res.string.booking_cancel),
                            color = MaterialTheme.colorScheme.error,
                            chevron = false,
                            onClick = { close(onCancel) },
                        )
                    }
                }
            }
        }
    }
}

/** Lo stato da scrivere accanto a una prenotazione: niente per quelle confermate (è il caso normale). */
@Composable
private fun BookingResponse.Status.label(): String? = when (this) {
    BookingResponse.Status.CONFIRMED -> null
    BookingResponse.Status.CANCELLED -> stringResource(Res.string.booking_status_cancelled)
    BookingResponse.Status.unknown_default_open_api -> stringResource(Res.string.booking_status_unknown)
}

/** "sab 10/10 → lun 12/10 · 2 notti · 2 persone". [nights] null: si calcola dalle date. */
@Composable
internal fun stayLabel(checkIn: LocalDate, checkOut: LocalDate, nights: Int?, guests: Int): String {
    val count = nights ?: (checkOut.toEpochDays() - checkIn.toEpochDays()).toInt()
    return "${checkIn.shortLabel()} → ${checkOut.shortLabel()} · " +
        pluralStringResource(Res.plurals.booking_nights_count, count, count) + " · " +
        pluralStringResource(Res.plurals.booking_guests_count, guests, guests)
}
