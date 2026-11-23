// Schermate di accesso dell'app: login (con il passo del codice della verifica in due passaggi), registrazione e
// password dimenticata. Sono tutte costruite con FormScaffold, lo scheletro comune dei form, e prendono lo stato dai
// rispettivi ViewModel tramite Koin. Contiene anche i componenti condivisi del codice e della data di nascita.
package it.unical.geosentinel_mobile.auth.ui

import androidx.compose.ui.backhandler.BackHandler
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
import androidx.compose.ui.ExperimentalComposeUiApi
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
import geosentinel_mobile.shared.generated.resources.mfa_code_label
import geosentinel_mobile.shared.generated.resources.mfa_login_body
import geosentinel_mobile.shared.generated.resources.mfa_login_body_recovery
import geosentinel_mobile.shared.generated.resources.mfa_login_button
import geosentinel_mobile.shared.generated.resources.mfa_login_title
import geosentinel_mobile.shared.generated.resources.mfa_lost_access
import geosentinel_mobile.shared.generated.resources.mfa_recovery_label
import geosentinel_mobile.shared.generated.resources.mfa_use_recovery
import geosentinel_mobile.shared.generated.resources.mfa_use_totp
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
import it.unical.geosentinel_mobile.core.ui.AppIcons

/**
 * Schermata di login con email e password.
 *
 * Quando il server chiede il codice della verifica in due passaggi la stessa schermata mostra il form del codice.
 * [onLoggedIn] viene chiamata una volta a login riuscito; [onForgotPassword] riceve l'email già scritta, così il
 * form di reset parte compilato.
 */
@Composable
fun LoginScreen(onBack: () -> Unit, onRegister: () -> Unit, onForgotPassword: (email: String) -> Unit, onLoggedIn: () -> Unit) {
    val viewModel = koinViewModel<LoginViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }

    if (state.mfaStep) {
        // Nel passo del codice, Indietro (anche quello di sistema) torna al form della password invece di chiudere
        // il login.
        @Suppress("DEPRECATION")
        @OptIn(ExperimentalComposeUiApi::class)
        BackHandler(onBack = viewModel::onMfaCancelled)
        FormScaffold(
            brand = true,
            onBack = viewModel::onMfaCancelled,
            title = Res.string.mfa_login_title,
            body = if (state.recoveryCode) Res.string.mfa_login_body_recovery else Res.string.mfa_login_body,
            error = state.error?.resolve(),
            actions = {
                PrimaryPillButton(
                    text = stringResource(Res.string.mfa_login_button),
                    onClick = viewModel::submitCode,
                    enabled = state.canSubmitCode,
                    loading = state.submitting,
                )
                Spacer(Modifier.size(12.dp))
            },
        ) { MfaCodeFields(state, viewModel) }
        return
    }

    FormScaffold(
        brand = true,
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

/**
 * Campo del codice della verifica in due passaggi, usato sia nel login con email sia nel dialogo del login con Google.
 *
 * Accetta il codice a 6 cifre dell'app di autenticazione oppure, con il pulsante sotto, un codice di recupero (con
 * una tastiera diversa). Il numero di tentativi rimasti non si mostra; il testo finale spiega che chi ha perso sia
 * l'app sia i codici deve rivolgersi a un amministratore.
 */
@Composable
internal fun MfaCodeFields(state: LoginUiState, viewModel: LoginViewModel) {
    FormTextField(
        value = state.mfaCode,
        onValueChange = viewModel::onMfaCodeChange,
        label = stringResource(if (state.recoveryCode) Res.string.mfa_recovery_label else Res.string.mfa_code_label),
        error = state.mfaCodeError?.resolve(),
        keyboardOptions = if (state.recoveryCode) {
            KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done)
        } else {
            KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
        },
        keyboardActions = KeyboardActions(onDone = { viewModel.submitCode() }),
    )
    TextButton(onClick = viewModel::onToggleRecoveryCode, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(if (state.recoveryCode) Res.string.mfa_use_totp else Res.string.mfa_use_recovery), fontWeight = FontWeight.SemiBold)
    }
    Text(stringResource(Res.string.mfa_lost_access), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Flusso della password dimenticata, un form per ogni passo di [ResetStep]: richiesta del codice via email, codice
 * ricevuto più nuova password, conferma finale. [email] precompila il primo passo; [onDone] riporta al login.
 */
@Composable
fun PasswordResetScreen(email: String, onBack: () -> Unit, onDone: () -> Unit) {
    val viewModel = koinViewModel<PasswordResetViewModel> { parametersOf(email) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.step) {
        ResetStep.EMAIL -> FormScaffold(
            brand = true,
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
            brand = true,
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
            brand = true,
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

/**
 * Schermata di registrazione: nome, email, password e data di nascita (scelta da calendario). Gli errori di
 * validazione si mostrano sotto il campo a cui si riferiscono. [onRegistered] viene chiamata a registrazione riuscita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onBack: () -> Unit, onLogin: () -> Unit, onRegistered: () -> Unit) {
    val viewModel = koinViewModel<RegisterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.registered) { if (state.registered) onRegistered() }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    FormScaffold(
        brand = true,
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

/**
 * Dialogo con il calendario per scegliere la data di nascita, usato nella registrazione e al primo accesso con
 * Google. [onPicked] riceve la data scelta; se non è selezionata alcuna data, Conferma si comporta come Annulla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthDatePickerDialog(title: StringResource, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // Il DatePicker restituisce la mezzanotte UTC del giorno scelto, in millisecondi: si converte in UTC
                // per non spostare la data di un giorno con il fuso orario del telefono.
                val picked = pickerState.selectedDateMillis
                if (picked == null) onDismiss() else onPicked(Instant.fromEpochMilliseconds(picked).toLocalDateTime(TimeZone.UTC).date)
            }) { Text(stringResource(Res.string.auth_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.auth_cancel)) } },
    ) {
        DatePicker(pickerState, title = { Text(stringResource(title), modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) })
    }
}

/**
 * Campo della data di nascita in sola lettura. Un riquadro trasparente sopra il campo lo rende tutto cliccabile e apre
 * il calendario: così l'utente non deve scrivere la data in un formato da indovinare.
 */
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
            trailing = { Icon(AppIcons.Calendar, contentDescription = null, modifier = Modifier.padding(end = 4.dp)) },
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick),
        )
    }
}

/** Formatta la data come gg/mm/aaaa, il formato abituale in italiano. */
private fun formatDate(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}/${date.month.number.toString().padStart(2, '0')}/${date.year}"
