package it.unical.geosentinel_mobile.trail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.map_search_empty
import geosentinel_mobile.shared.generated.resources.map_search_hint
import geosentinel_mobile.shared.generated.resources.map_search_refuge
import geosentinel_mobile.shared.generated.resources.map_search_scope
import geosentinel_mobile.shared.generated.resources.map_search_trail
import geosentinel_mobile.shared.generated.resources.nav_profile
import geosentinel_mobile.shared.generated.resources.near_me_title
import geosentinel_mobile.shared.generated.resources.near_peek_open
import geosentinel_mobile.shared.generated.resources.trail_eyebrow
import geosentinel_mobile.shared.generated.resources.trail_length_km
import geosentinel_mobile.shared.generated.resources.trails_zoom_in_hint
import it.unical.geosentinel_mobile.api.model.PoiResponse
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.api.model.TrailSummaryResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.distanceTo
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPositions
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.media.ui.AvatarPhoto
import androidx.compose.animation.animateContentSize
import geosentinel_mobile.shared.generated.resources.near_peek_collapse
import geosentinel_mobile.shared.generated.resources.near_peek_expand
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.IconTile
import it.unical.geosentinel_mobile.core.ui.StatusChip
import it.unical.geosentinel_mobile.core.ui.daylightShortLabel
import it.unical.geosentinel_mobile.core.ui.enterStagger
import it.unical.geosentinel_mobile.core.ui.islandShadow
import it.unical.geosentinel_mobile.core.ui.rememberDaylight
import it.unical.geosentinel_mobile.core.ui.theme.MarkerRefuge
import it.unical.geosentinel_mobile.core.ui.theme.warningTextColor
import org.jetbrains.compose.resources.stringResource

// Elementi sopra la mappa del redesign "Luce di quota" (Figma, schermata "01 Mappa"): in alto la barra di ricerca con
// l'avatar, in basso il foglio "Vicino a te" con le card dei sentieri dell'area. La mappa sotto resta quella di prima.

/** In alto: la capsula "Cerca sentieri, rifugi, cime" e, accanto, il tondo dell'utente che apre il Profilo. */
@Composable
internal fun MapTopBar(onSearch: () -> Unit, onProfile: () -> Unit, initial: Char?, avatarMediaId: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = mapControlColor(),
            modifier = Modifier.weight(1f).widthIn(max = 520.dp).islandShadow(CircleShape, MapControlElevation),
        ) {
            Row(
                Modifier
                    .heightIn(min = 52.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onSearch)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(Res.string.map_search_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
        val label = stringResource(Res.string.nav_profile)
        Box(
            Modifier
                .size(48.dp)
                .islandShadow(CircleShape, MapControlElevation)
                .clip(CircleShape)
                .background(colors.tertiary)
                .clickable(role = Role.Button, onClick = onProfile)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (initial != null) {
                Text(initial.uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.onTertiary)
            } else {
                Icon(AppIcons.User, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(22.dp))
            }
            // la foto profilo sopra l'iniziale, come nel Profilo
            avatarMediaId?.let { AvatarPhoto(it, 48.dp) }
        }
    }
}

/** Un risultato della ricerca: un sentiero, un rifugio o un punto di quelli caricati sulla mappa. */
internal sealed interface MapSearchHit {
    val name: String
    data class Trail(val trail: TrailSummaryResponse) : MapSearchHit { override val name get() = trail.name }
    data class Refuge(val refuge: RefugeResponse) : MapSearchHit { override val name get() = refuge.name }
    data class Poi(val poi: PoiResponse) : MapSearchHit { override val name get() = poi.name }
}

/** I risultati per [query] (senza maiuscole né accenti): prima quelli che iniziano così, poi quelli che lo contengono. */
internal fun searchMap(query: String, trails: List<TrailSummaryResponse>, refuges: List<RefugeResponse>, pois: List<PoiResponse>): List<MapSearchHit> {
    val needle = query.fold()
    if (needle.isBlank()) return emptyList()
    val all = trails.map { MapSearchHit.Trail(it) } + refuges.map { MapSearchHit.Refuge(it) } + pois.map { MapSearchHit.Poi(it) }
    return all
        .filter { it.name.fold().contains(needle) }
        .sortedWith(compareBy({ !it.name.fold().startsWith(needle) }, { it.name.length }))
        .take(30)
}

private fun String.fold(): String = lowercase().trim()
    .replace('à', 'a').replace('è', 'e').replace('é', 'e').replace('ì', 'i').replace('ò', 'o').replace('ù', 'u')

/**
 * Ricerca (foglio dal basso): un campo e, sotto, i risultati tra quello che la mappa ha già caricato nell'area. Il
 * backend non ha una ricerca per nome: si cerca qui, e lo si dice ("Nell'area della mappa").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapSearchSheet(
    trails: List<TrailSummaryResponse>,
    refuges: List<RefugeResponse>,
    pois: List<PoiResponse>,
    onPick: (MapSearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, trails, refuges, pois) { searchMap(query, trails, refuges, pois) }
    // si scrive subito: la tastiera si apre con il foglio
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            FormTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(Res.string.map_search_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Text(
                stringResource(Res.string.map_search_scope).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 20.dp, bottom = 8.dp),
            )
            if (query.isNotBlank() && hits.isEmpty()) {
                Text(
                    stringResource(Res.string.map_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(6.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(hits, key = { _, hit -> hit::class.simpleName + hit.name + hit.hashCode() }) { index, hit ->
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = colors.outlineVariant)
                    SearchRow(hit) { onPick(hit) }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(hit: MapSearchHit, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (icon, tint, kind) = when (hit) {
        is MapSearchHit.Trail -> Triple(AppIcons.Route, TrailState.of(hit.trail.status.value).color(), stringResource(Res.string.map_search_trail))
        is MapSearchHit.Refuge -> Triple(AppIcons.House, MarkerRefuge, stringResource(Res.string.map_search_refuge))
        is MapSearchHit.Poi -> Triple(hit.poi.type.icon(), hit.poi.type.markerColor(), stringResource(hit.poi.type.label()))
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon as ImageVector, tint = tint, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(kind, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/**
 * "Vicino a te" (Figma): un foglio basso sopra la barra con il titolo, l'ora del tramonto e i sentieri dell'area visibile
 * in card che scorrono di lato, i più vicini al centro della mappa per primi. Si chiude con la maniglia o la freccia
 * ([expanded], ricordato tra un avvio e l'altro) e resta una capsula sola, per lasciare la mappa libera. Il titolo apre
 * il foglio completo (Sentieri | Pericoli intorno alla tua posizione).
 */
@Composable
internal fun NearMePeek(
    trails: List<TrailSummaryResponse>,
    center: GeoPoint,
    zoomTooLow: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenTrail: (String) -> Unit,
    onOpenNearMe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val sorted = remember(trails) {
        trails.sortedBy { trail -> trail.path.toPositions().firstOrNull()?.toGeoPoint()?.distanceTo(center) ?: Double.MAX_VALUE }.take(12)
    }
    val openLabel = stringResource(Res.string.near_peek_open)
    val toggleLabel = stringResource(if (expanded) Res.string.near_peek_collapse else Res.string.near_peek_expand)
    val shape = RoundedCornerShape(30.dp)
    Surface(
        shape = shape,
        color = colors.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().animateContentSize().islandShadow(shape, MapControlElevation),
    ) {
        Column(Modifier.padding(bottom = if (expanded) 16.dp else 0.dp)) {
            if (expanded) {
                // la maniglia: un tocco chiude il foglio
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(36.dp).height(5.dp).background(colors.onSurfaceVariant.copy(alpha = 0.4f), CircleShape))
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpenNearMe)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.near_me_title),
                        style = if (expanded) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.semantics { heading() },
                    )
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp).size(20.dp))
                    Spacer(Modifier.weight(1f))
                    val daylight = rememberDaylight()
                    daylightShortLabel(daylight)?.let { StatusChip(it, colors.primary, icon = if (daylight.remaining != null) AppIcons.Sunset else AppIcons.Sunrise) }
                }
                // apri / chiudi: la freccia dice dove va il foglio
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onToggle)
                        .semantics { contentDescription = toggleLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (expanded) AppIcons.ChevronDown else AppIcons.ChevronUp,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (expanded) {
                if (sorted.isEmpty()) {
                    Text(
                        stringResource(Res.string.trails_zoom_in_hint).takeIf { zoomTooLow } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        itemsIndexed(sorted, key = { _, trail -> trail.id }) { index, trail ->
                            PeekTrailCard(trail, index) { onOpenTrail(trail.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeekTrailCard(trail: TrailSummaryResponse, index: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state = TrailState.of(trail.status.value)
    val (number, title) = remember(trail.name) { splitTrailName(trail.name) }
    val stateColor = when (state) {
        TrailState.OPEN -> colors.primary
        TrailState.WARNING -> warningTextColor()
        else -> state.color()
    }
    Column(
        Modifier
            .width(184.dp)
            .heightIn(min = 132.dp)
            .enterStagger(index)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            (stringResource(Res.string.trail_eyebrow) + (number?.let { " $it" } ?: "")).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.weight(1f, fill = false).height(8.dp))
        Text(
            stringResource(Res.string.trail_length_km, formatKm(trail.lengthM)),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Box(Modifier.size(8.dp).background(state.color(), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(state.label), style = MaterialTheme.typography.labelMedium, color = stateColor)
        }
    }
}

