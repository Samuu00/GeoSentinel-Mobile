package it.unical.geosentinel_mobile.hazard.ui

import it.unical.geosentinel_mobile.core.ui.PageMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.my_hazards_empty_body
import geosentinel_mobile.shared.generated.resources.my_hazards_empty_title
import geosentinel_mobile.shared.generated.resources.my_hazards_go_to_map
import geosentinel_mobile.shared.generated.resources.my_hazards_intro
import geosentinel_mobile.shared.generated.resources.my_hazards_open_trail
import geosentinel_mobile.shared.generated.resources.my_hazards_title
import geosentinel_mobile.shared.generated.resources.trail_retry
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.media.ui.HazardPhotoStrip
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.PageHeader
import it.unical.geosentinel_mobile.core.ui.groupedItem
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.trail.ui.color
import it.unical.geosentinel_mobile.trail.ui.label
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

// Quante righe prima del fondo si chiede la pagina dopo: arriva prima che l'utente se ne accorga
private const val PrefetchDistance = 3

/**
 * "Le mie segnalazioni" (M4.3), spinta dal Profilo: tipo, gravità, data e stato in parole di ogni segnalazione, dalla
 * più recente. Il tocco su una segnalazione con un sentiero apre il sentiero. Trascinando in basso si ricarica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHazardsScreen(onBack: () -> Unit, onOpenTrail: (String) -> Unit, onGoToMap: () -> Unit) {
    val viewModel = koinViewModel<MyHazardsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // La pagina dopo quando le ultime righe entrano sullo schermo (il ViewModel ignora le chiamate di troppo)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - PrefetchDistance
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMore() }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    PageHeader(title = stringResource(Res.string.my_hazards_title), intro = stringResource(Res.string.my_hazards_intro), hasBack = true)
                }
                when {
                    state.items.isEmpty() && state.loading -> item(key = "loading") { CenteredProgress() }
                    state.items.isEmpty() && state.error != null -> item(key = "error") {
                        Message(text = state.error!!.resolve(), isError = true) {
                            PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = viewModel::retry)
                        }
                    }
                    state.items.isEmpty() -> item(key = "empty") {
                        Message(title = stringResource(Res.string.my_hazards_empty_title), text = stringResource(Res.string.my_hazards_empty_body)) {
                            PrimaryPillButton(stringResource(Res.string.my_hazards_go_to_map), onClick = onGoToMap)
                        }
                    }
                    else -> {
                        item(key = "group-gap") { Spacer(Modifier.height(8.dp)) }
                        itemsIndexed(state.items, key = { _, hazard -> hazard.id }) { index, hazard ->
                            HazardCard(hazard, onOpenTrail, first = index == 0, last = index == state.items.lastIndex)
                        }
                        when {
                            state.loadingMore -> item(key = "more") { CenteredProgress(small = true) }
                            // errore sulla pagina dopo: le righe già caricate restano, sotto il motivo e "Riprova"
                            state.error != null -> item(key = "more-error") { InlineError(state.error!!.resolve(), onRetry = viewModel::retry) }
                        }
                    }
                }
            }
        }
        BackButton(onBack)
    }
}

// Una segnalazione: icona nel colore della gravità (il rosso solo per CRITICAL), tipo e stato in alto, gravità e data
// sotto, l'inizio della descrizione. Con un sentiero tutta la card lo apre (freccia a destra).
@Composable
private fun HazardCard(hazard: HazardResponse, onOpenTrail: (String) -> Unit, first: Boolean, last: Boolean) {
    // una riga del gruppo delle segnalazioni (prima una card per segnalazione)
    Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).groupedItem(first, last)) {
        if (!first) GroupDivider(54.dp)
        HazardRowContent(hazard, onOpenTrail)
    }
}

@Composable
private fun HazardRowContent(hazard: HazardResponse, onOpenTrail: (String) -> Unit) {
    val trailId = hazard.trailId
    val openLabel = stringResource(Res.string.my_hazards_open_trail)
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (trailId != null) Modifier.clickable(onClickLabel = openLabel, role = Role.Button) { onOpenTrail(trailId) } else Modifier)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        // decorativo: tipo e gravità sono scritti accanto
        Icon(Icons.Filled.Warning, contentDescription = null, tint = hazard.severity.color(), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(hazard.type.label()),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                StatusChip(hazard.status)
            }
            Text(
                stringResource(hazard.severity.label()) + " · " + remember(hazard.createdAt) { formatDate(hazard.createdAt) },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            hazard.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            HazardPhotoStrip(hazard.id, Modifier.padding(top = 10.dp))
        }
        if (trailId != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).align(Alignment.CenterVertically),
            )
        }
    }
}

// Lo stato è sempre scritto; il colore lo accompagna e basta. Prugna (non rosso) per "Non confermata"
@Composable
private fun StatusChip(status: HazardResponse.Status) {
    val colors = MaterialTheme.colorScheme
    val (background: Color, content: Color) = when (status) {
        HazardResponse.Status.VERIFIED -> colors.primaryContainer to colors.onPrimaryContainer
        HazardResponse.Status.REJECTED -> colors.errorContainer to colors.onErrorContainer
        HazardResponse.Status.RESOLVED -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        HazardResponse.Status.SUBMITTED, HazardResponse.Status.unknown_default_open_api -> colors.surfaceContainerHighest to colors.onSurface
    }
    Text(
        stringResource(status.label()),
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun CenteredProgress(small: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = if (small) 12.dp else 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (small) 24.dp else 36.dp),
            strokeWidth = if (small) 2.dp else 3.dp,
        )
    }
}

// Stato vuoto o d'errore: testo centrato e una sola azione
@Composable
private fun Message(text: String, title: String? = null, isError: Boolean = false, action: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
        )
        Spacer(Modifier.height(24.dp))
        action()
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.trail_retry))
        }
    }
}

// "05/10/2026, 13:36" nell'ora del telefono
private fun formatDate(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    fun Int.two() = toString().padStart(2, '0')
    return "${local.day.two()}/${local.month.number.two()}/${local.year}, ${local.hour.two()}:${local.minute.two()}"
}
