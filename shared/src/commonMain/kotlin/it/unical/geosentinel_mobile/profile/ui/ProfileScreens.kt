package it.unical.geosentinel_mobile.profile.ui

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.auth_no_account
import geosentinel_mobile.shared.generated.resources.auth_password_rules
import geosentinel_mobile.shared.generated.resources.auth_register_link
import geosentinel_mobile.shared.generated.resources.change_password_body
import geosentinel_mobile.shared.generated.resources.change_password_button
import geosentinel_mobile.shared.generated.resources.change_password_current
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
import geosentinel_mobile.shared.generated.resources.profile_change_password
import geosentinel_mobile.shared.generated.resources.profile_delete_account
import geosentinel_mobile.shared.generated.resources.profile_deleting
import geosentinel_mobile.shared.generated.resources.profile_guest_body
import geosentinel_mobile.shared.generated.resources.profile_login
import geosentinel_mobile.shared.generated.resources.profile_logout
import geosentinel_mobile.shared.generated.resources.profile_name
import geosentinel_mobile.shared.generated.resources.profile_session_expired
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
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.session.UserRole
import it.unical.geosentinel_mobile.core.session.role
import it.unical.geosentinel_mobile.core.ui.FooterLink
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
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ContentMaxWidth = 600.dp

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
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    val viewModel = koinViewModel<ProfileViewModel>()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var deleteStep by rememberSaveable { mutableIntStateOf(DeleteNone) }
    val profile = (session as? SessionState.LoggedIn)?.profile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ForestHeader(Modifier.fillMaxWidth().height(240.dp))
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
            // Chi sei: iniziale e nome, sotto ruolo ed email (o cosa si guadagna accedendo)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(profile?.displayName)
                Spacer(Modifier.size(16.dp))
                Text(
                    text = profile?.displayName ?: stringResource(session.role.label()),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            val subtitle = when {
                profile != null -> stringResource(profile.role.label()) + " · " + profile.email
                session is SessionState.LoggedIn -> null // profilo non ancora letto (offline)
                session is SessionState.Expired -> stringResource(Res.string.profile_session_expired)
                else -> stringResource(Res.string.profile_guest_body)
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (session is SessionState.LoggedIn) {
                SectionHeader(Res.string.profile_account_section)
                Card {
                    // Senza profilo (avvio offline) il nome non si può modificare: non sappiamo quale sia
                    SettingsRow(
                        label = stringResource(Res.string.profile_name),
                        value = profile?.displayName,
                        onClick = onEditName,
                        enabled = profile != null,
                        chevron = true,
                    )
                    RowDivider()
                    SettingsRow(stringResource(Res.string.profile_change_password), onClick = onChangePassword, chevron = true)
                    RowDivider()
                    // "Esci" è un'uscita, non un errore: verde, non prugna
                    SettingsRow(stringResource(Res.string.profile_logout), onClick = viewModel::logout, color = MaterialTheme.colorScheme.primary)
                }
                // Azione distruttiva da sola, lontana dalle altre e nel colore degli errori (mai il rosso dell'SOS)
                Spacer(Modifier.size(16.dp))
                Card {
                    SettingsRow(
                        label = stringResource(if (ui.deleting) Res.string.profile_deleting else Res.string.profile_delete_account),
                        onClick = { deleteStep = DeleteExplain },
                        enabled = !ui.deleting,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // es. personale: "si cancellano tramite l'amministratore"
                ui.error?.let {
                    Spacer(Modifier.size(12.dp))
                    FormErrorBanner(it.resolve())
                }
            } else {
                // Ospite: una sola azione evidente (Accedi, a tutta larghezza) e l'alternativa come link sotto,
                // come nei form: niente due pulsanti grandi in competizione.
                Spacer(Modifier.size(28.dp))
                PrimaryPillButton(stringResource(Res.string.profile_login), onClick = onLogin)
                Spacer(Modifier.size(4.dp))
                FooterLink(Res.string.auth_no_account, Res.string.auth_register_link, onRegister)
            }

            SectionHeader(Res.string.theme_section)
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.theme_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    ThemeSwitch(themeMode, onThemeModeSelected)
                }
            }

            Spacer(Modifier.size(32.dp))
        }
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
        FormErrorBanner(state.error?.resolve())
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
            onDone = viewModel::submit,
        )
        FormErrorBanner(state.error?.resolve())
    }
}

// ---- Mattoncini ----

// Card: un passo di tono sopra lo sfondo e un filo di bordo, senza ombra
@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier) { content() }
    }
}

// Riga di impostazione da 56 dp: etichetta, valore attuale a destra e freccia se apre un'altra schermata
@Composable
private fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    chevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = if (chevron) 8.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (chevron) FontWeight.Normal else FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).widthIn(max = 200.dp),
            )
        }
        if (chevron) {
            // decorativa: l'etichetta dice già dove porta la riga
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

// Iniziale del nome su un tondo color muschio; per l'ospite una sagoma. Decorativo: il nome è scritto accanto.
@Composable
private fun Monogram(name: String?, modifier: Modifier = Modifier) {
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()
    Box(
        modifier = modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(initial.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: StringResource) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp).semantics { heading() },
    )
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
