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
import it.unical.geosentinel_mobile.core.ui.CompactBar
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.StickyActionBar
import androidx.compose.foundation.shape.RoundedCornerShape
import geosentinel_mobile.shared.generated.resources.trail_tile_difficulty
import geosentinel_mobile.shared.generated.resources.trail_tile_length
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
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import it.unical.geosentinel_mobile.core.ui.GroupCard
import it.unical.geosentinel_mobile.core.ui.IconTile
import it.unical.geosentinel_mobile.core.ui.TonalPillButton
import it.unical.geosentinel_mobile.core.ui.LocalDirections
import it.unical.geosentinel_mobile.trail.data.ElevationProfile
import it.unical.geosentinel_mobile.trail.data.ElevationRepository
import it.unical.geosentinel_mobile.trail.data.hikingMinutes
import org.koin.compose.koinInject
import geosentinel_mobile.shared.generated.resources.stat_ascent
import geosentinel_mobile.shared.generated.resources.stat_time
import geosentinel_mobile.shared.generated.resources.trail_eyebrow
import geosentinel_mobile.shared.generated.resources.trail_profile_title
import geosentinel_mobile.shared.generated.resources.trail_profile_description
import geosentinel_mobile.shared.generated.resources.trail_hours_minutes
import geosentinel_mobile.shared.generated.resources.trail_minutes
import geosentinel_mobile.shared.generated.resources.report_short
import geosentinel_mobile.shared.generated.resources.refuge_directions
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.core.ui.Stat
import it.unical.geosentinel_mobile.core.ui.StatStrip
import it.unical.geosentinel_mobile.core.ui.StatusChip
import it.unical.geosentinel_mobile.core.ui.theme.MapMutedDark
import it.unical.geosentinel_mobile.core.ui.theme.MapMutedLight
import it.unical.geosentinel_mobile.core.ui.theme.emergencySoftColor
import it.unical.geosentinel_mobile.core.ui.theme.emergencyTextColor
import it.unical.geosentinel_mobile.core.ui.theme.warningSoftColor
import it.unical.geosentinel_mobile.core.ui.theme.warningTextColor


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

    val scroll = rememberScrollState()
    // l'anteprima è scorsa via (fin sotto la barra): in alto compare la barra con il nome, come nelle pagine di iOS
    val heroPx = with(LocalDensity.current) { (PreviewHeight - 96.dp).toPx() }
    val collapsed by remember { derivedStateOf { scroll.value > heroPx } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val trail = ui.trail
        val error = ui.error
        when {
            trail != null -> TrailContent(
                trail = trail,
                ui = ui,
                scroll = scroll,
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
        val name = trail?.name
        if (collapsed && name != null) CompactBar(name, collapsed = true, onBack = onBack, actions = null) else BackButton(onBack, Modifier.align(Alignment.TopStart))
    }
}

// Altezza dell'anteprima del percorso in testata
private val PreviewHeight = 300.dp

@Composable
private fun TrailContent(
    trail: TrailDetailResponse,
    ui: TrailDetailUiState,
    scroll: ScrollState,
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
    // segnalare: l'azione principale della pagina, fissa in basso (solo da loggati, dall'inizio del percorso)
    val reportFrom = if (ui.hazards != Section.Hidden) trail.path.toPositions().firstOrNull() else null
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // l'anteprima da bordo a bordo e il pannello del contenuto che le sale sopra con gli angoli da 28 (redesign 11/2026)
        Box(Modifier.fillMaxWidth().height(PreviewHeight)) {
            PathPreview(trail, state, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            )
        }
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
            // "Sentiero 404: Camigliatello – Spezzano": il numero va nell'occhiello, il nome resta il titolo
            val (fromName, title) = remember(trail.name) { splitTrailName(trail.name) }
            val facts = remember(trail.description) { trailFacts(trail.description) }
            val number = fromName ?: facts.waymark
            Text(
                (stringResource(Res.string.trail_eyebrow) + (number?.let { " $it" } ?: "") + (facts.caiScale?.let { " · CAI $it" } ?: "")).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrailStateBadge(state)
                StatusChip(
                    stringResource(TrailDifficulty.of(trail.difficulty.value).label),
                    MaterialTheme.colorScheme.onSurface,
                    icon = AppIcons.Mountain,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Spacer(Modifier.size(16.dp))
            // quote dalle Terrain Tiles del rilievo: dislivello, tempo e profilo; senza (offline) solo la lunghezza
            val elevation = koinInject<ElevationRepository>()
            val profile by produceState<ElevationProfile?>(null, trail.id) {
                value = elevation.profile(trail.path.toPositions().map { it.toGeoPoint() })
            }
            val climb = profile
            StatStrip(
                listOfNotNull(
                    Stat(stringResource(Res.string.trail_tile_length), formatKm(trail.lengthM), "km"),
                    climb?.let { Stat(stringResource(Res.string.stat_ascent), it.ascentM.toString(), "m") },
                    climb?.let { Stat(stringResource(Res.string.stat_time), durationLabel(hikingMinutes(trail.lengthM.toDouble(), it.ascentM, it.descentM))) },
                ),
            )
            if (climb != null) {
                SectionTitle(Res.string.trail_profile_title)
                ElevationChart(climb)
            }
            UpdatedLine(ui.updatedAt, ui.stale, onRetry)
            manage(trail)

            facts.text?.let { description ->
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
                // Le foto solo se ce ne sono: una sezione vuota non dice niente
                val photos = ui.photos
                if (photos !is Section.Loaded || photos.value.isNotEmpty()) {
                    SectionTitle(Res.string.trail_photos)
                    SectionBody(photos, onRetryPhotos) { list ->
                        PhotoStrip(list, ui.photoUrls, ui.failedPhotos, onPhotoShown, onPhotoFailed, onPhotoRetry)
                    }
                }
            }
            // spazio per la barra fissa in basso
            Spacer(Modifier.size(112.dp))
        }
    }
    // la mappa del form parte dall'inizio del sentiero
    // in basso, fissi: segnalare (da loggati) e le indicazioni fino all'inizio del sentiero, col nostro motore sulla mappa
    val start = trail.path.toPositions().firstOrNull()
    val directions = LocalDirections.current
    if (start != null) {
        StickyActionBar(Modifier.align(Alignment.BottomCenter)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (reportFrom != null) {
                    TonalPillButton(
                        stringResource(Res.string.report_short),
                        onClick = { onReportHazard(reportFrom.toGeoPoint()) },
                        icon = AppIcons.Alert,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    )
                }
                PrimaryPillButton(
                    stringResource(Res.string.refuge_directions),
                    onClick = { directions(trail.name, start.latitude, start.longitude) },
                    icon = AppIcons.Navigation,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
        TrailPath(positions, state, casing = casing, unknownColor = if (mapIsDark) MapMutedDark else MapMutedLight)
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

/** Stato del sentiero in una capsula con simbolo ed etichetta: si legge anche senza distinguere i colori (§2). */
@Composable
internal fun TrailStateBadge(state: TrailState) {
    val colors = MaterialTheme.colorScheme
    val icon: ImageVector = when (state) {
        TrailState.OPEN -> AppIcons.CheckCircle
        TrailState.WARNING -> AppIcons.Alert
        TrailState.CLOSED -> AppIcons.Close
        TrailState.UNKNOWN -> AppIcons.Info
    }
    // testo e fondo con contrasto verificato (almeno 5:1); il rosso solo per "Chiuso"
    val (content, container) = when (state) {
        TrailState.OPEN -> colors.primary to colors.primaryContainer
        TrailState.WARNING -> warningTextColor() to warningSoftColor()
        TrailState.CLOSED -> emergencyTextColor() to emergencySoftColor()
        TrailState.UNKNOWN -> colors.onSurfaceVariant to colors.surfaceContainerHighest
    }
    StatusChip(stringResource(state.label), content, icon = icon, container = container)
}

@Composable
private fun PoiRow(poi: PoiResponse) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // lo stesso bollino della mappa (decorativo: il tipo è scritto accanto)
        Box(Modifier.size(32.dp).background(poi.type.markerColor(), CircleShape), contentAlignment = Alignment.Center) {
            Icon(poi.type.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(poi.name, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(poi.type.label()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HazardRow(hazard: HazardResponse) {
    val tint = hazard.severity.color()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 14.dp).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
    ) {
        // decorativo: tipo e gravità sono scritti accanto (il rosso solo per CRITICAL)
        IconTile(AppIcons.Alert, tint = tint, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(hazard.type.label()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(hazard.severity.label()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            hazard.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.size(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Profilo altimetrico: l'area sotto la linea sfuma verso il basso; quota minima e massima negli angoli in alto. */
@Composable
private fun ElevationChart(profile: ElevationProfile) {
    val colors = MaterialTheme.colorScheme
    val line = colors.primary
    val description = stringResource(Res.string.trail_profile_description, profile.minM, profile.maxM, profile.ascentM)
    GroupCard {
        Box(Modifier.fillMaxWidth().height(132.dp).clearAndSetSemantics { contentDescription = description }) {
            Canvas(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 14.dp)) {
                val values = profile.meters
                val low = profile.minM.toFloat()
                val range = (profile.maxM - profile.minM).coerceAtLeast(20).toFloat()
                fun at(index: Int) = Offset(
                    size.width * index / (values.size - 1),
                    size.height * (1f - (values[index].toFloat() - low) / range),
                )
                val stroke = Path().apply {
                    moveTo(at(0).x, at(0).y)
                    for (i in 1 until values.size) lineTo(at(i).x, at(i).y)
                }
                val area = Path().apply {
                    addPath(stroke)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.32f), line.copy(alpha = 0f))))
                drawPath(stroke, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Text(
                "${profile.minM} m",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 10.dp),
            )
            Text(
                "${profile.maxM} m",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 10.dp),
            )
        }
    }
}

// "Sentiero 404: Camigliatello – Spezzano" → ("404", "Camigliatello – Spezzano"); altri nomi restano interi
internal fun splitTrailName(name: String): Pair<String?, String> {
    val match = Regex("""^Sentiero\s+([\p{L}\d./-]{1,8})\s*[:–-]\s*(.+)$""", RegexOption.IGNORE_CASE).find(name.trim())
    return if (match != null) match.groupValues[1] to match.groupValues[2] else null to name
}

/** Quello che la descrizione importata dice in righe fisse: segnavia e scala CAI vanno nell'occhiello. */
internal data class TrailFacts(val waymark: String?, val caiScale: String?, val text: String?)

/**
 * Divide la descrizione importata: "Segnavia 412" e "Scala CAI: T" diventano dati, le righe di attribuzione delle fonti
 * (OpenStreetMap, ©) si tolgono (stanno in Profilo > Crediti e licenze), il resto resta il testo.
 */
internal fun trailFacts(description: String?): TrailFacts {
    var waymark: String? = null
    var scale: String? = null
    val kept = description.orEmpty().lines().filter { line ->
        val trimmed = line.trim()
        Regex("""^Segnavia\s+(.+)$""", RegexOption.IGNORE_CASE).find(trimmed)?.let { waymark = it.groupValues[1].trim(); return@filter false }
        Regex("""^Scala CAI:\s*(.+)$""", RegexOption.IGNORE_CASE).find(trimmed)?.let { scale = it.groupValues[1].trim(); return@filter false }
        !isSourceCredit(trimmed)
    }
    return TrailFacts(waymark, scale, kept.joinToString("\n").trim().ifBlank { null })
}

/** Una riga che cita la fonte dei dati: si mostra solo nei crediti. */
internal fun isSourceCredit(line: String): Boolean =
    line.contains("OpenStreetMap", ignoreCase = true) || line.contains("©") || line.contains("Wikimedia", ignoreCase = true)

// 190 minuti → "3 h 10", 45 → "45 min"
@Composable
private fun durationLabel(minutes: Int): String =
    if (minutes >= 60) stringResource(Res.string.trail_hours_minutes, minutes / 60, (minutes % 60).toString().padStart(2, '0')) else stringResource(Res.string.trail_minutes, minutes)

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
        modifier = Modifier.padding(top = 32.dp, bottom = 8.dp).semantics { heading() },
    )
}

// Card del nuovo sistema (GroupCard: bianca, angoli da 24, ombra morbida)
@Composable
private fun Card(content: @Composable () -> Unit) = GroupCard(content = content)

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
