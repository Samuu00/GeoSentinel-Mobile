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
import geosentinel_mobile.shared.generated.resources.my_bookings_short
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
import it.unical.geosentinel_mobile.core.ui.GroupCard
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.GroupIconIndent
import it.unical.geosentinel_mobile.core.ui.GroupRow
import it.unical.geosentinel_mobile.core.ui.HeaderPill
import it.unical.geosentinel_mobile.core.ui.PageHeader
import it.unical.geosentinel_mobile.core.ui.pageBottomInset
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

    val nearMe = (state as? RefugeList.Loaded)?.nearMe
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        // il contenuto scorre sotto la barra a isola fino al bordo
        contentPadding = PaddingValues(bottom = 24.dp + pageBottomInset()),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item(key = "header") {
            PageHeader(
                title = stringResource(Res.string.refuges_title),
                // finché non si sa da dove si parte, niente frase (cambierebbe subito dopo)
                intro = nearMe?.let { stringResource(if (it) Res.string.refuges_intro_near_me else Res.string.refuges_intro_area) },
                // le proprie prenotazioni (M5.2): accanto al titolo, sempre a portata, senza occupare una riga della lista
                trailing = { HeaderPill(stringResource(Res.string.my_bookings_short), Icons.Filled.DateRange, onOpenBookings) },
            )
        }
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
                // un gruppo solo, righe separate da un filo (stile raggruppato): prima una card con il bordo per rifugio
                item(key = "refuges") {
                    GroupCard(Modifier.widthIn(max = ContentMaxWidth).padding(horizontal = 20.dp).padding(top = 12.dp)) {
                        list.refuges.forEachIndexed { index, refuge ->
                            if (index > 0) GroupDivider(GroupIconIndent)
                            RefugeRow(refuge, from = list.from) { onOpenRefuge(refuge.id) }
                        }
                    }
                }
            }
        }
    }
}

// Un rifugio: casetta su un tondo muschio, nome, posti letto e distanza; tutta la riga apre il dettaglio
@Composable
private fun RefugeRow(refuge: RefugeResponse, from: GeoPoint, onClick: () -> Unit) {
    val distance = distanceLabel(from.distanceTo(refuge.location.toGeoPoint()))
    GroupRow(
        title = refuge.name,
        subtitle = pluralStringResource(Res.plurals.refuge_beds, refuge.capacity, refuge.capacity) + " · " +
            stringResource(Res.string.hazards_distance, distance),
        icon = Icons.Filled.Home,
        onClickLabel = stringResource(Res.string.refuge_open),
        onClick = onClick,
    )
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
