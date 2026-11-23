// Schermata SOS: pulsante da tenere premuto per lanciare l'allerta, note mediche, stato della richiesta, posizione
// attuale, annullamento e pulsante "Chiama 112". Usa sempre il tema scuro d'emergenza.
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
import androidx.compose.ui.unit.sp
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import it.unical.geosentinel_mobile.core.ui.coordinatesLabel
import geosentinel_mobile.shared.generated.resources.sos_position_title
import geosentinel_mobile.shared.generated.resources.sos_position_waiting
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.core.ui.rememberReduceMotion
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyTheme
import it.unical.geosentinel_mobile.core.ui.theme.StatusBarOverDark
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

/** Attesa massima della posizione al momento del lancio; se il sistema ce l'ha, si usa subito l'ultima posizione nota. */
private val FixTimeout = 10.seconds
/** Tempo senza risposta dopo il quale "Chiama 112" passa in primo piano. */
private val LateAfter = 30.seconds
/** Intervallo di rilettura dello stato con la schermata aperta, utile anche quando le notifiche push non arrivano. */
private val RefreshEvery = 30.seconds
/** Durata in millisecondi della pressione necessaria per lanciare l'SOS. */
private const val HoldMillis = 3_000
/** Numero unico europeo di emergenza. */
private const val EmergencyNumber = "112"

/**
 * Schermata SOS. Non ha il paesaggio in testata, perché qui conta solo lo stato, che dice sempre se il soccorso ha
 * ricevuto la richiesta. "Chiama 112" è sempre visibile e passa in primo piano (pieno e rosso) quando l'app da sola
 * non basta: utente ospite o con sessione scaduta, invio senza risposta dopo 30 secondi, risposta negativa,
 * posizione non disponibile. [onLogin] apre il login per l'ospite.
 */
@Composable
fun SosScreen(onBack: () -> Unit, onLogin: () -> Unit) {
    val viewModel = koinViewModel<SosViewModel>()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cancel by viewModel.cancel.collectAsStateWithLifecycle()
    val dial = rememberDialer()
    val loggedIn = session is SessionState.LoggedIn

    // Il permesso della posizione si chiede qui, quando serve davvero; intanto la posizione comincia ad arrivare,
    // così al momento del lancio è già disponibile.
    val location = rememberLocationState()
    LaunchedEffect(loggedIn) {
        val permission = location.permission
        if (loggedIn && permission is LocationPermission.NotGranted && permission.canRequest != false) location.requestPermission()
    }
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var noLocation by remember { mutableStateOf(false) }
    // remember e non rememberSaveable: le note mediche non devono finire nello stato salvato dell'Activity, anche a
    // costo di perderle ruotando lo schermo. Si svuotano all'invio, perché da quel momento stanno nell'outbox finché
    // il server non risponde.
    var notes by remember { mutableStateOf("") }
    var confirmCancel by remember { mutableStateOf(false) }

    // Vero se l'invio è partito da più di 30 secondi (anche prima di un riavvio) ed è ancora senza risposta.
    var late by remember { mutableStateOf(false) }
    val since = (state as? SosState.Sending)?.since
    LaunchedEffect(since) {
        late = false
        if (since != null) {
            delay(LateAfter - (Clock.System.now() - since))
            late = true
        }
    }

    // La posizione la invia App (SosLocationUpdates); qui si rilegge solo lo stato, per vedere la squadra assegnata
    // anche se la notifica push non arriva.
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

    // La schermata d'emergenza è sempre scura, con un alone rosso dietro al pulsante.
    StatusBarOverDark(true)
    EmergencyTheme {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(EmergencyRed.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width / 2, size.height * 0.42f),
                        radius = size.width * 0.9f,
                    ),
                )
            },
    ) {
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
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() },
                )
                // Regione "live": TalkBack e VoiceOver annunciano lo stato da soli quando cambia.
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
                                // Prima il permesso, se manca e si può ancora chiedere; poi la posizione (o l'ultima
                                // nota).
                                val permission = location.permission
                                if (permission is LocationPermission.NotGranted && permission.canRequest != false) location.requestPermission()
                                val fix = withTimeoutOrNull(FixTimeout) { snapshotFlow { location.lastLocation }.filterNotNull().first() }
                                locating = false
                                if (fix == null) noLocation = true else viewModel.raise(fix.position.toGeoPoint(), notes).also { notes = "" }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    if (locating) Body(stringResource(Res.string.sos_locating), Modifier.align(Alignment.CenterHorizontally))
                    if (noLocation) FormErrorBanner(stringResource(Res.string.sos_no_location))
                }

                // Da loggati, prima di "Chiama 112", la posizione come la riceverà il soccorso.
                if (loggedIn) PositionCard(location.lastLocation?.position?.let { coordinatesLabel(it.latitude, it.longitude) })
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

/** Card con la posizione attuale: un mirino e le coordinate, oppure l'attesa del GPS se [coordinates] è null. */
@Composable
private fun PositionCard(coordinates: String?) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Locate, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(Res.string.sos_position_title), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(
                    coordinates ?: stringResource(Res.string.sos_position_waiting),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Testo normale della schermata, nel colore secondario. */
@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Titolo dello stato dell'SOS. */
@Composable
private fun StatusTitle(text: String) {
    // Dimensione fissa di 26 sp: la schermata SOS non usa la scala ridotta dei titoli del resto dell'app.
    Text(text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 30.sp), fontWeight = FontWeight.SemiBold)
}

/**
 * Stato "in invio": spiegazione, numero di tentativi, avviso se la richiesta è scaduta ([expired]) e avviso se
 * tarda la risposta ([late]).
 */
@Composable
private fun SendingStatus(state: SosState.Sending, late: Boolean, expired: Boolean) {
    StatusTitle(stringResource(Res.string.sos_sending_title))
    Body(stringResource(Res.string.sos_sending_body))
    if (state.attempts > 0) Body(stringResource(Res.string.sos_sending_attempts, state.attempts))
    if (expired) Body(stringResource(Res.string.sos_expired))
    if (late) FormErrorBanner(stringResource(Res.string.sos_sending_late))
}

/** Stato "ricevuto": se è già stata inviata una squadra ne mostra nome e nominativo, poi i consigli per l'attesa. */
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

/**
 * Pulsante "Chiama 112", alto 72 dp come le altre azioni d'emergenza. Con [prominent] è pieno e rosso, altrimenti ha
 * solo il bordo rosso. Apre il compositore del telefono con il numero: la chiamata la avvia l'utente.
 */
@Composable
private fun Call112Button(prominent: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    val label: @Composable () -> Unit = {
        // Icona decorativa: l'azione è scritta accanto.
        Icon(AppIcons.Phone, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(Res.string.sos_call_112), style = MaterialTheme.typography.titleLarge)
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = modifier,
        ) { label() }
    }
}

/**
 * Pulsante dell'SOS: va tenuto premuto per 3 secondi mentre un anello si riempie, così non parte per sbaglio in tasca.
 * Lasciandolo prima, l'anello torna indietro. Con TalkBack e VoiceOver basta l'azione "tieni premuto" del lettore di
 * schermo, che è già un gesto deliberato. [onTriggered] viene chiamata a pressione completata.
 */
@Composable
private fun HoldToSendButton(enabled: Boolean, onTriggered: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val description = stringResource(Res.string.sos_hold_description)
    val still = rememberReduceMotion()
    // Due aloni che si allargano e svaniscono finché il pulsante è in attesa, per far vedere dove toccare.
    val wave = if (still || !enabled) {
        0f
    } else {
        val breathing by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        )
        breathing
    }
    Box(
        modifier
            .size(260.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
                if (enabled) onLongClick(description) { onTriggered(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Si lancia nello scope della composizione e non in quello del gesto: se a fine corsa il pulsante
                    // si disattiva, il lancio non viene annullato.
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
                val stroke = 6.dp.toPx()
                val disc = size.minDimension / 2 - 22.dp.toPx()
                // Aloni: due onde sfasate di mezzo ciclo, che vanno dal bordo del disco fino al bordo del pulsante.
                if (progress.value == 0f) {
                    listOf(wave, (wave + 0.5f) % 1f).forEach { t ->
                        drawCircle(EmergencyRed.copy(alpha = 0.35f * (1f - t)), radius = disc + (size.minDimension / 2 - disc) * t)
                    }
                }
                drawCircle(EmergencyRed, radius = disc)
                // Anello bianco dentro il bordo del disco: appena accennato a riposo, si riempie man mano che si tiene
                // premuto.
                val ring = disc - stroke * 1.5f
                val topLeft = Offset(center.x - ring, center.y - ring)
                val arcSize = Size(ring * 2, ring * 2)
                drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(Color.White, -90f, 360f * progress.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.sos_button), style = MaterialTheme.typography.displayMedium, letterSpacing = 4.sp, color = EmergencyOnRed)
            Text(stringResource(Res.string.sos_hold), style = MaterialTheme.typography.labelLarge, color = EmergencyOnRed)
        }
    }
}
