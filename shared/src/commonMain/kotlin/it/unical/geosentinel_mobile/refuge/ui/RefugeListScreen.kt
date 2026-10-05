package it.unical.geosentinel_mobile.refuge.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.hazards_distance
import geosentinel_mobile.shared.generated.resources.my_bookings_open
import geosentinel_mobile.shared.generated.resources.refuge_beds
import geosentinel_mobile.shared.generated.resources.refuge_open
import geosentinel_mobile.shared.generated.resources.refuges_empty
import geosentinel_mobile.shared.generated.resources.refuges_intro_area
import geosentinel_mobile.shared.generated.resources.refuges_intro_near_me
import geosentinel_mobile.shared.generated.resources.refuges_title
import geosentinel_mobile.shared.generated.resources.trail_retry
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.distanceTo
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.hazard.ui.distanceLabel
import it.unical.geosentinel_mobile.trail.ui.DefaultArea
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberLocationState
import kotlin.time.Duration.Companion.seconds

private val ContentMaxWidth = 600.dp
private val CardShape = RoundedCornerShape(20.dp)
// Quanto si aspetta la posizione (con il permesso già dato) prima di ripiegare sull'area di partenza
private val FixWait = 3.seconds

/**
 * Scheda Rifugi (M5.1): i rifugi entro 50 km, dal più vicino. Con il permesso della posizione già dato si parte da lì;
 * altrimenti dall'area di partenza della mappa, senza chiedere il permesso (lo chiede la mappa, quando serve).
 */
@Composable
fun RefugeListScreen(onOpenRefuge: (String) -> Unit, onOpenBookings: () -> Unit) {
    val viewModel = koinViewModel<RefugeListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val location = rememberLocationState()

    // a ogni ingresso nella scheda: i posti e la posizione possono essere cambiati
    LaunchedEffect(location.permission is LocationPermission.Granted) {
        val fix = if (location.permission is LocationPermission.Granted) {
            withTimeoutOrNull(FixWait) { snapshotFlow { location.lastLocation }.filterNotNull().first() }
        } else {
            null
        }
        if (fix != null) viewModel.load(fix.position.toGeoPoint(), nearMe = true) else viewModel.load(DefaultArea, nearMe = false)
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item(key = "header") { ForestHeader(Modifier.fillMaxWidth().height(200.dp)) }
        item(key = "title") { Title(nearMe = (state as? RefugeList.Loaded)?.nearMe) }
        // le proprie prenotazioni stanno qui, prima della lista (M5.2)
        item(key = "bookings") { BookingsLink(onOpenBookings) }
        when (val list = state) {
            RefugeList.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                }
            }
            is RefugeList.Failed -> item(key = "error") {
                Message(list.message.resolve(), isError = true) {
                    PrimaryPillButton(stringResource(Res.string.trail_retry), onClick = viewModel::retry)
                }
            }
            is RefugeList.Loaded -> if (list.refuges.isEmpty()) {
                item(key = "empty") { Message(stringResource(Res.string.refuges_empty)) }
            } else {
                items(list.refuges, key = { it.id }) { refuge -> RefugeCard(refuge, from = list.from) { onOpenRefuge(refuge.id) } }
            }
        }
    }
}

@Composable
private fun Title(nearMe: Boolean?) {
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp)) {
        Text(
            stringResource(Res.string.refuges_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        // finché non si sa da dove si parte, niente frase (cambierebbe subito dopo)
        if (nearMe != null) {
            Text(
                stringResource(if (nearMe) Res.string.refuges_intro_near_me else Res.string.refuges_intro_area),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// Un rifugio: nome, posti letto e distanza; tutta la card apre il dettaglio
@Composable
private fun RefugeCard(refuge: RefugeResponse, from: GeoPoint, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val distance = distanceLabel(from.distanceTo(refuge.location.toGeoPoint()))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(CardShape)
            .background(colors.surfaceContainerLowest)
            .border(1.dp, colors.outlineVariant, CardShape)
            .clickable(onClickLabel = stringResource(Res.string.refuge_open), role = Role.Button, onClick = onClick)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        // decorativa: il nome dice già che è un rifugio
        Icon(Icons.Filled.Home, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(refuge.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                pluralStringResource(Res.plurals.refuge_beds, refuge.capacity, refuge.capacity) + " · " +
                    stringResource(Res.string.hazards_distance, distance),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

// "Le mie prenotazioni": una card come quelle dei rifugi, in evidenza nel verde del tema
@Composable
private fun BookingsLink(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(CardShape)
            .background(colors.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // decorativa: il testo dice già cosa apre
        Icon(Icons.Filled.DateRange, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(Res.string.my_bookings_open),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onPrimaryContainer)
    }
}

// Stato vuoto o d'errore: testo centrato e, se c'è, una sola azione
@Composable
private fun Message(text: String, isError: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
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
