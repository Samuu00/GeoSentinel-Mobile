// Scheda Allerte degli operatori del soccorso: elenco delle allerte SOS aperte con stato, età e squadra, aggiornato
// periodicamente e a ogni notifica. Contiene anche alcune funzioni condivise con il dettaglio (ora corrente, "da quanto").
package it.unical.geosentinel_mobile.rescue.ui

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
import it.unical.geosentinel_mobile.core.ui.GroupIconIndent
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.groupedItem
import it.unical.geosentinel_mobile.core.ui.HeaderLink
import it.unical.geosentinel_mobile.core.ui.PageHeader
import it.unical.geosentinel_mobile.core.ui.pageBottomInset
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
import it.unical.geosentinel_mobile.core.ui.TapCard
import it.unical.geosentinel_mobile.core.ui.IconTile
import androidx.compose.material3.HorizontalDivider
import it.unical.geosentinel_mobile.core.ui.coordinatesLabel
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.core.ui.SkyStatusBarScrim

/** Quanti elementi prima della fine della lista si chiede la pagina successiva. */
private const val PrefetchDistance = 3

/** Intervallo di rilettura di lista e dettaglio con la schermata aperta, lo stesso dello stato nella schermata SOS. */
internal val RescueRefreshEvery = 30.seconds

/**
 * Scheda Allerte, per `RESCUE_OPERATOR` e `ADMIN`: le allerte aperte, con chi aspetta da più tempo in cima. Per
 * ognuna mostra lo stato a parole, da quanto è aperta e la squadra se c'è; le note mediche compaiono solo nel
 * dettaglio. Si rilegge trascinando in basso, ogni 30 secondi in primo piano e subito a ogni notifica di un nuovo SOS.
 * [onCreateTeam] non è null solo per gli amministratori e mostra il link per creare una squadra.
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
    // Al ritorno in primo piano o dal dettaglio si rilegge subito (alla prima apertura il ViewModel sta già
    // caricando e la chiamata non fa nulla), poi ogni 30 secondi.
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
                        // Link "Nuova squadra di soccorso", solo per gli amministratori.
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
                            AlertCard(alert, now, index) { onOpenAlert(alert.id) }
                        }
                        when {
                            state.loadingMore -> item(key = "more") { CenteredProgress(small = true) }
                            state.error != null -> item(key = "more-error") { InlineError(state.error!!.resolve(), onRetry = viewModel::retry) }
                        }
                    }
                }
            }
        }
        SkyStatusBarScrim(show = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120)
    }
}

/**
 * Card di un'allerta, che entra a cascata secondo [index]. In alto il simbolo della campanella (rosso pieno se manca
 * la squadra), lo stato e da quanto è aperta; sotto una linea sottile, la squadra assegnata o, senza squadra, dove si
 * trova l'escursionista.
 */
@Composable
private fun AlertCard(alert: SosAlertResponse, now: Instant, index: Int, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val waiting = alert.status == SosAlertResponse.Status.ACTIVE
    TapCard(
        onClick = onOpen,
        index = index,
        onClickLabel = stringResource(Res.string.rescue_alert_open),
        modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) { }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(AppIcons.Bell, tint = if (waiting) EmergencyRed else colors.primary, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(alert.status.label()), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.rescue_raised_ago, ago(alert.createdAt, now)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(AppIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                val team = alert.assignedTeam
                Icon(if (team != null) AppIcons.Users else AppIcons.Navigation, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (team != null) stringResource(Res.string.rescue_team_label, "${team.name} (${team.callSign})") else coordinatesLabel(alert.location.lat, alert.location.lon),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Campanella in un tondo rosso: pieno se l'allerta aspetta ancora una squadra ([waiting]), chiaro con il bordo rosso
 * se la squadra è stata inviata, come sulla mappa. È decorativa perché lo stato è scritto accanto.
 */
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
        Icon(AppIcons.Bell, contentDescription = null, tint = if (waiting) Color.White else EmergencyRed, modifier = Modifier.size(22.dp))
    }
}

/** Indicatore di caricamento centrato; [small] per quello in fondo alla lista. */
@Composable
private fun CenteredProgress(small: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = if (small) 12.dp else 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(if (small) 24.dp else 36.dp), strokeWidth = if (small) 2.dp else 3.dp)
    }
}

/** Messaggio per lo stato vuoto o d'errore: testo centrato, titolo facoltativo e al massimo un'azione. */
@Composable
private fun Message(text: String, title: String? = null, isError: Boolean = false, action: (@Composable () -> Unit)? = null) {
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
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/** Errore in fondo alla lista, con il pulsante "Riprova". */
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

/** Ora corrente aggiornata ogni 30 secondi, così i testi "da quanto" avanzano anche quando i dati non cambiano. */
@Composable
internal fun rememberNow(): Instant = produceState(Clock.System.now()) {
    while (true) {
        delay(RescueRefreshEvery)
        value = Clock.System.now()
    }
}.value

/** Tempo trascorso da [then] a [now] in forma breve: "adesso", poi i minuti, poi le ore. */
@Composable
internal fun ago(then: Instant, now: Instant): String {
    val minutes = (now - then).inWholeMinutes.coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(Res.string.rescue_age_now)
        minutes < 60 -> stringResource(Res.string.rescue_age_minutes, minutes)
        else -> stringResource(Res.string.rescue_age_hours, minutes / 60)
    }
}
