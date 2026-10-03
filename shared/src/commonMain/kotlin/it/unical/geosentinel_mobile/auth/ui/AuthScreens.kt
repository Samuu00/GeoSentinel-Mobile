package it.unical.geosentinel_mobile.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_birth_date
import geosentinel_mobile.shared.generated.resources.auth_birth_date_pick
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.auth_confirm
import geosentinel_mobile.shared.generated.resources.auth_display_name
import geosentinel_mobile.shared.generated.resources.auth_email
import geosentinel_mobile.shared.generated.resources.auth_have_account
import geosentinel_mobile.shared.generated.resources.auth_hide_password
import geosentinel_mobile.shared.generated.resources.auth_login_body
import geosentinel_mobile.shared.generated.resources.auth_login_button
import geosentinel_mobile.shared.generated.resources.auth_login_link
import geosentinel_mobile.shared.generated.resources.auth_login_title
import geosentinel_mobile.shared.generated.resources.auth_no_account
import geosentinel_mobile.shared.generated.resources.auth_password
import geosentinel_mobile.shared.generated.resources.auth_password_rules
import geosentinel_mobile.shared.generated.resources.auth_register_body
import geosentinel_mobile.shared.generated.resources.auth_register_button
import geosentinel_mobile.shared.generated.resources.auth_register_link
import geosentinel_mobile.shared.generated.resources.auth_register_title
import geosentinel_mobile.shared.generated.resources.auth_show_password
import it.unical.geosentinel_mobile.core.ui.BackButton
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
fun LoginScreen(onBack: () -> Unit, onRegister: () -> Unit, onLoggedIn: () -> Unit) {
    val viewModel = koinViewModel<LoginViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }

    FormScaffold(
        onBack = onBack,
        title = Res.string.auth_login_title,
        body = Res.string.auth_login_body,
        actions = {
            PrimaryPillButton(
                text = stringResource(Res.string.auth_login_button),
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                loading = state.submitting,
            )
            FooterLink(Res.string.auth_no_account, Res.string.auth_register_link, onRegister)
        },
    ) {
        FormTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(Res.string.auth_email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        PasswordField(value = state.password, onValueChange = viewModel::onPasswordChange, onDone = viewModel::submit)
        FormErrorBanner(state.error?.resolve())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onBack: () -> Unit, onLogin: () -> Unit, onRegistered: () -> Unit) {
    val viewModel = koinViewModel<RegisterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.registered) { if (state.registered) onRegistered() }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    FormScaffold(
        onBack = onBack,
        title = Res.string.auth_register_title,
        body = Res.string.auth_register_body,
        actions = {
            PrimaryPillButton(
                text = stringResource(Res.string.auth_register_button),
                onClick = viewModel::submit,
                loading = state.submitting,
            )
            FooterLink(Res.string.auth_have_account, Res.string.auth_login_link, onLogin)
        },
    ) {
        FormTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = stringResource(Res.string.auth_display_name),
            error = state.fieldErrors[RegisterField.DISPLAY_NAME]?.resolve(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        FormTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(Res.string.auth_email),
            error = state.fieldErrors[RegisterField.EMAIL]?.resolve(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            error = state.fieldErrors[RegisterField.PASSWORD]?.resolve(),
            hint = stringResource(Res.string.auth_password_rules),
            onDone = { pickingDate = true },
        )
        BirthDateField(
            date = state.birthDate,
            error = state.fieldErrors[RegisterField.BIRTH_DATE]?.resolve(),
            onClick = { pickingDate = true },
        )
        FormErrorBanner(state.error?.resolve())
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    // il DatePicker lavora in millisecondi UTC a mezzanotte
                    pickerState.selectedDateMillis?.let {
                        viewModel.onBirthDateChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    }
                    pickingDate = false
                }) { Text(stringResource(Res.string.auth_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(Res.string.auth_cancel)) } },
        ) {
            DatePicker(pickerState)
        }
    }
}

// Struttura dei form, secondo gli standard per mobile:
// - in alto la testata con il paesaggio (sotto la barra di stato) e il tondo Indietro (su iPhone non c'è quello di sistema);
// - al centro titolo, testo e campi, che scorrono;
// - in fondo, fissa, l'azione principale con il link alternativo sotto: sempre visibile e a portata di pollice.
//   Con la tastiera aperta sale sopra la tastiera: lo Scaffold di App applica safeDrawing, che comprende WindowInsets.ime.
// Contenuto largo al massimo 480 dp.
@Composable
private fun FormScaffold(
    onBack: () -> Unit,
    title: StringResource,
    body: StringResource,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ForestHeader(Modifier.fillMaxWidth().height(200.dp))
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
                    Spacer(Modifier.size(24.dp))
                }
            }
            // azioni in fondo, su un filo che le separa dal contenuto che scorre
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                content = actions,
            )
        }
        BackButton(onBack, Modifier.align(Alignment.TopStart))
    }
}

// "Non hai un account? Registrati": la domanda in grigio, il link in verde. Va a capo con il testo grande.
// È l'azione alternativa sotto quella principale, qui e nel Profilo da ospite.
@Composable
internal fun FooterLink(question: StringResource, link: StringResource, onClick: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(question), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(link), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    error: String? = null,
    hint: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(Res.string.auth_password),
        error = error,
        supporting = hint,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        // testo e non l'icona dell'occhio, che in material-icons-core non c'è; dice anche cosa fa
        trailing = {
            TextButton(onClick = { visible = !visible }, modifier = Modifier.heightIn(min = 48.dp).padding(end = 4.dp)) {
                Text(
                    stringResource(if (visible) Res.string.auth_hide_password else Res.string.auth_show_password),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

// Tutto il campo apre il calendario: niente formati di data da indovinare
@Composable
private fun BirthDateField(date: LocalDate?, error: String?, onClick: () -> Unit) {
    val openLabel = stringResource(Res.string.auth_birth_date_pick)
    Box {
        FormTextField(
            value = date?.let(::formatDate).orEmpty(),
            onValueChange = {},
            label = stringResource(Res.string.auth_birth_date),
            error = error,
            readOnly = true,
            trailing = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.padding(end = 4.dp)) },
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick),
        )
    }
}

private fun formatDate(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}/${date.month.number.toString().padStart(2, '0')}/${date.year}"
