package it.unical.geosentinel_mobile.trail.ui

import it.unical.geosentinel_mobile.core.ui.PageMaxWidth
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.profile_login
import geosentinel_mobile.shared.generated.resources.report_open
import geosentinel_mobile.shared.generated.resources.trail_description
import geosentinel_mobile.shared.generated.resources.trail_difficulty
import geosentinel_mobile.shared.generated.resources.trail_hazards
import geosentinel_mobile.shared.generated.resources.trail_hazards_none
import geosentinel_mobile.shared.generated.resources.trail_length_km
import geosentinel_mobile.shared.generated.resources.trail_load_failed_title
import geosentinel_mobile.shared.generated.resources.trail_login_for_more
import geosentinel_mobile.shared.generated.resources.trail_photo_description
import geosentinel_mobile.shared.generated.resources.trail_photo_failed
import geosentinel_mobile.shared.generated.resources.trail_photos
import geosentinel_mobile.shared.generated.resources.trail_pois
import geosentinel_mobile.shared.generated.resources.trail_retry
import geosentinel_mobile.shared.generated.resources.trail_stale_retry
import geosentinel_mobile.shared.generated.resources.trail_updated_day
import geosentinel_mobile.shared.generated.resources.trail_updated_today
import geosentinel_mobile.shared.generated.resources.trails_loading
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.TrailDetailResponse
import it.unical.geosentinel_mobile.core.location.GeoBounds
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toBoundingBox
import it.unical.geosentinel_mobile.core.location.toPositions
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.trail.data.MapType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.util.DpPadding
import kotlin.time.Clock
import kotlin.time.Instant


/**
 * Dettaglio di un sentiero (§M3.2): anteprima del percorso, difficoltà, lunghezza, stato, descrizione, POI e,
 * solo da loggati, pericoli attivi e foto. Schermata "spinta" sopra la mappa, con il suo Indietro.
 */
@Composable
fun TrailDetailScreen(
    trailId: String,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onReportHazard: (start: GeoPoint) -> Unit,
    // azioni del gestore del parco (M8.1), sotto lo stato; vuoto per gli altri ruoli
    manage: @Composable (TrailDetailResponse) -> Unit = {},
) {
    val viewModel = koinViewModel<TrailDetailViewModel>(key = "trail-$trailId") { parametersOf(trailId) }
    val ui by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val trail = ui.trail
        val error = ui.error
        when {
            trail != null -> TrailContent(
                trail = trail,
                ui = ui,
                onLogin = onLogin,
                onReportHazard = onReportHazard,
                manage = manage,
                onRetry = viewModel::retry,
                onRetryHazards = viewModel::retryHazards,
                onRetryPhotos = viewModel::retryPhotos,
                onPhotoShown = viewModel::onPhotoShown,
                onPhotoFailed = viewModel::onPhotoFailed,
                onPhotoRetry = viewModel::onPhotoRetry,
            )
            error != null -> LoadFailed(error, onRetry = viewModel::retry)
            else -> Loading()
        }
        BackButton(onBack, Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun TrailContent(
    trail: TrailDetailResponse,
    ui: TrailDetailUiState,
    onLogin: () -> Unit,
    onReportHazard: (start: GeoPoint) -> Unit,
    manage: @Composable (TrailDetailResponse) -> Unit,
    onRetry: () -> Unit,
    onRetryHazards: () -> Unit,
    onRetryPhotos: () -> Unit,
    onPhotoShown: (String) -> Unit,
    onPhotoFailed: (String) -> Unit,
    onPhotoRetry: (String) -> Unit,
) {
    val state = TrailState.of(trail.status.value)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PathPreview(trail, state, Modifier.fillMaxWidth().height(280.dp))
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.size(20.dp))
            Text(trail.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.size(12.dp))
            TrailStateBadge(state)
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(Res.string.trail_difficulty, stringResource(TrailDifficulty.of(trail.difficulty.value).label)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(Res.string.trail_length_km, formatKm(trail.lengthM)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UpdatedLine(ui.updatedAt, ui.stale, onRetry)
            manage(trail)

            trail.description?.takeIf { it.isNotBlank() }?.let { description ->
                SectionTitle(Res.string.trail_description)
                Text(description, style = MaterialTheme.typography.bodyLarge)
            }

            if (ui.pois.isNotEmpty()) {
                SectionTitle(Res.string.trail_pois)
                Card {
                    ui.pois.forEachIndexed { index, poi ->
                        if (index > 0) RowDivider()
                        PoiRow(poi)
                    }
                }
            }

            // Pericoli e foto: da ospite un invito ad accedere (le rotte richiedono il login)
            if (ui.hazards == Section.Hidden && ui.photos == Section.Hidden) {
                Spacer(Modifier.size(24.dp))
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(Res.string.trail_login_for_more), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = onLogin, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(Res.string.profile_login), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                SectionTitle(Res.string.trail_hazards)
                SectionBody(ui.hazards, onRetryHazards) { hazards ->
                    if (hazards.isEmpty()) {
                        Text(
                            stringResource(Res.string.trail_hazards_none),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Card {
                            hazards.forEachIndexed { index, hazard ->
                                if (index > 0) RowDivider()
                                HazardRow(hazard)
                            }
                        }
                    }
                }
                // Solo da loggati (qui siamo nel ramo con i pericoli): la mappa del form parte dall'inizio del sentiero
                trail.path.toPositions().firstOrNull()?.let { start ->
                    TextButton(
                        onClick = { onReportHazard(start.toGeoPoint()) },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(Res.string.report_open), fontWeight = FontWeight.SemiBold)
                    }
                }
                // Le foto solo se ce ne sono: una sezione vuota non dice niente
                val photos = ui.photos
                if (photos !is Section.Loaded || photos.value.isNotEmpty()) {
                    SectionTitle(Res.string.trail_photos)
                    SectionBody(photos, onRetryPhotos) { list ->
                        PhotoStrip(list, ui.photoUrls, ui.failedPhotos, onPhotoShown, onPhotoFailed, onPhotoRetry)
                    }
                }
            }
            Spacer(Modifier.size(32.dp))
        }
    }
}

// "Aggiornato alle 14:05" sotto la lunghezza; se l'aggiornamento non è riuscito (es. senza rete) si dice perché e
// un tocco riprova: i dati mostrati sono quelli salvati (§2, "offline visibile")
@Composable
private fun UpdatedLine(at: Instant?, stale: UiMessage?, onRetry: () -> Unit) {
    if (at == null) return
    val stamp = remember(at) { updatedAt(at, Clock.System.now()) }
    val line = if (stamp.today) stringResource(Res.string.trail_updated_today, stamp.time) else stringResource(Res.string.trail_updated_day, stamp.date, stamp.time)
    if (stale == null) {
        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        return
    }
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onRetry)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(stale.resolve() + " " + stringResource(Res.string.trail_stale_retry), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Anteprima ferma del percorso: inquadrato tutto, senza gesti (la mappa vera è nella scheda Mappa)
@Composable
private fun PathPreview(trail: TrailDetailResponse, state: TrailState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val mapType = rememberSavedMapType()
    val style = rememberMapStyle(mapType)
    val positions = remember(trail.path) { trail.path.toPositions() }
    val bounds = remember(positions) { GeoBounds.around(positions) }
    val mapIsDark = mapType == MapType.SATELLITE || colors.background.luminance() < 0.5f
    val casing = trailCasing(mapIsDark)
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = positions.firstOrNull() ?: CameraPosition().target, zoom = 12.0),
    ) {
        TrailPath(positions, state, casing = casing, unknownColor = colors.onSurfaceVariant)
    }
    LaunchedEffect(bounds) {
        // in alto c'è l'Indietro: margine più ampio
        bounds?.let { mapState.fitCameraToBounds(it.toBoundingBox(), fitPadding = DpPadding(left = 32.dp, top = 72.dp, right = 32.dp, bottom = 32.dp)) }
    }
    // decorativa per gli screen reader: nome, stato e lunghezza sono scritti sotto
    Box(modifier.clearAndSetSemantics { }) {
        MaplibreMap(
            state = mapState,
            interactions = MapInteractions.None,
            // solo l'attribuzione (obbligatoria): niente scala, bussola o controlli su un'anteprima ferma
            overlay = { AttributionButton(mapState, Modifier.align(Alignment.BottomEnd).padding(8.dp), alignEnd = true) },
        )
    }
}

/** Stato del sentiero con simbolo ed etichetta: si legge anche senza distinguere i colori (§2). */
@Composable
internal fun TrailStateBadge(state: TrailState) {
    val color = state.color()
    val icon: ImageVector = when (state) {
        TrailState.OPEN -> Icons.Filled.CheckCircle
        TrailState.WARNING -> Icons.Filled.Warning
        TrailState.CLOSED -> Icons.Filled.Close
        TrailState.UNKNOWN -> Icons.Filled.Info
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.5.dp, color),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // decorativa: l'etichetta dice già lo stato
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(state.label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PoiRow(poi: PoiResponse) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(poi.type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(poi.name, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(poi.type.label()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HazardRow(hazard: HazardResponse) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
    ) {
        // decorativo: tipo e gravità sono scritti accanto (il rosso solo per CRITICAL)
        Icon(Icons.Filled.Warning, contentDescription = null, tint = hazard.severity.color(), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(hazard.type.label()), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(hazard.severity.label()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            hazard.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.size(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// Striscia di foto: ognuna chiede il suo URL firmato quando compare, e uno nuovo se il download non riesce (scaduto)
@Composable
private fun PhotoStrip(
    photos: List<MediaResponse>,
    urls: Map<String, String>,
    failed: Set<String>,
    onShown: (String) -> Unit,
    onFailed: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    val context = LocalPlatformContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
            val url = urls[photo.id]
            val description = stringResource(Res.string.trail_photo_description, index + 1, photos.size)
            LaunchedEffect(photo.id, url) { if (url == null) onShown(photo.id) }
            Box(
                Modifier
                    .size(width = 220.dp, height = 160.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                if (photo.id in failed) {
                    // tutta la foto è il bersaglio del tocco (ben oltre i 48 dp)
                    Box(
                        Modifier.fillMaxSize().clickable(role = Role.Button) { onRetry(photo.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(Res.string.trail_photo_failed) + "\n" + stringResource(Res.string.trail_retry),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else if (url != null) {
                    val request = remember(url) {
                        // la chiave della cache è l'id, non l'URL: l'URL cambia a ogni richiesta, la foto no
                        ImageRequest.Builder(context).data(url).memoryCacheKey(photo.id).diskCacheKey(photo.id).build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { onFailed(photo.id) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun <T> SectionBody(section: Section<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (section) {
        Section.Loading, Section.Hidden -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        is Section.Failed -> Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(section.message.resolve(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.trail_retry), fontWeight = FontWeight.SemiBold)
            }
        }
        is Section.Loaded -> content(section.value)
    }
}

@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxSize().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(12.dp))
        Text(stringResource(Res.string.trails_loading), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LoadFailed(message: UiMessage, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.trail_load_failed_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.size(8.dp))
        Text(message.resolve(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.trail_retry), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp).semantics { heading() },
    )
}

// Card come nel Profilo: un passo di tono sopra lo sfondo e un filo di bordo, senza ombra
@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
