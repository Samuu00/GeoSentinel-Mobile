package it.unical.geosentinel_mobile.refuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.refuge_beds
import geosentinel_mobile.shared.generated.resources.refuge_free_beds
import geosentinel_mobile.shared.generated.resources.refuge_full
import geosentinel_mobile.shared.generated.resources.refuge_nights_title
import geosentinel_mobile.shared.generated.resources.refuge_tonight
import geosentinel_mobile.shared.generated.resources.trail_retry
import geosentinel_mobile.shared.generated.resources.weekday_1
import geosentinel_mobile.shared.generated.resources.weekday_2
import geosentinel_mobile.shared.generated.resources.weekday_3
import geosentinel_mobile.shared.generated.resources.weekday_4
import geosentinel_mobile.shared.generated.resources.weekday_5
import geosentinel_mobile.shared.generated.resources.weekday_6
import geosentinel_mobile.shared.generated.resources.weekday_7
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.refuge.data.NightAvailability
import it.unical.geosentinel_mobile.trail.ui.Section
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val ContentMaxWidth = 600.dp

/**
 * Dettaglio di un rifugio (M5.1), spinto dalla scheda Rifugi o dalla mappa: nome, posti letto e i posti liberi delle
 * prossime 14 notti. La prenotazione arriva con M5.2.
 */
@Composable
fun RefugeDetailScreen(refugeId: String, onBack: () -> Unit) {
    val viewModel = koinViewModel<RefugeDetailViewModel> { parametersOf(refugeId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { ForestHeader(Modifier.fillMaxWidth().height(200.dp)) }
            when (val refuge = state.refuge) {
                Section.Loading, Section.Hidden -> item(key = "loading") { CenteredProgress() }
                is Section.Failed -> item(key = "error") { RetryMessage(refuge.message, onRetry = viewModel::retry) }
                is Section.Loaded -> {
                    item(key = "title") {
                        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
                            Text(
                                refuge.value.name,
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                pluralStringResource(Res.plurals.refuge_beds, refuge.value.capacity, refuge.value.capacity),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                stringResource(Res.string.refuge_nights_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 24.dp, bottom = 10.dp).semantics { heading() },
                            )
                        }
                    }
                    when (val nights = state.nights) {
                        Section.Loading, Section.Hidden -> item(key = "nights-loading") { CenteredProgress(small = true) }
                        is Section.Failed -> item(key = "nights-error") { RetryMessage(nights.message, onRetry = viewModel::retry) }
                        is Section.Loaded -> itemsIndexed(nights.value, key = { _, night -> night.date.toString() }) { index, night ->
                            NightRow(night, tonight = index == 0, first = index == 0, last = index == nights.value.lastIndex)
                        }
                    }
                }
            }
        }
        BackButton(onBack)
    }
}

// Una notte del calendario, dentro un'unica card (prima e ultima riga con gli angoli tondi). I posti sono sempre scritti;
// "Pieno" non è rosso (il rosso resta all'emergenza, §2), si distingue dal testo e dal peso
@Composable
private fun NightRow(night: NightAvailability, tonight: Boolean, first: Boolean, last: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(
        topStart = if (first) 20.dp else 0.dp,
        topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp,
        bottomEnd = if (last) 20.dp else 0.dp,
    )
    Column(
        Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(shape)
            .background(colors.surfaceContainerLowest)
            .border(1.dp, colors.outlineVariant, shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { },
        ) {
            Text(
                if (tonight) stringResource(Res.string.refuge_tonight) else "${stringResource(night.date.weekday())} ${night.date.day}/${night.date.month.number}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (night.isFull) stringResource(Res.string.refuge_full) else pluralStringResource(Res.plurals.refuge_free_beds, night.freeBeds, night.freeBeds),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (night.isFull) FontWeight.Normal else FontWeight.SemiBold,
                color = if (night.isFull) colors.onSurfaceVariant else colors.primary,
            )
        }
        if (!last) HorizontalDivider(color = colors.outlineVariant)
    }
}

private fun LocalDate.weekday(): StringResource = when (dayOfWeek.isoDayNumber) {
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
