package it.unical.geosentinel_mobile.sos.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.sos_button
import geosentinel_mobile.shared.generated.resources.sos_call_112
import geosentinel_mobile.shared.generated.resources.sos_cancel
import geosentinel_mobile.shared.generated.resources.sos_cancel_confirm
import geosentinel_mobile.shared.generated.resources.sos_cancel_dialog_body
import geosentinel_mobile.shared.generated.resources.sos_cancel_dialog_title
import geosentinel_mobile.shared.generated.resources.sos_cancel_dismiss
import geosentinel_mobile.shared.generated.resources.sos_dispatched_team
import geosentinel_mobile.shared.generated.resources.sos_dispatched_title
import geosentinel_mobile.shared.generated.resources.sos_expired
import geosentinel_mobile.shared.generated.resources.sos_failed
import geosentinel_mobile.shared.generated.resources.sos_guest
import geosentinel_mobile.shared.generated.resources.sos_hold
import geosentinel_mobile.shared.generated.resources.sos_hold_description
import geosentinel_mobile.shared.generated.resources.sos_intro
import geosentinel_mobile.shared.generated.resources.sos_keep_open
import geosentinel_mobile.shared.generated.resources.sos_locating
import geosentinel_mobile.shared.generated.resources.sos_login
import geosentinel_mobile.shared.generated.resources.sos_login_question
import geosentinel_mobile.shared.generated.resources.sos_no_location
import geosentinel_mobile.shared.generated.resources.sos_notes_hint
import geosentinel_mobile.shared.generated.resources.sos_notes_label
import geosentinel_mobile.shared.generated.resources.sos_raised_body
import geosentinel_mobile.shared.generated.resources.sos_raised_title
import geosentinel_mobile.shared.generated.resources.sos_sending_attempts
import geosentinel_mobile.shared.generated.resources.sos_sending_body
import geosentinel_mobile.shared.generated.resources.sos_sending_late
import geosentinel_mobile.shared.generated.resources.sos_sending_title
import geosentinel_mobile.shared.generated.resources.sos_title
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.FooterLink
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.rememberDialer
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyOnRed
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import it.unical.geosentinel_mobile.sos.data.MEDICAL_NOTES_MAX_LENGTH
import it.unical.geosentinel_mobile.sos.data.SosState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberLocationState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

// Il fix si aspetta al massimo 10 s (M6.1); il primo è l'ultima posizione nota, se il sistema ce l'ha
private val FixTimeout = 10.seconds
// Senza risposta dopo 30 s "Chiama 112" passa in primo piano
private val LateAfter = 30.seconds
// Finché mancano le push (M7) lo stato si rilegge ogni 30 s con la schermata aperta (M6.2)
private val RefreshEvery = 30.seconds
private const val HoldMillis = 3_000
private const val EmergencyNumber = "112"

/**
 * SOS (M6.1, stato aggiornato in M6.2). Niente paesaggio in testata: qui conta solo lo stato. Lo stato dice sempre se il soccorso ha la
 * richiesta; "Chiama 112" è sempre visibile e passa in primo piano (pieno, rosso) quando l'app non basta: ospite,
 * sessione scaduta, invio senza risposta dopo 30 s, risposta negativa, posizione assente.
 */
@Composable
fun SosScreen(onBack: () -> Unit, onLogin: () -> Unit) {
    val viewModel = koinViewModel<SosViewModel>()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cancel by viewModel.cancel.collectAsStateWithLifecycle()
    val dial = rememberDialer()
    val loggedIn = session is SessionState.LoggedIn

    // il permesso si chiede qui, quando serve davvero; la posizione intanto arriva, così al tocco c'è già un fix
    val location = rememberLocationState()
    LaunchedEffect(loggedIn) {
        val permission = location.permission
        if (loggedIn && permission is LocationPermission.NotGranted && permission.canRequest != false) location.requestPermission()
    }
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var noLocation by remember { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmCancel by remember { mutableStateOf(false) }

    // invio partito da più di 30 s (anche prima di un riavvio) e ancora senza risposta
    var late by remember { mutableStateOf(false) }
    val since = (state as? SosState.Sending)?.since
    LaunchedEffect(since) {
        late = false
        if (since != null) {
            delay(LateAfter - (Clock.System.now() - since))
            late = true
        }
    }

    // la posizione la manda App (SosLocationUpdates); qui si rilegge lo stato, per vedere la squadra anche senza push
    val raised = state is SosState.Raised
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(raised, lifecycleOwner) {
        if (!raised) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(RefreshEvery)
                viewModel.refresh()
            }
        }
    }

    val callFirst = !loggedIn || late || noLocation || state is SosState.Failed

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp)) // sotto il pulsante Indietro
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(Res.string.sos_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                // lo stato si annuncia da solo a TalkBack e VoiceOver quando cambia
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (val current = state) {
                        is SosState.Sending -> SendingStatus(current, late, expired = session is SessionState.Expired)
                        is SosState.Raised -> RaisedStatus(current.alert)
                        else -> when {
                            !loggedIn -> Body(stringResource(if (session is SessionState.Expired) Res.string.sos_expired else Res.string.sos_guest))
                            else -> {
                                if (current is SosState.Failed) FormErrorBanner(stringResource(Res.string.sos_failed))
                                Body(stringResource(Res.string.sos_intro))
                            }
                        }
                    }
                }

                val canRaise = loggedIn && (state is SosState.Idle || state is SosState.Failed)
                if (canRaise) {
                    FormTextField(
                        value = notes,
                        onValueChange = { notes = it.take(MEDICAL_NOTES_MAX_LENGTH) },
                        label = stringResource(Res.string.sos_notes_label),
                        supporting = stringResource(Res.string.sos_notes_hint),
                        singleLine = false,
                        minLines = 2,
                    )
                    HoldToSendButton(
                        enabled = !locating,
                        onTriggered = {
                            noLocation = false
                            locating = true
                            scope.launch {
                                // prima il permesso, se manca e si può ancora chiedere; poi il fix (o l'ultimo noto)
                                val permission = location.permission
                                if (permission is LocationPermission.NotGranted && permission.canRequest != false) location.requestPermission()
                                val fix = withTimeoutOrNull(FixTimeout) { snapshotFlow { location.lastLocation }.filterNotNull().first() }
                                locating = false
                                if (fix == null) noLocation = true else viewModel.raise(fix.position.toGeoPoint(), notes)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    if (locating) Body(stringResource(Res.string.sos_locating), Modifier.align(Alignment.CenterHorizontally))
                    if (noLocation) FormErrorBanner(stringResource(Res.string.sos_no_location))
                }

                Call112Button(prominent = callFirst, onClick = { dial(EmergencyNumber) })

                if (!loggedIn) FooterLink(Res.string.sos_login_question, Res.string.sos_login, onLogin)

                if (state is SosState.Sending || state is SosState.Raised) {
                    FormErrorBanner(cancel.error?.resolve())
                    TextButton(
                        onClick = { confirmCancel = true },
                        enabled = !cancel.running,
                        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(Res.string.sos_cancel), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).height(24.dp))
            }
        }
        BackButton(onBack, Modifier.align(Alignment.TopStart))
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(Res.string.sos_cancel_dialog_title)) },
            text = { Text(stringResource(Res.string.sos_cancel_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; viewModel.cancelSos() }) { Text(stringResource(Res.string.sos_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(Res.string.sos_cancel_dismiss)) }
            },
        )
    }
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun StatusTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SendingStatus(state: SosState.Sending, late: Boolean, expired: Boolean) {
    StatusTitle(stringResource(Res.string.sos_sending_title))
    Body(stringResource(Res.string.sos_sending_body))
    if (state.attempts > 0) Body(stringResource(Res.string.sos_sending_attempts, state.attempts))
    if (expired) Body(stringResource(Res.string.sos_expired))
    if (late) FormErrorBanner(stringResource(Res.string.sos_sending_late))
}

@Composable
private fun RaisedStatus(alert: SosAlertResponse) {
    val team = alert.assignedTeam
    if (alert.status == SosAlertResponse.Status.DISPATCHED && team != null) {
        StatusTitle(stringResource(Res.string.sos_dispatched_title))
        Body(stringResource(Res.string.sos_dispatched_team, "${team.name} (${team.callSign})"))
    } else {
        StatusTitle(stringResource(Res.string.sos_raised_title))
    }
    Body(stringResource(Res.string.sos_raised_body))
    Body(stringResource(Res.string.sos_keep_open))
}

// "Chiama 112": 72 dp (azione d'emergenza, §2). In primo piano pieno e rosso, altrimenti con il solo bordo rosso.
// Apre il compositore con il numero, la chiamata la fa l'utente.
@Composable
private fun Call112Button(prominent: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    val label: @Composable () -> Unit = {
        Text(stringResource(Res.string.sos_call_112), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    if (prominent) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = EmergencyOnRed),
            modifier = modifier,
        ) { label() }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = CircleShape,
            border = BorderStroke(2.dp, EmergencyRed),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = modifier,
        ) { label() }
    }
}

/**
 * Il pulsante dell'SOS: si tiene premuto 3 s mentre un anello si riempie, così non parte dalla tasca. Lasciando prima
 * l'anello torna indietro. Con TalkBack e VoiceOver basta l'azione "tieni premuto" del lettore, già deliberata.
 */
@Composable
private fun HoldToSendButton(enabled: Boolean, onTriggered: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val description = stringResource(Res.string.sos_hold_description)
    Box(
        modifier
            .size(200.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
                if (enabled) onLongClick(description) { onTriggered(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // nello scope della composizione, non del gesto: se il pulsante si disabilita a fine corsa non si annulla
                    val hold = scope.launch {
                        val remaining = (HoldMillis * (1f - progress.value)).toInt().coerceAtLeast(1)
                        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTriggered()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    if (hold.isActive) {
                        hold.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                })
            }
            .drawBehind {
                val stroke = 10.dp.toPx()
                drawCircle(EmergencyRed, radius = size.minDimension / 2 - stroke * 1.5f)
                // l'anello attorno: prima appena accennato, poi pieno man mano che si tiene premuto
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(EmergencyRed.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(EmergencyRed, -90f, 360f * progress.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.sos_button), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = EmergencyOnRed)
            Text(stringResource(Res.string.sos_hold), style = MaterialTheme.typography.labelLarge, color = EmergencyOnRed)
        }
    }
}
