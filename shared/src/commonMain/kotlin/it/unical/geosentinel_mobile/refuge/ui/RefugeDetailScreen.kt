package it.unical.geosentinel_mobile.refuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.booking_open
import geosentinel_mobile.shared.generated.resources.refuge_about_title
import geosentinel_mobile.shared.generated.resources.refuge_altitude
import geosentinel_mobile.shared.generated.resources.refuge_amenities_title
import geosentinel_mobile.shared.generated.resources.refuge_amenity_dogs_allowed
import geosentinel_mobile.shared.generated.resources.refuge_amenity_drinking_water
import geosentinel_mobile.shared.generated.resources.refuge_amenity_electricity
import geosentinel_mobile.shared.generated.resources.refuge_amenity_heating
import geosentinel_mobile.shared.generated.resources.refuge_amenity_kitchen
import geosentinel_mobile.shared.generated.resources.refuge_amenity_restaurant
import geosentinel_mobile.shared.generated.resources.refuge_amenity_sheets_provided
import geosentinel_mobile.shared.generated.resources.refuge_amenity_shower
import geosentinel_mobile.shared.generated.resources.refuge_beds
import geosentinel_mobile.shared.generated.resources.refuge_call
import geosentinel_mobile.shared.generated.resources.refuge_day_book
import geosentinel_mobile.shared.generated.resources.refuge_directions
import geosentinel_mobile.shared.generated.resources.refuge_few_beds
import geosentinel_mobile.shared.generated.resources.refuge_free_beds
import geosentinel_mobile.shared.generated.resources.refuge_full
import geosentinel_mobile.shared.generated.resources.refuge_full_tonight
import geosentinel_mobile.shared.generated.resources.refuge_manage_title
import geosentinel_mobile.shared.generated.resources.refuge_next_free
import geosentinel_mobile.shared.generated.resources.refuge_nights_title
import geosentinel_mobile.shared.generated.resources.refuge_photo_count
import geosentinel_mobile.shared.generated.resources.refuge_read_less
import geosentinel_mobile.shared.generated.resources.refuge_read_more
import geosentinel_mobile.shared.generated.resources.refuge_tonight
import geosentinel_mobile.shared.generated.resources.refuge_tonight_lower
import geosentinel_mobile.shared.generated.resources.refuge_trails_title
import geosentinel_mobile.shared.generated.resources.staff_capacity_edit
import geosentinel_mobile.shared.generated.resources.weekday_1
import geosentinel_mobile.shared.generated.resources.weekday_2
import geosentinel_mobile.shared.generated.resources.weekday_3
import geosentinel_mobile.shared.generated.resources.weekday_4
import geosentinel_mobile.shared.generated.resources.weekday_5
import geosentinel_mobile.shared.generated.resources.weekday_6
import geosentinel_mobile.shared.generated.resources.weekday_7
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.CompactBar
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import it.unical.geosentinel_mobile.core.ui.ErrorState
import it.unical.geosentinel_mobile.core.ui.GroupCard
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.GroupRow
import it.unical.geosentinel_mobile.core.ui.LoadingMore
import it.unical.geosentinel_mobile.core.ui.MetaLine
import it.unical.geosentinel_mobile.core.ui.PageMaxWidth
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.SectionHeader
import it.unical.geosentinel_mobile.core.ui.StickyActionBar
import it.unical.geosentinel_mobile.core.ui.TonalPillButton
import it.unical.geosentinel_mobile.core.ui.directionsUri
import it.unical.geosentinel_mobile.core.ui.rememberDialer
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.skeleton
import it.unical.geosentinel_mobile.core.ui.theme.warningTextColor
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.media.ui.PhotoCredit
import it.unical.geosentinel_mobile.media.ui.PhotoViewer
import it.unical.geosentinel_mobile.media.ui.photoRequest
import it.unical.geosentinel_mobile.media.ui.rememberSignedUrl
import it.unical.geosentinel_mobile.refuge.data.NightAvailability
import it.unical.geosentinel_mobile.trail.ui.AttributionButton
import it.unical.geosentinel_mobile.trail.ui.Section
import it.unical.geosentinel_mobile.trail.ui.TrailRow
import it.unical.geosentinel_mobile.trail.ui.rememberMapStyle
import it.unical.geosentinel_mobile.trail.ui.rememberSavedMapType
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState

private val HeroHeight = 320.dp
// Il pannello del contenuto sale sulla foto con gli angoli alti arrotondati
private val PanelLip = 28.dp
// Zoom della mappa in testata: il rifugio con paesi e strade intorno
private const val HeroZoom = 12.5
// Oltre questa scala del testo i servizi vanno in una colonna sola
private const val LargeTextScale = 1.3f

/**
 * Pagina del rifugio (M5.1, rifatta con il redesign 11/2026, docs/UI_REDESIGN_PLAN.md §3.2): foto (o mappa) da bordo a
 * bordo, poi un pannello con nome, quota · posti · stagione, Chiama e Indicazioni, subito la disponibilità delle
 * prossime notti (ogni giorno apre "Prenota" da quella data), la descrizione, i servizi, i sentieri vicini e, per chi
 * gestisce il rifugio, la gestione. In basso, fissa, com'è stanotte e "Prenota", che apre il foglio [BookingSheet].
 */
@Composable
fun RefugeDetailScreen(
    refugeId: String,
    onBack: () -> Unit,
    onOpenTrail: (String) -> Unit,
    onOpenBookings: () -> Unit,
    // posti letto (M8.3): il gestore di questo rifugio o un ADMIN (il server risponde comunque 403 agli altri)
    canManage: (RefugeResponse) -> Boolean = { false },
    onEditCapacity: (RefugeResponse) -> Unit = {},
) {
    val viewModel = koinViewModel<RefugeDetailViewModel> { parametersOf(refugeId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // tornando dal form dei posti (M8.3) posti e disponibilità si rileggono
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val loaded = (state.refuge as? Section.Loaded)?.value
    // il foglio "Prenota": null chiuso, "" aperto senza un giorno, altrimenti la data d'arrivo (ISO)
    var sheetArrival by rememberSaveable { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    // la foto (primo item) è scorsa via: in alto compare la barra con il nome, come nelle pagine di iOS
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            // spazio per la barra fissa in basso
            contentPadding = PaddingValues(bottom = if (loaded != null) 112.dp else 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val refuge = state.refuge) {
                Section.Loading, Section.Hidden -> item(key = "skeleton") { DetailSkeleton() }
                is Section.Failed -> item(key = "error") {
                    Spacer(Modifier.height(HeroHeight / 2))
                    ErrorState(refuge.message.resolve(), onRetry = viewModel::retry)
                }
                is Section.Loaded -> {
                    val value = refuge.value
                    item(key = "hero") { Hero(value, state.photos) }
                    item(key = "title") { Title(value) }
                    item(key = "contacts") { Contacts(value) }
                    item(key = "nights-title") { SectionHeader(stringResource(Res.string.refuge_nights_title)) }
                    item(key = "nights") {
                        when (val nights = state.nights) {
                            Section.Loading, Section.Hidden -> LoadingMore()
                            is Section.Failed -> ErrorState(nights.message.resolve(), onRetry = viewModel::retry, compact = true)
                            is Section.Loaded -> NightStrip(nights.value) { date -> sheetArrival = date.toString() }
                        }
                    }
                    value.description?.takeIf { it.isNotBlank() }?.let { description ->
                        item(key = "about-title") { SectionHeader(stringResource(Res.string.refuge_about_title)) }
                        item(key = "about") { Description(description) }
                    }
                    val amenities = value.amenities.mapNotNull { it.label() }
                    if (amenities.isNotEmpty()) {
                        item(key = "amenities-title") { SectionHeader(stringResource(Res.string.refuge_amenities_title)) }
                        item(key = "amenities") { Amenities(amenities) }
                    }
                    if (state.nearbyTrails.isNotEmpty()) {
                        item(key = "trails-title") { SectionHeader(stringResource(Res.string.refuge_trails_title)) }
                        item(key = "trails") {
                            GroupCard(Modifier.widthIn(max = PageMaxWidth).padding(horizontal = 20.dp)) {
                                state.nearbyTrails.forEachIndexed { index, trail ->
                                    if (index > 0) GroupDivider()
                                    TrailRow(trail, onClick = { onOpenTrail(trail.id) })
                                }
                            }
                        }
                    }
                    // la gestione in fondo: è fuori dal percorso dell'escursionista
                    if (canManage(value)) {
                        item(key = "manage-title") { SectionHeader(stringResource(Res.string.refuge_manage_title)) }
                        item(key = "manage") {
                            GroupCard(Modifier.widthIn(max = PageMaxWidth).padding(horizontal = 20.dp)) {
                                GroupRow(
                                    title = stringResource(Res.string.staff_capacity_edit),
                                    value = pluralStringResource(Res.plurals.refuge_beds, value.capacity, value.capacity),
                                    onClick = { onEditCapacity(value) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (collapsed && loaded != null) CompactBar(loaded.name, collapsed = true, onBack = onBack, actions = null) else BackButton(onBack)
        // l'azione principale in fondo, nella zona del pollice, con com'è stanotte accanto
        if (loaded != null) {
            StickyActionBar(Modifier.align(Alignment.BottomCenter)) {
                BookBar(state.nights) { sheetArrival = "" }
            }
        }
    }

    if (loaded != null) {
        sheetArrival?.let { arrival ->
            BookingHost(
                refuge = loaded,
                arrival = arrival,
                onClose = { sheetArrival = null },
                onBooked = {
                    sheetArrival = null
                    viewModel.refresh()
                },
                onOpenBookings = {
                    sheetArrival = null
                    onOpenBookings()
                },
            )
        }
    }
}

// Il ViewModel del foglio vive con questa pagina (la voce della pila), non con il foglio: chiuderlo non annulla un invio
@Composable
private fun BookingHost(refuge: RefugeResponse, arrival: String, onClose: () -> Unit, onBooked: () -> Unit, onOpenBookings: () -> Unit) {
    val viewModel = koinViewModel<BookRefugeViewModel>(key = "book-${refuge.id}") { parametersOf(refuge.id, refuge.capacity) }
    LaunchedEffect(arrival) { viewModel.open(arrival.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)) }
    BookingSheet(refuge.name, viewModel, onDismiss = onClose, onBooked = onBooked, onOpenBookings = onOpenBookings)
}

// ---- Testata ----

@Composable
private fun Hero(refuge: RefugeResponse, photos: List<MediaResponse>) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        if (photos.isEmpty()) MapHero(refuge) else Gallery(photos)
        // il pannello che sale sulla foto: gli angoli alti, nel colore della pagina
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(PanelLip)
                .background(background, RoundedCornerShape(topStart = PanelLip, topEnd = PanelLip)),
        )
    }
}

// Le foto del rifugio, una alla volta scorrendo; puntini (fino a 5) o "2/7"; un tocco la apre intera
@Composable
private fun Gallery(photos: List<MediaResponse>) {
    val colors = MaterialTheme.colorScheme
    val media = koinInject<MediaRepository>()
    val pager = rememberPagerState { photos.size }
    var opened by remember { mutableStateOf<MediaResponse?>(null) }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val photo = photos[page]
            val signed = rememberSignedUrl(media, photo.id)
            val scope = rememberCoroutineScope()
            val url = signed.url
            Box(
                Modifier.fillMaxSize().background(colors.surfaceContainerHigh).clickable(role = Role.Button) { opened = photo },
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) {
                    AsyncImage(
                        model = photoRequest(url, photo.id),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { scope.launch { signed.onDownloadFailed() } },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (!signed.failed) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        }
        // autore e licenza della foto mostrata (solo per le foto importate), sopra il bordo del pannello
        PhotoCredit(photos[pager.currentPage].credit, Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = PanelLip + 4.dp))
        if (photos.size > 1) {
            val pill = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = PanelLip + 8.dp)
                .clip(CircleShape)
                .background(colors.inverseSurface.copy(alpha = 0.6f))
            if (photos.size <= 5) {
                Row(pill.padding(horizontal = 10.dp, vertical = 8.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(photos.size) { index ->
                        val current = index == pager.currentPage
                        Box(Modifier.size(6.dp).background(colors.inverseOnSurface.copy(alpha = if (current) 1f else 0.5f), CircleShape))
                    }
                }
            } else {
                Text(
                    stringResource(Res.string.refuge_photo_count, pager.currentPage + 1, photos.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inverseOnSurface,
                    modifier = pill.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
    opened?.let { PhotoViewer(it, media, onClose = { opened = null }) }
}

// Senza foto: la mappa ferma sul rifugio con il suo segnaposto. Decorativa per gli screen reader: nome e posti sono sotto
@Composable
private fun MapHero(refuge: RefugeResponse) {
    val style = rememberMapStyle(rememberSavedMapType())
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = refuge.location.toGeoPoint().toPosition(), zoom = HeroZoom),
    ) {
        // senza nome sulla mappa: è scritto subito sotto, nel pannello
        RefugeMarkers(listOf(refuge), label = Color.Transparent, halo = Color.Transparent, onRefugeClick = {})
    }
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        MaplibreMap(
            state = mapState,
            interactions = MapInteractions.None,
            overlay = {
                // sopra il bordo del pannello, in basso a destra: in alto coprirebbe Indietro, al centro il rifugio
                AttributionButton(
                    mapState,
                    Modifier.align(Alignment.BottomEnd).padding(bottom = PanelLip + 4.dp, end = 8.dp),
                    alignEnd = true,
                    startExpanded = false,
                )
            },
        )
    }
}

// Nome e, in una riga, quota · posti · stagione
@Composable
private fun Title(refuge: RefugeResponse) {
    Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(refuge.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        MetaLine(
            listOf(
                refuge.altitudeM?.let { stringResource(Res.string.refuge_altitude, it) },
                pluralStringResource(Res.plurals.refuge_beds, refuge.capacity, refuge.capacity),
                refuge.season,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// Chiamata (se c'è il numero) e indicazioni con le mappe del telefono, metà e metà; senza numero Indicazioni resta a metà
@Composable
private fun Contacts(refuge: RefugeResponse) {
    val dial = rememberDialer()
    val uriHandler = LocalUriHandler.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp),
    ) {
        refuge.phone?.let { phone ->
            TonalPillButton(stringResource(Res.string.refuge_call), onClick = { dial(phone) }, icon = Icons.Filled.Call, modifier = Modifier.weight(1f))
        }
        TonalPillButton(
            stringResource(Res.string.refuge_directions),
            onClick = { uriHandler.openUri(directionsUri(refuge.location.lat, refuge.location.lon)) },
            icon = Icons.Filled.Place,
            modifier = Modifier.weight(1f),
        )
        if (refuge.phone == null) Spacer(Modifier.weight(1f))
    }
}

// ---- Disponibilità ----

// Le prossime notti come una striscia di giorni: ogni giorno con posti apre "Prenota" con quell'arrivo già scelto
@Composable
private fun NightStrip(nights: List<NightAvailability>, onPick: (LocalDate) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(nights, key = { _, night -> night.date.toString() }) { index, night ->
            NightCell(night, tonight = index == 0, onClick = { onPick(night.date) })
        }
    }
}

// Una notte: giorno della settimana, numero del giorno (Barlow) e i posti liberi. Stanotte ha il fondo muschio.
// Pochi posti in ambra con "ultimi"; "Pieno" spento e non toccabile (mai rosso: il rosso resta all'emergenza)
@Composable
private fun NightCell(night: NightAvailability, tonight: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container = if (tonight) colors.primaryContainer else colors.surfaceContainerLowest
    val content = if (tonight) colors.onPrimaryContainer else colors.onSurface
    val few = !night.isFull && night.freeBeds <= FEW_BEDS
    val dayLabel = if (tonight) stringResource(Res.string.refuge_tonight) else night.date.longLabel()
    val bedsLabel = if (night.isFull) {
        stringResource(Res.string.refuge_full)
    } else {
        pluralStringResource(Res.plurals.refuge_free_beds, night.freeBeds, night.freeBeds)
    }
    val action = stringResource(Res.string.refuge_day_book)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .clickable(enabled = !night.isFull, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = if (night.isFull) "$dayLabel, $bedsLabel" else "$dayLabel, $bedsLabel, $action"
                role = Role.Button
                if (night.isFull) disabled()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            if (tonight) stringResource(Res.string.refuge_tonight) else stringResource(night.date.weekday()),
            style = MaterialTheme.typography.labelSmall,
            color = if (tonight) content else colors.onSurfaceVariant,
        )
        Text(
            night.date.day.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = if (night.isFull) colors.onSurfaceVariant else content,
        )
        Spacer(Modifier.height(4.dp))
        when {
            night.isFull -> Text("—", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            few -> Text(
                "${night.freeBeds} ${stringResource(Res.string.refuge_few_beds)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (tonight) content else warningTextColor(),
            )
            else -> Text(
                night.freeBeds.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (tonight) content else colors.primary,
            )
        }
    }
}

// La barra in basso: a sinistra com'è stanotte (il valore grande, l'etichetta piccola), a destra "Prenota".
// Senza calendario (in caricamento o in errore) solo il pulsante, a tutta larghezza.
@Composable
private fun BookBar(nights: Section<List<NightAvailability>>, onBook: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val list = (nights as? Section.Loaded)?.value
    val tonight = list?.firstOrNull()
    if (list == null || tonight == null) {
        PrimaryPillButton(stringResource(Res.string.booking_open), onClick = onBook)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp).semantics(mergeDescendants = true) { }) {
            if (tonight.isFull) {
                Text(stringResource(Res.string.refuge_full_tonight), style = MaterialTheme.typography.titleMedium)
                list.firstOrNull { !it.isFull }?.let { next ->
                    Text(
                        stringResource(Res.string.refuge_next_free, next.date.shortLabel()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    pluralStringResource(Res.plurals.refuge_free_beds, tonight.freeBeds, tonight.freeBeds),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(Res.string.refuge_tonight_lower), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        PrimaryPillButton(stringResource(Res.string.booking_open), onClick = onBook, fill = false, modifier = Modifier.widthIn(min = 132.dp))
    }
}

// ---- Descrizione e servizi ----

// Quattro righe e "Leggi tutto", che apre il testo sul posto
@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(if (expanded) Res.string.refuge_read_less else Res.string.refuge_read_more), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// I servizi in una card, due colonne (una con il testo grande), spunta verde e testo
@Composable
private fun Amenities(labels: List<StringResource>) {
    val colors = MaterialTheme.colorScheme
    val columns = if (LocalDensity.current.fontScale > LargeTextScale) 1 else 2
    GroupCard(Modifier.widthIn(max = PageMaxWidth).padding(horizontal = 20.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            labels.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { label ->
                        Row(Modifier.weight(1f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            // decorativa: il servizio è scritto accanto
                            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// L'etichetta di un servizio; un valore nuovo del server (unknown) non si mostra
private fun RefugeResponse.Amenities.label(): StringResource? = when (this) {
    RefugeResponse.Amenities.DRINKING_WATER -> Res.string.refuge_amenity_drinking_water
    RefugeResponse.Amenities.RESTAURANT -> Res.string.refuge_amenity_restaurant
    RefugeResponse.Amenities.KITCHEN -> Res.string.refuge_amenity_kitchen
    RefugeResponse.Amenities.HEATING -> Res.string.refuge_amenity_heating
    RefugeResponse.Amenities.SHOWER -> Res.string.refuge_amenity_shower
    RefugeResponse.Amenities.ELECTRICITY -> Res.string.refuge_amenity_electricity
    RefugeResponse.Amenities.DOGS_ALLOWED -> Res.string.refuge_amenity_dogs_allowed
    RefugeResponse.Amenities.SHEETS_PROVIDED -> Res.string.refuge_amenity_sheets_provided
    RefugeResponse.Amenities.unknown_default_open_api -> null
}

// Sagoma della pagina mentre arriva il rifugio: foto, titolo, due righe, la striscia dei giorni
@Composable
private fun DetailSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(HeroHeight).skeleton(RectangleShape))
        Column(Modifier.padding(20.dp)) {
            Box(Modifier.fillMaxWidth(0.65f).height(28.dp).skeleton())
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(0.45f).height(16.dp).skeleton())
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(48.dp).skeleton(CircleShape))
                Box(Modifier.weight(1f).height(48.dp).skeleton(CircleShape))
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { Box(Modifier.size(64.dp, 88.dp).skeleton(MaterialTheme.shapes.small)) }
            }
        }
    }
}

/** "sab 10/10": giorno della settimana e data, nel calendario, nel riepilogo e nelle prenotazioni. */
@Composable
internal fun LocalDate.shortLabel(): String = "${stringResource(weekday())} $day/${month.number}"

internal fun LocalDate.weekday(): StringResource = when (dayOfWeek.isoDayNumber) {
    1 -> Res.string.weekday_1
    2 -> Res.string.weekday_2
    3 -> Res.string.weekday_3
    4 -> Res.string.weekday_4
    5 -> Res.string.weekday_5
    6 -> Res.string.weekday_6
    else -> Res.string.weekday_7
}
