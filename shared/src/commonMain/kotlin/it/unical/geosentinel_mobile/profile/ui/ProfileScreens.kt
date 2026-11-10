package it.unical.geosentinel_mobile.profile.ui

import it.unical.geosentinel_mobile.core.ui.PageMaxWidth
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.my_hazards_title
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.auth_no_account
import geosentinel_mobile.shared.generated.resources.auth_password_rules
import geosentinel_mobile.shared.generated.resources.auth_register_link
import geosentinel_mobile.shared.generated.resources.change_password_body
import geosentinel_mobile.shared.generated.resources.change_password_button
import geosentinel_mobile.shared.generated.resources.change_password_current
import geosentinel_mobile.shared.generated.resources.change_password_mfa
import geosentinel_mobile.shared.generated.resources.change_password_mfa_hint
import geosentinel_mobile.shared.generated.resources.change_password_new
import geosentinel_mobile.shared.generated.resources.change_password_title
import geosentinel_mobile.shared.generated.resources.delete_confirm_body
import geosentinel_mobile.shared.generated.resources.delete_confirm_button
import geosentinel_mobile.shared.generated.resources.delete_confirm_title
import geosentinel_mobile.shared.generated.resources.delete_dialog_body
import geosentinel_mobile.shared.generated.resources.delete_dialog_continue
import geosentinel_mobile.shared.generated.resources.delete_dialog_title
import geosentinel_mobile.shared.generated.resources.edit_name_body
import geosentinel_mobile.shared.generated.resources.edit_name_button
import geosentinel_mobile.shared.generated.resources.edit_name_title
import geosentinel_mobile.shared.generated.resources.profile_account_section
import geosentinel_mobile.shared.generated.resources.profile_activity_section
import geosentinel_mobile.shared.generated.resources.profile_change_password
import geosentinel_mobile.shared.generated.resources.profile_delete_account
import geosentinel_mobile.shared.generated.resources.profile_deleting
import geosentinel_mobile.shared.generated.resources.profile_guest_body
import geosentinel_mobile.shared.generated.resources.profile_guest_title
import geosentinel_mobile.shared.generated.resources.profile_login_email
import geosentinel_mobile.shared.generated.resources.profile_logout
import geosentinel_mobile.shared.generated.resources.profile_mfa
import geosentinel_mobile.shared.generated.resources.profile_mfa_off
import geosentinel_mobile.shared.generated.resources.profile_mfa_on
import geosentinel_mobile.shared.generated.resources.profile_photo
import geosentinel_mobile.shared.generated.resources.profile_photo_choose
import geosentinel_mobile.shared.generated.resources.profile_photo_remove
import geosentinel_mobile.shared.generated.resources.profile_logout_anyway
import geosentinel_mobile.shared.generated.resources.profile_logout_sos_raised
import geosentinel_mobile.shared.generated.resources.profile_logout_sos_sending
import geosentinel_mobile.shared.generated.resources.profile_logout_sos_title
import geosentinel_mobile.shared.generated.resources.profile_logout_stay
import geosentinel_mobile.shared.generated.resources.profile_name
import geosentinel_mobile.shared.generated.resources.profile_session_expired
import geosentinel_mobile.shared.generated.resources.profile_set_password
import geosentinel_mobile.shared.generated.resources.role_admin
import geosentinel_mobile.shared.generated.resources.role_guest
import geosentinel_mobile.shared.generated.resources.role_hiker
import geosentinel_mobile.shared.generated.resources.role_park_manager
import geosentinel_mobile.shared.generated.resources.role_rescue_operator
import geosentinel_mobile.shared.generated.resources.theme_dark
import geosentinel_mobile.shared.generated.resources.theme_light
import geosentinel_mobile.shared.generated.resources.theme_section
import geosentinel_mobile.shared.generated.resources.theme_system
import geosentinel_mobile.shared.generated.resources.theme_title
import it.unical.geosentinel_mobile.auth.ui.ContinueWithGoogle
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.session.UserProfile
import it.unical.geosentinel_mobile.media.data.ImageEncoder
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.media.ui.rememberPhotoPicker
import it.unical.geosentinel_mobile.media.ui.rememberSignedUrl
import kotlinx.coroutines.launch
import it.unical.geosentinel_mobile.core.session.UserRole
import it.unical.geosentinel_mobile.core.session.role
import it.unical.geosentinel_mobile.core.ui.FooterLink
import it.unical.geosentinel_mobile.core.ui.GroupCard
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.GroupHeader
import it.unical.geosentinel_mobile.core.ui.GroupIconIndent
import it.unical.geosentinel_mobile.core.ui.GroupRow
import it.unical.geosentinel_mobile.core.ui.PageHeaderHeight
import it.unical.geosentinel_mobile.core.ui.pageBottomInset
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PasswordField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.theme.ThemeMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import it.unical.geosentinel_mobile.sos.data.SosState
import it.unical.geosentinel_mobile.sos.ui.SosController
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


// Passi della cancellazione: prima cosa succede, poi la conferma definitiva (doppia conferma, §M2.3)
private const val DeleteNone = 0
private const val DeleteExplain = 1
private const val DeleteConfirm = 2

@Composable
fun ProfileScreen(
    session: SessionState,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onEditName: () -> Unit,
    onChangePassword: () -> Unit,
    onSetPassword: (email: String) -> Unit,
    onMfa: () -> Unit,
    onMyHazards: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    val viewModel = koinViewModel<ProfileViewModel>()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var deleteStep by rememberSaveable { mutableIntStateOf(DeleteNone) }
    val profile = (session as? SessionState.LoggedIn)?.profile
    // Con un SOS in corso uscire ha conseguenze: si chiede prima (in invio: la richiesta smette di partire e si toglie
    // dal telefono; attivo: l'allerta resta ma la posizione non si aggiorna più)
    val sosState by koinInject<SosController>().state.collectAsStateWithLifecycle()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val onLogout = {
        if (sosState is SosState.Sending || sosState is SosState.Raised) confirmLogout = true else viewModel.logout()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ForestHeader(Modifier.fillMaxWidth().height(PageHeaderHeight))
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
            if (session is SessionState.LoggedIn) {
                // Chi sei: iniziale e nome, sotto ruolo ed email
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val encoder = koinInject<ImageEncoder>()
                    // quadrata da 512 px: il cerchio la mostra a 56 dp, anche sugli schermi più densi
                    val pickPhoto = rememberPhotoPicker { picked ->
                        viewModel.uploadAvatar { encoder.toJpeg(picked, maxSidePx = AvatarSidePx, squareCrop = true) }
                    }
                    ProfileAvatar(profile, busy = ui.avatarBusy, onPick = pickPhoto, onRemove = viewModel::removeAvatar)
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = profile?.displayName ?: stringResource(session.role.label()),
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        // senza profilo (avvio offline) niente sottotitolo
                        if (profile != null) {
                            Text(
                                stringResource(profile.role.label()) + " · " + profile.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Gruppi nell'ordine d'uso: quello che hai fatto, chi sei, come appare l'app; uscire e cancellarsi in fondo
                GroupHeader(stringResource(Res.string.profile_activity_section))
                GroupCard {
                    GroupRow(stringResource(Res.string.my_hazards_title), onClick = onMyHazards, icon = Icons.Filled.Warning)
                }
                GroupHeader(stringResource(Res.string.profile_account_section))
                GroupCard {
                    // Senza profilo (avvio offline) il nome non si può modificare: non sappiamo quale sia
                    GroupRow(
                        title = stringResource(Res.string.profile_name),
                        value = profile?.displayName,
                        onClick = onEditName,
                        enabled = profile != null,
                        icon = Icons.Filled.Person,
                    )
                    GroupDivider(GroupIconIndent)
                    // Account aperto con Google: non c'è una password da cambiare (il server darebbe 422), si imposta via email
                    if (profile?.hasPassword == false) {
                        GroupRow(stringResource(Res.string.profile_set_password), onClick = { onSetPassword(profile.email) }, icon = Icons.Filled.Lock)
                    } else {
                        GroupRow(stringResource(Res.string.profile_change_password), onClick = onChangePassword, icon = Icons.Filled.Lock)
                    }
                    GroupDivider(GroupIconIndent)
                    // senza profilo non si sa se è attiva: la riga aspetta
                    GroupRow(
                        title = stringResource(Res.string.profile_mfa),
                        value = profile?.let { stringResource(if (it.mfaEnabled) Res.string.profile_mfa_on else Res.string.profile_mfa_off) },
                        onClick = onMfa,
                        enabled = profile != null,
                        icon = Icons.Filled.CheckCircle,
                    )
                }
            } else {
                // Ospite (o sessione scaduta): un solo blocco con le due strade per entrare, una sopra l'altra, e la
                // registrazione come alternativa sotto. Come l'accesso all'account nelle Impostazioni di iOS.
                SignInCard(expired = session is SessionState.Expired, onLogin = onLogin, onRegister = onRegister)
            }

            GroupHeader(stringResource(Res.string.theme_section))
            GroupCard {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.theme_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    ThemeSwitch(themeMode, onThemeModeSelected)
                }
            }

            if (session is SessionState.LoggedIn) {
                // Uscire e cancellarsi: in fondo, insieme, come nelle impostazioni di sistema. "Esci" è un'uscita, non un
                // errore (verde); "Elimina account" nel colore degli errori (mai il rosso dell'SOS) e con due conferme
                Spacer(Modifier.size(24.dp))
                GroupCard {
                    GroupRow(stringResource(Res.string.profile_logout), onClick = onLogout, color = MaterialTheme.colorScheme.primary, chevron = false)
                    GroupDivider()
                    GroupRow(
                        title = stringResource(if (ui.deleting) Res.string.profile_deleting else Res.string.profile_delete_account),
                        onClick = { deleteStep = DeleteExplain },
                        enabled = !ui.deleting,
                        color = MaterialTheme.colorScheme.error,
                        chevron = false,
                    )
                }
                // es. personale: "si cancellano tramite l'amministratore"
                ui.error?.let {
                    Spacer(Modifier.size(12.dp))
                    FormErrorBanner(it.resolve())
                }
            }

            // il contenuto scorre sotto la barra a isola
            Spacer(Modifier.size(32.dp + pageBottomInset()))
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(Res.string.profile_logout_sos_title)) },
            text = {
                Text(stringResource(if (sosState is SosState.Sending) Res.string.profile_logout_sos_sending else Res.string.profile_logout_sos_raised))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout()
                }) { Text(stringResource(Res.string.profile_logout_anyway)) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(Res.string.profile_logout_stay)) } },
        )
    }

    when (deleteStep) {
        DeleteExplain -> AlertDialog(
            onDismissRequest = { deleteStep = DeleteNone },
            title = { Text(stringResource(Res.string.delete_dialog_title)) },
            text = { Text(stringResource(Res.string.delete_dialog_body)) },
            confirmButton = { TextButton(onClick = { deleteStep = DeleteConfirm }) { Text(stringResource(Res.string.delete_dialog_continue)) } },
            dismissButton = { TextButton(onClick = { deleteStep = DeleteNone }) { Text(stringResource(Res.string.auth_cancel)) } },
        )
        DeleteConfirm -> AlertDialog(
            onDismissRequest = { deleteStep = DeleteNone },
            title = { Text(stringResource(Res.string.delete_confirm_title)) },
            text = { Text(stringResource(Res.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteStep = DeleteNone
                        viewModel.deleteAccount()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.delete_confirm_button), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { deleteStep = DeleteNone }) { Text(stringResource(Res.string.auth_cancel)) } },
        )
    }
}

// Accesso dal Profilo: sagoma, titolo, cosa si guadagna, poi "Accedi con email" e "Continua con Google" (quest'ultimo
// solo se configurato) della stessa larghezza e altezza, e "Registrati" come link sotto
@Composable
private fun SignInCard(expired: Boolean, onLogin: () -> Unit, onRegister: () -> Unit) {
    GroupCard(Modifier.padding(top = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Monogram(null, size = 72.dp)
            Spacer(Modifier.size(16.dp))
            Text(
                stringResource(Res.string.profile_guest_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(if (expired) Res.string.profile_session_expired else Res.string.profile_guest_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(24.dp))
            PrimaryPillButton(stringResource(Res.string.profile_login_email), onClick = onLogin)
            ContinueWithGoogle(Modifier.padding(top = 12.dp))
            Spacer(Modifier.size(4.dp))
            FooterLink(Res.string.auth_no_account, Res.string.auth_register_link, onRegister)
        }
    }
}

// ---- Nome visualizzato ---------------------------------------------------------------------------------------

@Composable
fun EditNameScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val viewModel = koinViewModel<EditNameViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    FormScaffold(
        onBack = onBack,
        title = Res.string.edit_name_title,
        body = Res.string.edit_name_body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(
                text = stringResource(Res.string.edit_name_button),
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                loading = state.submitting,
            )
            Spacer(Modifier.size(12.dp))
        },
    ) {
        FormTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = stringResource(Res.string.profile_name),
            error = state.fieldError?.resolve(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
        )
    }
}

// ---- Cambio password -----------------------------------------------------------------------------------------

@Composable
fun ChangePasswordScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val viewModel = koinViewModel<ChangePasswordViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    LaunchedEffect(state.changed) { if (state.changed) onChanged() }

    FormScaffold(
        onBack = onBack,
        title = Res.string.change_password_title,
        body = Res.string.change_password_body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(
                text = stringResource(Res.string.change_password_button),
                onClick = viewModel::submit,
                loading = state.submitting,
            )
            Spacer(Modifier.size(12.dp))
        },
    ) {
        PasswordField(
            value = state.currentPassword,
            onValueChange = viewModel::onCurrentChange,
            label = stringResource(Res.string.change_password_current),
            error = state.fieldErrors[PasswordField.CURRENT]?.resolve(),
            imeAction = ImeAction.Next,
            onDone = { focus.moveFocus(FocusDirection.Down) },
        )
        PasswordField(
            value = state.newPassword,
            onValueChange = viewModel::onNewChange,
            label = stringResource(Res.string.change_password_new),
            error = state.fieldErrors[PasswordField.NEW]?.resolve(),
            hint = stringResource(Res.string.auth_password_rules),
            imeAction = if (state.mfaRequired) ImeAction.Next else ImeAction.Done,
            onDone = { if (state.mfaRequired) focus.moveFocus(FocusDirection.Down) else viewModel.submit() },
        )
        if (state.mfaRequired) {
            FormTextField(
                value = state.mfaCode,
                onValueChange = viewModel::onMfaCodeChange,
                label = stringResource(Res.string.change_password_mfa),
                error = state.fieldErrors[PasswordField.MFA]?.resolve(),
                supporting = stringResource(Res.string.change_password_mfa_hint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            )
        }
    }
}

// ---- Mattoncini ----

private const val AvatarSidePx = 512

/**
 * La foto profilo (M11), o l'iniziale se non c'è. Un tocco apre il menu: scegli una foto, e rimuovila se c'è. Durante
 * il caricamento un anello attorno. L'URL firmato vale 5 minuti; la foto resta solo nella cache in memoria (chiave =
 * id, che cambia a ogni foto nuova): su disco no, è un dato personale che al logout resterebbe lì.
 */
@Composable
private fun ProfileAvatar(profile: UserProfile?, busy: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.profile_photo)
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(enabled = profile != null && !busy, role = Role.Button) { menu = true }
                .semantics { contentDescription = label },
        ) {
            Monogram(profile?.displayName)
            profile?.avatarMediaId?.let { AvatarPhoto(it) }
            if (busy) CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 3.dp)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.profile_photo_choose)) }, onClick = {
                menu = false
                onPick()
            })
            if (profile?.avatarMediaId != null) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.profile_photo_remove)) }, onClick = {
                    menu = false
                    onRemove()
                })
            }
        }
    }
}

// Sopra l'iniziale, che si vede finché la foto non arriva (o se non arriva)
@Composable
private fun AvatarPhoto(mediaId: String) {
    val signed = rememberSignedUrl(koinInject<MediaRepository>(), mediaId)
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val sidePx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val url = signed.url ?: return
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(sidePx)
            .memoryCacheKey(mediaId)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { scope.launch { signed.onDownloadFailed() } },
        modifier = Modifier.size(56.dp).clip(CircleShape),
    )
}

// Iniziale del nome su un tondo color muschio; per l'ospite una sagoma. Decorativo: il nome è scritto accanto.
@Composable
private fun Monogram(name: String?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()
    Box(
        modifier = modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(initial.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size / 2))
        }
    }
}

// ---- Selettore del tema: capsula con tre simboli e un cursore che scorre ----

// 48 dp per segmento: è il bersaglio minimo; il cursore visibile è un cerchio da 40 al centro
private val SegmentSize = 48.dp

@Composable
private fun ThemeSwitch(current: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    val thumbOffset by animateDpAsState(
        targetValue = SegmentSize * options.indexOf(current),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
    )
    Box(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .selectableGroup(),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(SegmentSize)
                .padding(4.dp)
                .shadow(1.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
        )
        Row {
            options.forEach { mode ->
                val label = stringResource(mode.label())
                val selected = mode == current
                Box(
                    modifier = Modifier
                        .size(SegmentSize)
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { onSelected(mode) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    ThemeGlyph(
                        mode = mode,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// Simboli disegnati a mano: auto (cerchio mezzo pieno), sole, luna. Le icone di base non li hanno.
@Composable
private fun ThemeGlyph(mode: ThemeMode, tint: Color, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val r = size.minDimension / 2
        val c = center
        when (mode) {
            ThemeMode.SYSTEM -> {
                val radius = r * 0.8f
                drawCircle(tint, radius, c, style = Stroke(width = r * 0.16f))
                drawArc(
                    color = tint,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = Size(radius * 2, radius * 2),
                )
            }
            ThemeMode.LIGHT -> {
                drawCircle(tint, r * 0.36f, c)
                repeat(8) { i ->
                    val angle = i * PI / 4
                    val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(tint, c + dir * (r * 0.62f), c + dir * (r * 0.92f), strokeWidth = r * 0.15f, cap = StrokeCap.Round)
                }
            }
            ThemeMode.DARK -> {
                val moon = Path().apply { addOval(Rect(c, r * 0.8f)) }
                val bite = Path().apply { addOval(Rect(Offset(c.x + r * 0.42f, c.y - r * 0.34f), r * 0.66f)) }
                drawPath(Path.combine(PathOperation.Difference, moon, bite), tint)
            }
        }
    }
}

private fun UserRole.label(): StringResource = when (this) {
    UserRole.GUEST -> Res.string.role_guest
    UserRole.HIKER -> Res.string.role_hiker
    UserRole.PARK_MANAGER -> Res.string.role_park_manager
    UserRole.RESCUE_OPERATOR -> Res.string.role_rescue_operator
    UserRole.ADMIN -> Res.string.role_admin
}

private fun ThemeMode.label(): StringResource = when (this) {
    ThemeMode.SYSTEM -> Res.string.theme_system
    ThemeMode.LIGHT -> Res.string.theme_light
    ThemeMode.DARK -> Res.string.theme_dark
}
