// Pagina di dettaglio di un rifugio: foto o mappa in testata, dati principali, contatti, disponibilità delle
// prossime notti, descrizione, servizi, sentieri vicini e, per chi lo gestisce, la modifica dei posti letto. In basso
// una barra fissa apre il foglio "Prenota".
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
import it.unical.geosentinel_mobile.core.ui.LocalDirections
import it.unical.geosentinel_mobile.core.ui.rememberDialer
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.skeleton
import it.unical.geosentinel_mobile.core.ui.theme.warningTextColor
import it.unical.geosentinel_mobile.media.data.MediaRepository
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import it.unical.geosentinel_mobile.core.ui.cardShadow
import geosentinel_mobile.shared.generated.resources.booking_less
import geosentinel_mobile.shared.generated.resources.booking_more
import geosentinel_mobile.shared.generated.resources.refuge_guests_value
import geosentinel_mobile.shared.generated.resources.refuge_nights_tap
import geosentinel_mobile.shared.generated.resources.refuge_cell_beds
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.trail.data.ElevationRepository
import androidx.compose.runtime.produceState
import it.unical.geosentinel_mobile.trail.ui.isSourceCredit
import it.unical.geosentinel_mobile.core.ui.Stat
import it.unical.geosentinel_mobile.core.ui.StatTiles
import geosentinel_mobile.shared.generated.resources.stat_altitude
import geosentinel_mobile.shared.generated.resources.stat_beds
import geosentinel_mobile.shared.generated.resources.stat_season

/** Altezza della testata con la foto o la mappa. */
private val HeroHeight = 320.dp
/** Quanto il pannello del contenuto sale sopra la foto; è anche il raggio dei suoi angoli alti arrotondati. */
private val PanelLip = 28.dp
/** Zoom della mappa in testata, che mostra il rifugio con paesi e strade attorno. */
private const val HeroZoom = 12.5
/** Scala del testo oltre la quale i servizi si dispongono in una sola colonna. */
private const val LargeTextScale = 1.3f

/**
 * Pagina del rifugio [refugeId]. In alto le foto (o la mappa, se non ce ne sono) da bordo a bordo; sotto, un pannello
 * con il nome, le tessere di quota, posti e stagione, i pulsanti "Chiama" e "Indicazioni", subito la disponibilità
 * delle prossime notti (ogni giorno apre "Prenota" da quella data), poi descrizione, servizi, sentieri vicini e, per
 * chi gestisce il rifugio, la sezione di gestione. In basso, fissa, c'è la barra con il numero di persone e
 * "Prenota", che apre [BookingSheet].
 *
 * [canManage] decide se mostrare la modifica dei posti letto (al gestore di quel rifugio o a un amministratore): è
 * solo una scelta di interfaccia, perché agli altri il server risponde comunque 403.
 */
@Composable
fun RefugeDetailScreen(
    refugeId: String,
    onBack: () -> Unit,
    onOpenTrail: (String) -> Unit,
    onOpenBookings: () -> Unit,
    canManage: (RefugeResponse) -> Boolean = { false },
    onEditCapacity: (RefugeResponse) -> Unit = {},
) {
    val viewModel = koinViewModel<RefugeDetailViewModel> { parametersOf(refugeId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A ogni ritorno sulla pagina (per esempio dal form dei posti letto) posti e disponibilità si rileggono.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val loaded = (state.refuge as? Section.Loaded)?.value
    // Stato del foglio "Prenota": null se è chiuso, stringa vuota se è aperto senza un giorno, altrimenti la data
    // d'arrivo in formato ISO. È una stringa perché così si salva con rememberSaveable.
    var sheetArrival by rememberSaveable { mutableStateOf<String?>(null) }
    // Persone scelte nella barra in basso: il foglio "Prenota" si apre con questo numero.
    var guests by rememberSaveable { mutableIntStateOf(1) }

    val listState = rememberLazyListState()
    // Quando la testata (primo elemento della lista) è scorsa via, in alto compare una barra con il nome, come
    // nelle pagine di iOS.
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            // Spazio in fondo per non finire sotto la barra fissa.
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
                    item(key = "nights-title") {
                        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth()) {
                            SectionHeader(stringResource(Res.string.refuge_nights_title), Modifier.padding(bottom = 0.dp))
                            Text(
                                stringResource(Res.string.refuge_nights_tap),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 12.dp),
                            )
                        }
                    }
                    item(key = "nights") {
                        when (val nights = state.nights) {
                            Section.Loading, Section.Hidden -> LoadingMore()
                            is Section.Failed -> ErrorState(nights.message.resolve(), onRetry = viewModel::retry, compact = true)
                            is Section.Loaded -> NightStrip(nights.value) { date -> sheetArrival = date.toString() }
                        }
                    }
                    // Le righe della descrizione che citano le fonti (OpenStreetMap, copyright) si tolgono: le
                    // attribuzioni stanno nella pagina dei crediti.
                    value.description?.lines()?.filterNot { isSourceCredit(it.trim()) }?.joinToString("\n")?.trim()?.takeIf { it.isNotBlank() }?.let { description ->
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
                    // La gestione sta in fondo, fuori dal percorso di lettura dell'escursionista.
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
        // L'azione principale sta in basso, nella zona raggiungibile con il pollice.
        if (loaded != null) {
            StickyActionBar(Modifier.align(Alignment.BottomCenter)) {
                BookBar(guests = guests, maxGuests = minOf(loaded.capacity, MaxGuestsPerBooking), onGuests = { guests = it }) { sheetArrival = "" }
            }
        }
    }

    if (loaded != null) {
        sheetArrival?.let { arrival ->
            BookingHost(
                refuge = loaded,
                arrival = arrival,
                guests = guests,
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

/**
 * Ospita il foglio "Prenota". Il suo ViewModel è legato a questa pagina (alla sua voce nella pila di navigazione) e
 * non al foglio: così chiudere il foglio non annulla un invio in corso. A ogni nuova apertura il foglio riparte da
 * [arrival] (vuota: nessun giorno scelto) e da [guests] persone.
 */
@Composable
private fun BookingHost(refuge: RefugeResponse, arrival: String, guests: Int, onClose: () -> Unit, onBooked: () -> Unit, onOpenBookings: () -> Unit) {
    val viewModel = koinViewModel<BookRefugeViewModel>(key = "book-${refuge.id}") { parametersOf(refuge.id, refuge.capacity) }
    LaunchedEffect(arrival) {
        viewModel.open(arrival.takeIf { it.isNotEmpty() }?.let(LocalDate::parse))
        viewModel.onGuestsChange(guests)
    }
    BookingSheet(refuge.name, viewModel, onDismiss = onClose, onBooked = onBooked, onOpenBookings = onOpenBookings)
}

// Testata.

/** Testata della pagina: la galleria delle foto, o la mappa se non ce ne sono, con il bordo arrotondato del pannello. */
@Composable
private fun Hero(refuge: RefugeResponse, photos: List<MediaResponse>) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        if (photos.isEmpty()) MapHero(refuge) else Gallery(photos)
        // Inizio del pannello che sale sulla foto: una fascia con gli angoli alti arrotondati nel colore della pagina.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(PanelLip)
                .background(background, RoundedCornerShape(topStart = PanelLip, topEnd = PanelLip)),
        )
    }
}

/**
 * Galleria delle foto del rifugio, una per pagina con lo scorrimento orizzontale. Con più foto mostra dei puntini
 * (fino a 5) o un contatore. Un tocco apre la foto a schermo intero. Gli URL firmati si chiedono per ogni foto e si
 * rinnovano se il download fallisce.
 */
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

/**
 * Testata senza foto: la mappa ferma sul rifugio con il suo segnaposto, senza interazioni. È nascosta allo screen
 * reader perché nome e posti sono scritti subito sotto.
 */
@Composable
private fun MapHero(refuge: RefugeResponse) {
    val style = rememberMapStyle(rememberSavedMapType())
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = refuge.location.toGeoPoint().toPosition(), zoom = HeroZoom),
    ) {
        // Etichetta trasparente: il nome è già scritto nel pannello sotto.
        RefugeMarkers(listOf(refuge), label = Color.Transparent, halo = Color.Transparent, onRefugeClick = {})
    }
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        MaplibreMap(
            state = mapState,
            interactions = MapInteractions.None,
            overlay = {
                // Attribuzione della mappa in basso a destra, sopra il bordo del pannello: in alto coprirebbe il
                // pulsante Indietro, al centro il rifugio.
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

/** Nome del rifugio in grande e, sotto, le tessere con i dati disponibili: quota, posti e stagione. */
@Composable
private fun Title(refuge: RefugeResponse) {
    // Si usa la quota del rifugio; se manca (succede per i rifugi importati) si ricava dai dati di elevazione nel
    // punto del rifugio.
    val elevation = koinInject<ElevationRepository>()
    val altitude by produceState(refuge.altitudeM, refuge.id) {
        if (value == null) value = elevation.metersAt(refuge.location.toGeoPoint())
    }
    Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(refuge.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        StatTiles(
            listOfNotNull(
                altitude?.let { Stat(stringResource(Res.string.stat_altitude), formatThousands(it), "m") },
                Stat(stringResource(Res.string.stat_beds), refuge.capacity.toString()),
                refuge.season?.takeIf { it.isNotBlank() }?.let { Stat(stringResource(Res.string.stat_season), it) },
            ),
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** Separa le migliaia con uno spazio, come sulle carte topografiche (per esempio 1 760). */
private fun formatThousands(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(" ").reversed()

/**
 * Pulsanti "Chiama" (solo se il rifugio ha un numero) e "Indicazioni", affiancati a metà larghezza. Le indicazioni
 * usano il percorso calcolato dall'app ([LocalDirections]). Senza numero, "Indicazioni" resta comunque largo metà.
 */
@Composable
private fun Contacts(refuge: RefugeResponse) {
    val dial = rememberDialer()
    val directions = LocalDirections.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp),
    ) {
        refuge.phone?.let { phone ->
            TonalPillButton(stringResource(Res.string.refuge_call), onClick = { dial(phone) }, icon = AppIcons.Phone, modifier = Modifier.weight(1f))
        }
        TonalPillButton(
            stringResource(Res.string.refuge_directions),
            onClick = { directions(refuge.name, refuge.location.lat, refuge.location.lon) },
            icon = AppIcons.MapPin,
            modifier = Modifier.weight(1f),
        )
        if (refuge.phone == null) Spacer(Modifier.weight(1f))
    }
}

// Disponibilità.

/** Striscia orizzontale delle prossime notti: toccare un giorno con posti apre "Prenota" con quell'arrivo già scelto. */
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

/**
 * Cella di una notte: giorno della settimana, numero del giorno e posti liberi. Stanotte ha un fondo pieno nel
 * colore terziario. Con pochi posti il numero è in ambra con la parola "ultimi"; una notte piena è spenta e non si
 * può toccare, ma non diventa mai rossa, perché il rosso è riservato all'emergenza.
 */
@Composable
private fun NightCell(night: NightAvailability, tonight: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container = if (tonight) colors.tertiary else colors.surfaceContainerLowest
    val content = if (tonight) colors.onTertiary else colors.onSurface
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
            (if (tonight) stringResource(Res.string.refuge_tonight) else stringResource(night.date.weekday())).uppercase(),
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
                pluralStringResource(Res.plurals.refuge_cell_beds, night.freeBeds, night.freeBeds),
                style = MaterialTheme.typography.labelMedium,
                color = if (tonight) content else colors.primary,
            )
        }
    }
}

/**
 * Barra fissa in basso: a sinistra il contatore delle persone, a destra "Prenota", che apre il foglio con quel numero
 * di persone già scelto. I posti di ogni notte sono nella striscia più in alto.
 */
@Composable
private fun BookBar(guests: Int, maxGuests: Int, onGuests: (Int) -> Unit, onBook: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val guestsLabel = stringResource(Res.string.refuge_guests_value, guests)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = colors.surfaceContainerLowest, modifier = Modifier.cardShadow(CircleShape)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = false) { }) {
                IconButton(onClick = { onGuests(guests - 1) }, enabled = guests > 1, modifier = Modifier.size(56.dp)) {
                    Icon(AppIcons.Minus, contentDescription = stringResource(Res.string.booking_less))
                }
                Text(
                    guests.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.widthIn(min = 24.dp).clearAndSetSemantics { contentDescription = guestsLabel; liveRegion = LiveRegionMode.Polite },
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { onGuests(guests + 1) }, enabled = guests < maxGuests, modifier = Modifier.size(56.dp)) {
                    Icon(AppIcons.Plus, contentDescription = stringResource(Res.string.booking_more))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        PrimaryPillButton(stringResource(Res.string.booking_open), onClick = onBook, modifier = Modifier.weight(1f))
    }
}

/** Persone al massimo per prenotazione, secondo il contratto della richiesta. */
private const val MaxGuestsPerBooking = 20

// Descrizione e servizi.

/** Descrizione limitata a quattro righe; se il testo è più lungo compare "Leggi tutto", che lo apre sul posto. */
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

/** Servizi del rifugio in una card, su due colonne (una sola con il testo grande), ognuno con una spunta. */
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
                            // Icona decorativa: il nome del servizio è scritto accanto.
                            Icon(AppIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
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

/** Etichetta di un servizio. Un valore che l'app non conosce ancora restituisce null e non si mostra. */
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

/** Sagoma della pagina mostrata durante il caricamento: testata, titolo, una riga, due pulsanti e la striscia dei giorni. */
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

/** Giorno della settimana abbreviato seguito da giorno e mese in numeri, usato nel riepilogo e nelle prenotazioni. */
@Composable
internal fun LocalDate.shortLabel(): String = "${stringResource(weekday())} $day/${month.number}"

/** Nome abbreviato del giorno della settimana, dalle risorse di testo. */
internal fun LocalDate.weekday(): StringResource = when (dayOfWeek.isoDayNumber) {
    1 -> Res.string.weekday_1
    2 -> Res.string.weekday_2
    3 -> Res.string.weekday_3
    4 -> Res.string.weekday_4
    5 -> Res.string.weekday_5
    6 -> Res.string.weekday_6
    else -> Res.string.weekday_7
}
