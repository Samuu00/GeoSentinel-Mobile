package it.unical.geosentinel_mobile.rescue.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.staff_team_open
import geosentinel_mobile.shared.generated.resources.rescue_age_hours
import geosentinel_mobile.shared.generated.resources.rescue_age_minutes
import geosentinel_mobile.shared.generated.resources.rescue_age_now
import geosentinel_mobile.shared.generated.resources.rescue_alert_open
import geosentinel_mobile.shared.generated.resources.rescue_alerts_empty_body
import geosentinel_mobile.shared.generated.resources.rescue_alerts_empty_title
import geosentinel_mobile.shared.generated.resources.rescue_alerts_intro
import geosentinel_mobile.shared.generated.resources.rescue_alerts_title
import geosentinel_mobile.shared.generated.resources.rescue_raised_ago
import geosentinel_mobile.shared.generated.resources.rescue_team_label
import geosentinel_mobile.shared.generated.resources.trail_retry
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.push.PushEvent
import it.unical.geosentinel_mobile.core.push.PushManager
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.GroupIconIndent
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.groupedItem
import it.unical.geosentinel_mobile.core.ui.HeaderLink
import it.unical.geosentinel_mobile.core.ui.PageHeader
import it.unical.geosentinel_mobile.core.ui.pageBottomInset
import it.unical.geosentinel_mobile.core.ui.PageHeaderHeight
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val ContentMaxWidth = 600.dp
private val CardShape = RoundedCornerShape(20.dp)
private const val PrefetchDistance = 3
// con la schermata aperta lista e dettaglio si rileggono ogni 30 s, come lo stato nella schermata SOS (M6.2)
internal val RescueRefreshEvery = 30.seconds

/**
 * Scheda Allerte (M7.2), per `RESCUE_OPERATOR` e `ADMIN`: le allerte aperte, chi aspetta da più tempo in cima. Per ognuna
 * lo stato a parole, da quanto è aperta e la squadra se c'è; le note mediche solo nel dettaglio. Si rilegge trascinando in
 * basso, ogni 30 s in primo piano e subito a ogni push di un SOS nuovo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescueAlertsListScreen(onOpenAlert: (String) -> Unit, onCreateTeam: (() -> Unit)? = null) {
    val viewModel = koinViewModel<RescueAlertsListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val push = koinInject<PushManager>()
    val now = rememberNow()

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
    // al ritorno in primo piano o dal dettaglio subito (la prima volta il ViewModel sta già caricando), poi ogni 30 s
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.poll()
                delay(RescueRefreshEvery)
            }
        }
    }
    LaunchedEffect(push) {
        push.received.filterIsInstance<PushEvent.SosRaised>().collect { viewModel.poll() }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 24.dp + pageBottomInset()),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    PageHeader(
                        title = stringResource(Res.string.rescue_alerts_title),
                        intro = stringResource(Res.string.rescue_alerts_intro),
                        // "Nuova squadra di soccorso" (M8.3), solo per ADMIN
                        below = onCreateTeam?.let { { HeaderLink(stringResource(Res.string.staff_team_open), it) } },
                    )
                }
                when {
                    state.items.isEmpty() && state.loading -> item(key = "loading") { CenteredProgress() }
                    state.items.isEmpty() && state.error != null -> item(key = "error") {
                        Message(text = state.error!!.resolve(), isError = true) {
                            PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = viewModel::retry)
                        }
                    }
                    state.items.isEmpty() -> item(key = "empty") {
                        Message(title = stringResource(Res.string.rescue_alerts_empty_title), text = stringResource(Res.string.rescue_alerts_empty_body))
                    }
                    else -> {
                        item(key = "group-gap") { Spacer(Modifier.height(8.dp)) }
                        itemsIndexed(state.items, key = { _, alert -> alert.id }) { index, alert ->
                            AlertCard(alert, now, first = index == 0, last = index == state.items.lastIndex) { onOpenAlert(alert.id) }
                        }
                        when {
                            state.loadingMore -> item(key = "more") { CenteredProgress(small = true) }
                            state.error != null -> item(key = "more-error") { InlineError(state.error!!.resolve(), onRetry = viewModel::retry) }
                        }
                    }
                }
            }
        }
    }
}

// Un'allerta: campanella rossa (piena senza squadra), stato a parole, da quanto è aperta e la squadra.
// Tutta la card apre il dettaglio.
@Composable
private fun AlertCard(alert: SosAlertResponse, now: Instant, first: Boolean, last: Boolean, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val openLabel = stringResource(Res.string.rescue_alert_open)
    // una riga del gruppo delle allerte (prima una card per allerta)
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).groupedItem(first, last)) {
        if (!first) GroupDivider(GroupIconIndent)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                .padding(16.dp)
                .semantics(mergeDescendants = true) { },
        ) {
            AlertBadge(waiting = alert.status == SosAlertResponse.Status.ACTIVE)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(alert.status.label()), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(Res.string.rescue_raised_ago, ago(alert.createdAt, now)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                alert.assignedTeam?.let { team ->
                    Text(
                        stringResource(Res.string.rescue_team_label, "${team.name} (${team.callSign})"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

// Campanella nel tondo rosso: pieno senza squadra, chiaro con il bordo rosso con la squadra (come sulla mappa).
// Decorativa: lo stato è scritto accanto.
@Composable
internal fun AlertBadge(waiting: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (waiting) EmergencyRed else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(2.dp, EmergencyRed, CircleShape),
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = null, tint = if (waiting) Color.White else EmergencyRed, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CenteredProgress(small: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = if (small) 12.dp else 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(if (small) 24.dp else 36.dp), strokeWidth = if (small) 2.dp else 3.dp)
    }
}

// Stato vuoto o d'errore: testo centrato e al massimo un'azione
@Composable
private fun Message(text: String, title: String? = null, isError: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
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
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp),
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

// L'ora di adesso, aggiornata ogni 30 s: "da quanto" cambia anche quando i dati restano uguali
@Composable
internal fun rememberNow(): Instant = produceState(Clock.System.now()) {
    while (true) {
        delay(RescueRefreshEvery)
        value = Clock.System.now()
    }
}.value

// "adesso", "12 min fa", "3 h fa"
@Composable
internal fun ago(then: Instant, now: Instant): String {
    val minutes = (now - then).inWholeMinutes.coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(Res.string.rescue_age_now)
        minutes < 60 -> stringResource(Res.string.rescue_age_minutes, minutes)
        else -> stringResource(Res.string.rescue_age_hours, minutes / 60)
    }
}
