package it.unical.geosentinel_mobile.refuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.booking_open
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
import geosentinel_mobile.shared.generated.resources.refuge_directions
import geosentinel_mobile.shared.generated.resources.refuge_free_beds
import geosentinel_mobile.shared.generated.resources.refuge_free_short
import geosentinel_mobile.shared.generated.resources.refuge_full
import geosentinel_mobile.shared.generated.resources.refuge_nights_hint
import geosentinel_mobile.shared.generated.resources.refuge_nights_title
import geosentinel_mobile.shared.generated.resources.refuge_photo_count
import geosentinel_mobile.shared.generated.resources.refuge_tonight
import geosentinel_mobile.shared.generated.resources.refuge_tonight_free
import geosentinel_mobile.shared.generated.resources.refuge_tonight_full
import geosentinel_mobile.shared.generated.resources.refuge_trails_title
import geosentinel_mobile.shared.generated.resources.trail_retry
import geosentinel_mobile.shared.generated.resources.weekday_1
import geosentinel_mobile.shared.generated.resources.weekday_2
import geosentinel_mobile.shared.generated.resources.weekday_3
import geosentinel_mobile.shared.generated.resources.weekday_4
import geosentinel_mobile.shared.generated.resources.weekday_5
import geosentinel_mobile.shared.generated.resources.weekday_6
import geosentinel_mobile.shared.generated.resources.weekday_7
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.directionsUri
import it.unical.geosentinel_mobile.core.ui.rememberDialer
import it.unical.geosentinel_mobile.core.ui.resolve
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

private val ContentMaxWidth = 600.dp
private val HeroHeight = 300.dp
// Zoom della mappa in testata: il rifugio con paesi e strade intorno
private const val HeroZoom = 12.5

/**
 * Dettaglio di un rifugio (M5.1, rinnovato il 10/10): in testata le foto del rifugio (la mappa se non ce ne sono), poi
 * nome con quota e posti, la stagione, la descrizione, la disponibilità delle prossime 14 notti come striscia di giorni,
 * i servizi, i sentieri che passano vicino e i contatti (chiamata e indicazioni). "Prenota" (M5.2) sta in fondo, fisso.
 */
@Composable
fun RefugeDetailScreen(
    refugeId: String,
    onBack: () -> Unit,
    onBook: (refugeId: String, name: String, capacity: Int) -> Unit,
    onOpenTrail: (String) -> Unit,
) {
    val viewModel = koinViewModel<RefugeDetailViewModel> { parametersOf(refugeId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val loaded = state.refuge as? Section.Loaded

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            // spazio per il pulsante "Prenota" fisso in basso
            contentPadding = PaddingValues(bottom = bottomInset + if (loaded != null) 104.dp else 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val refuge = state.refuge) {
                Section.Loading, Section.Hidden -> {
                    item(key = "header") { ForestHeader(Modifier.fillMaxWidth().height(200.dp)) }
                    item(key = "loading") { CenteredProgress() }
                }
                is Section.Failed -> {
                    item(key = "header") { ForestHeader(Modifier.fillMaxWidth().height(200.dp)) }
                    item(key = "error") { RetryMessage(refuge.message, onRetry = viewModel::retry) }
                }
                is Section.Loaded -> {
                    val value = refuge.value
                    item(key = "hero") { if (state.photos.isEmpty()) RefugeHero(value) else RefugeGallery(state.photos) }
                    item(key = "title") { Title(value, state.nights) }
                    value.description?.let { description ->
                        item(key = "description") { Paragraph(description) }
                    }
                    item(key = "nights-title") { SectionTitle(Res.string.refuge_nights_title, hint = Res.string.refuge_nights_hint) }
                    item(key = "nights") {
                        when (val nights = state.nights) {
                            Section.Loading, Section.Hidden -> CenteredProgress(small = true)
                            is Section.Failed -> RetryMessage(nights.message, onRetry = viewModel::retry)
                            is Section.Loaded -> NightStrip(nights.value)
                        }
                    }
                    val amenities = value.amenities.mapNotNull { it.label() }
                    if (amenities.isNotEmpty()) {
                        item(key = "amenities-title") { SectionTitle(Res.string.refuge_amenities_title) }
                        item(key = "amenities") { Amenities(amenities) }
                    }
                    if (state.nearbyTrails.isNotEmpty()) {
                        item(key = "trails-title") { SectionTitle(Res.string.refuge_trails_title) }
                        item(key = "trails") { NearbyTrails(state.nearbyTrails, onOpenTrail) }
                    }
                    item(key = "contacts") { Contacts(value) }
                }
            }
        }
        BackButton(onBack)
        // l'azione principale in fondo, nella zona del pollice (§2), sopra uno sfondo pieno
        if (loaded != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp + bottomInset),
                contentAlignment = Alignment.Center,
            ) {
                PrimaryPillButton(
                    stringResource(Res.string.booking_open),
                    onClick = { onBook(loaded.value.id, loaded.value.name, loaded.value.capacity) },
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                )
            }
        }
    }
}

// Le foto del rifugio a tutta larghezza, una alla volta scorrendo; "2/5" in basso a destra; un tocco la apre intera
@Composable
private fun RefugeGallery(photos: List<MediaResponse>) {
    val media = koinInject<MediaRepository>()
    val pager = rememberPagerState { photos.size }
    var opened by remember { mutableStateOf<MediaResponse?>(null) }
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val photo = photos[page]
            val signed = rememberSignedUrl(media, photo.id)
            val scope = rememberCoroutineScope()
            val url = signed.url
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(role = Role.Button) { opened = photo },
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
        // sfumatura verso la pagina, come per la mappa
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
        )
        if (photos.size > 1) {
            Text(
                stringResource(Res.string.refuge_photo_count, pager.currentPage + 1, photos.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    opened?.let { PhotoViewer(it, media, onClose = { opened = null }) }
}

// Senza foto: la mappa ferma sul rifugio, con il suo segnaposto e una sfumatura che la fonde con la pagina. Decorativa
// per gli screen reader: nome e posti sono scritti sotto
@Composable
private fun RefugeHero(refuge: RefugeResponse) {
    val colors = MaterialTheme.colorScheme
    val style = rememberMapStyle(rememberSavedMapType())
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = refuge.location.toGeoPoint().toPosition(), zoom = HeroZoom),
    ) {
        RefugeMarkers(listOf(refuge), fill = colors.primary, outline = colors.surfaceContainerLowest, symbol = colors.onPrimary, onRefugeClick = {})
    }
    Box(Modifier.fillMaxWidth().height(HeroHeight).clearAndSetSemantics { }) {
        MaplibreMap(
            state = mapState,
            interactions = MapInteractions.None,
            overlay = {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))),
                )
                // sopra la sfumatura, sul bordo in basso a destra (ha il suo fondo): in alto coprirebbe Indietro, al
                // centro il rifugio
                AttributionButton(mapState, Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp), alignEnd = true)
            },
        )
    }
}

// Nome, poi le capsule con quota e posti (e, appena arriva il calendario, com'è stanotte) e la stagione
@Composable
private fun Title(refuge: RefugeResponse, nights: Section<List<NightAvailability>>) {
    val colors = MaterialTheme.colorScheme
    val tonight = (nights as? Section.Loaded)?.value?.firstOrNull()
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(refuge.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            refuge.altitudeM?.let { InfoChip(stringResource(Res.string.refuge_altitude, it)) }
            InfoChip(pluralStringResource(Res.plurals.refuge_beds, refuge.capacity, refuge.capacity))
            when {
                tonight == null -> Unit
                tonight.isFull -> InfoChip(stringResource(Res.string.refuge_tonight_full))
                else -> InfoChip(pluralStringResource(Res.plurals.refuge_tonight_free, tonight.freeBeds, tonight.freeBeds), highlighted = true)
            }
        }
        refuge.season?.let { season ->
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Top) {
                // decorativa: la stagione è scritta accanto
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(season, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, highlighted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (highlighted) colors.primaryContainer else colors.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp),
    )
}

@Composable
private fun SectionTitle(title: StringResource, hint: StringResource? = null) {
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 12.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (hint != null) {
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// Le prossime notti come una striscia di giorni che scorre in orizzontale: si legge la settimana a colpo d'occhio
@Composable
private fun NightStrip(nights: List<NightAvailability>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(nights, key = { _, night -> night.date.toString() }) { index, night -> NightCard(night, tonight = index == 0) }
    }
}

// Una notte: giorno della settimana, numero del giorno e i posti liberi. Stanotte ha il fondo muschio. "Pieno" non è rosso
// (il rosso resta all'emergenza, §2): si distingue dal testo e dal tono spento
@Composable
private fun NightCard(night: NightAvailability, tonight: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val container = if (tonight) colors.primaryContainer else colors.surfaceContainerLowest
    val content = if (tonight) colors.onPrimaryContainer else colors.onSurface
    val dayLabel = if (tonight) stringResource(Res.string.refuge_tonight) else night.date.shortLabel()
    val bedsLabel = if (night.isFull) stringResource(Res.string.refuge_full) else pluralStringResource(Res.plurals.refuge_free_beds, night.freeBeds, night.freeBeds)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 84.dp)
            .clip(shape)
            .background(container)
            .border(1.dp, if (tonight) Color.Transparent else colors.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .clearAndSetSemantics { contentDescription = "$dayLabel, $bedsLabel" },
    ) {
        Text(
            if (tonight) stringResource(Res.string.refuge_tonight) else stringResource(night.date.weekday()),
            style = MaterialTheme.typography.labelLarge,
            color = if (tonight) content else colors.onSurfaceVariant,
        )
        Text(
            night.date.day.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
        Spacer(Modifier.height(6.dp))
        if (night.isFull) {
            Text(stringResource(Res.string.refuge_full), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        } else {
            Text(
                night.freeBeds.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (tonight) content else colors.primary,
            )
            Text(
                pluralStringResource(Res.plurals.refuge_free_short, night.freeBeds),
                style = MaterialTheme.typography.labelMedium,
                color = if (tonight) content else colors.onSurfaceVariant,
            )
        }
    }
}

// I servizi come capsule con la spunta: si leggono a colpo d'occhio e vanno a capo con il testo grande
@Composable
private fun Amenities(labels: List<StringResource>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        labels.forEach { label ->
            val colors = MaterialTheme.colorScheme
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surfaceContainerLowest)
                    .border(1.dp, colors.outlineVariant, CircleShape)
                    .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            ) {
                // decorativa: il servizio è scritto accanto
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// I sentieri che passano entro 5 km, in una card: come in "Vicino a te", il tocco apre il sentiero
@Composable
private fun NearbyTrails(trails: List<TrailSummaryResponse>, onOpenTrail: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(shape)
            .background(colors.surfaceContainerLowest)
            .border(1.dp, colors.outlineVariant, shape),
    ) {
        trails.forEachIndexed { index, trail ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 20.dp), color = colors.outlineVariant)
            TrailRow(trail, onClick = { onOpenTrail(trail.id) })
        }
    }
}

// Chiamata (se c'è il numero) e indicazioni con le mappe del telefono, affiancate
@Composable
private fun Contacts(refuge: RefugeResponse) {
    val dial = rememberDialer()
    val uriHandler = LocalUriHandler.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 28.dp),
    ) {
        refuge.phone?.let { phone ->
            ContactButton(Icons.Filled.Call, stringResource(Res.string.refuge_call), Modifier.weight(1f)) { dial(phone) }
        }
        ContactButton(Icons.Filled.Place, stringResource(Res.string.refuge_directions), Modifier.weight(1f)) {
            uriHandler.openUri(directionsUri(refuge.location.lat, refuge.location.lon))
        }
    }
}

@Composable
private fun ContactButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 52.dp)) {
        // decorativa: l'azione è scritta accanto
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
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

/** "sab 10/10": giorno della settimana e data, nel calendario, nel form e nelle prenotazioni. */
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

@Composable
private fun CenteredProgress(small: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = if (small) 16.dp else 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(if (small) 24.dp else 36.dp), strokeWidth = if (small) 2.dp else 3.dp)
    }
}

@Composable
private fun RetryMessage(message: UiMessage, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            message.resolve(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = onRetry)
    }
}
