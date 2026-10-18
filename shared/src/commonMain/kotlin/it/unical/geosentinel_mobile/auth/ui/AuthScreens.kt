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
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import geosentinel_mobile.shared.generated.resources.auth_forgot_password
import geosentinel_mobile.shared.generated.resources.reset_change_email
import geosentinel_mobile.shared.generated.resources.reset_code
import geosentinel_mobile.shared.generated.resources.reset_code_body
import geosentinel_mobile.shared.generated.resources.reset_code_button
import geosentinel_mobile.shared.generated.resources.reset_code_hint
import geosentinel_mobile.shared.generated.resources.reset_code_title
import geosentinel_mobile.shared.generated.resources.reset_done_body
import geosentinel_mobile.shared.generated.resources.reset_done_button
import geosentinel_mobile.shared.generated.resources.reset_done_title
import geosentinel_mobile.shared.generated.resources.reset_email_body
import geosentinel_mobile.shared.generated.resources.reset_email_button
import geosentinel_mobile.shared.generated.resources.reset_email_title
import geosentinel_mobile.shared.generated.resources.reset_new_password
import geosentinel_mobile.shared.generated.resources.reset_not_arrived
import geosentinel_mobile.shared.generated.resources.reset_send_again
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
import it.unical.geosentinel_mobile.core.ui.FooterLink
import it.unical.geosentinel_mobile.core.ui.ForestHeader
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PasswordField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LoginScreen(onBack: () -> Unit, onRegister: () -> Unit, onForgotPassword: (email: String) -> Unit, onLoggedIn: () -> Unit) {
    val viewModel = koinViewModel<LoginViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }

    FormScaffold(
        onBack = onBack,
        title = Res.string.auth_login_title,
        body = Res.string.auth_login_body,
        error = state.error?.resolve(),
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
        TextButton(
            onClick = { onForgotPassword(state.email.trim()) },
            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.auth_forgot_password), fontWeight = FontWeight.SemiBold) }
    }
}

// Password dimenticata: un form per passo, con lo stesso scheletro degli altri
@Composable
fun PasswordResetScreen(email: String, onBack: () -> Unit, onDone: () -> Unit) {
    val viewModel = koinViewModel<PasswordResetViewModel> { parametersOf(email) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.step) {
        ResetStep.EMAIL -> FormScaffold(
            onBack = onBack,
            title = Res.string.reset_email_title,
            body = Res.string.reset_email_body,
            error = state.error?.resolve(),
            actions = {
                PrimaryPillButton(stringResource(Res.string.reset_email_button), onClick = viewModel::requestCode, loading = state.submitting)
                Spacer(Modifier.size(12.dp))
            },
        ) {
            FormTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(Res.string.auth_email),
                error = state.fieldErrors[ResetField.EMAIL]?.resolve(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.requestCode() }),
            )
        }
        ResetStep.CODE -> FormScaffold(
            onBack = viewModel::changeEmail,
            title = Res.string.reset_code_title,
            body = Res.string.reset_code_body,
            error = state.error?.resolve(),
            actions = {
                PrimaryPillButton(stringResource(Res.string.reset_code_button), onClick = viewModel::confirm, loading = state.submitting)
                FooterLink(Res.string.reset_not_arrived, Res.string.reset_send_again, viewModel::requestCode)
            },
        ) {
            Text(state.email, style = MaterialTheme.typography.titleMedium)
            FormTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                label = stringResource(Res.string.reset_code),
                error = state.fieldErrors[ResetField.CODE]?.resolve(),
                supporting = stringResource(Res.string.reset_code_hint),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            )
            PasswordField(
                value = state.newPassword,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(Res.string.reset_new_password),
                error = state.fieldErrors[ResetField.PASSWORD]?.resolve(),
                hint = stringResource(Res.string.auth_password_rules),
                onDone = viewModel::confirm,
            )
            TextButton(onClick = viewModel::changeEmail, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.reset_change_email), fontWeight = FontWeight.SemiBold)
            }
        }
        ResetStep.DONE -> FormScaffold(
            onBack = onDone,
            title = Res.string.reset_done_title,
            body = Res.string.reset_done_body,
            actions = {
                PrimaryPillButton(stringResource(Res.string.reset_done_button), onClick = onDone)
                Spacer(Modifier.size(12.dp))
            },
        ) {}
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
        error = state.error?.resolve(),
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
    }

    if (pickingDate) {
        BirthDatePickerDialog(
            title = Res.string.auth_birth_date,
            onPicked = {
                viewModel.onBirthDateChange(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

// Calendario della data di nascita, per la registrazione e per il primo accesso con Google
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthDatePickerDialog(title: StringResource, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // il DatePicker lavora in millisecondi UTC a mezzanotte; senza una data è come annullare
                val picked = pickerState.selectedDateMillis
                if (picked == null) onDismiss() else onPicked(Instant.fromEpochMilliseconds(picked).toLocalDateTime(TimeZone.UTC).date)
            }) { Text(stringResource(Res.string.auth_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.auth_cancel)) } },
    ) {
        DatePicker(pickerState, title = { Text(stringResource(title), modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) })
    }
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
