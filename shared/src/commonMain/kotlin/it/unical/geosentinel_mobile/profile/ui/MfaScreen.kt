package it.unical.geosentinel_mobile.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.error_mfa_no_app
import geosentinel_mobile.shared.generated.resources.mfa_code_label
import geosentinel_mobile.shared.generated.resources.mfa_codes_body
import geosentinel_mobile.shared.generated.resources.mfa_codes_copy
import geosentinel_mobile.shared.generated.resources.mfa_codes_done
import geosentinel_mobile.shared.generated.resources.mfa_codes_leave
import geosentinel_mobile.shared.generated.resources.mfa_codes_leave_body
import geosentinel_mobile.shared.generated.resources.mfa_codes_leave_title
import geosentinel_mobile.shared.generated.resources.mfa_codes_saved
import geosentinel_mobile.shared.generated.resources.mfa_codes_title
import geosentinel_mobile.shared.generated.resources.mfa_disable
import geosentinel_mobile.shared.generated.resources.mfa_disable_body
import geosentinel_mobile.shared.generated.resources.mfa_disable_label
import geosentinel_mobile.shared.generated.resources.mfa_disable_title
import geosentinel_mobile.shared.generated.resources.mfa_email_code_hint
import geosentinel_mobile.shared.generated.resources.mfa_email_code_label
import geosentinel_mobile.shared.generated.resources.mfa_enable
import geosentinel_mobile.shared.generated.resources.mfa_off_body
import geosentinel_mobile.shared.generated.resources.mfa_on_body
import geosentinel_mobile.shared.generated.resources.mfa_regenerate
import geosentinel_mobile.shared.generated.resources.mfa_regenerate_body
import geosentinel_mobile.shared.generated.resources.mfa_regenerate_button
import geosentinel_mobile.shared.generated.resources.mfa_setup_body
import geosentinel_mobile.shared.generated.resources.mfa_setup_button
import geosentinel_mobile.shared.generated.resources.mfa_setup_copy_key
import geosentinel_mobile.shared.generated.resources.mfa_setup_key
import geosentinel_mobile.shared.generated.resources.mfa_setup_open_app
import geosentinel_mobile.shared.generated.resources.mfa_setup_title
import geosentinel_mobile.shared.generated.resources.mfa_title
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.GroupCard
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.GroupRow
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.SecureScreen
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.sensitiveClipEntry
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Verifica in due passaggi (M10.2): un form per passo, con lo scheletro degli altri. Niente QR code: sul telefono che
 * ha l'app di autenticazione basta aprire l'URI `otpauth://`; per un altro dispositivo c'è la chiave da copiare.
 */
@Composable
fun MfaScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<MfaViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    // dal passo dei codici si esce solo confermando: non si rivedranno più
    val stepBack = { if (state.step == MfaStep.CODES) confirmLeave = true else viewModel.back() }
    if (state.step != MfaStep.OVERVIEW) {
        @Suppress("DEPRECATION")
        @OptIn(ExperimentalComposeUiApi::class)
        BackHandler(onBack = stepBack)
    }

    when (state.step) {
        MfaStep.OVERVIEW -> FormScaffold(
            onBack = onBack,
            title = Res.string.mfa_title,
            body = if (state.enabled) Res.string.mfa_on_body else Res.string.mfa_off_body,
            error = state.error?.resolve(),
            actions = {
                if (!state.enabled) PrimaryPillButton(stringResource(Res.string.mfa_enable), onClick = viewModel::startSetup, loading = state.busy)
                Spacer(Modifier.size(12.dp))
            },
        ) {
            if (state.enabled) {
                GroupCard {
                    GroupRow(stringResource(Res.string.mfa_regenerate), onClick = viewModel::openRegenerate)
                    GroupDivider()
                    GroupRow(stringResource(Res.string.mfa_disable), onClick = viewModel::openDisable, color = MaterialTheme.colorScheme.error, chevron = false)
                }
            }
        }
        MfaStep.SETUP -> CodeStep(
            state = state, viewModel = viewModel, onBack = stepBack,
            title = Res.string.mfa_setup_title, body = Res.string.mfa_setup_body, button = Res.string.mfa_setup_button,
        ) {
            SetupKey(state, onOpenFailed = viewModel::onOpenAppFailed)
            EmailCode(state, viewModel)
        }
        MfaStep.DISABLE -> CodeStep(
            state = state, viewModel = viewModel, onBack = stepBack,
            title = Res.string.mfa_disable_title, body = Res.string.mfa_disable_body, button = Res.string.mfa_disable,
            label = Res.string.mfa_disable_label, freeText = true,
        )
        MfaStep.REGENERATE -> CodeStep(
            state = state, viewModel = viewModel, onBack = stepBack,
            title = Res.string.mfa_regenerate, body = Res.string.mfa_regenerate_body, button = Res.string.mfa_regenerate_button,
        )
        MfaStep.CODES -> RecoveryCodes(state, viewModel, onBack = stepBack)
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(Res.string.mfa_codes_leave_title)) },
            text = { Text(stringResource(Res.string.mfa_codes_leave_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.back()
                }) { Text(stringResource(Res.string.mfa_codes_leave)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(Res.string.auth_cancel)) } },
        )
    }
}

// Un passo con un codice da scrivere: TOTP a 6 cifre, o anche un codice di recupero (disattivazione)
@Composable
private fun CodeStep(
    state: MfaUiState,
    viewModel: MfaViewModel,
    onBack: () -> Unit,
    title: StringResource,
    body: StringResource,
    button: StringResource,
    label: StringResource = Res.string.mfa_code_label,
    freeText: Boolean = false,
    above: @Composable () -> Unit = {},
) {
    FormScaffold(
        onBack = onBack,
        title = title,
        body = body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(stringResource(button), onClick = viewModel::submitCode, enabled = state.canSubmitCode, loading = state.busy)
            Spacer(Modifier.size(12.dp))
        },
    ) {
        above()
        FormTextField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            label = stringResource(label),
            error = state.codeError?.resolve(),
            keyboardOptions = if (freeText) {
                KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done)
            } else {
                KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
            },
            keyboardActions = KeyboardActions(onDone = { viewModel.submitCode() }),
        )
    }
}

// L'URI apre l'app di autenticazione (Google Authenticator, Aegis su Android; Password su iOS); sotto la chiave a gruppi
// di 4 per chi la scrive a mano
@Composable
private fun SetupKey(state: MfaUiState, onOpenFailed: (UiMessage) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val secret = state.secret ?: return
    // la chiave vale quanto i codici di recupero: chi la vede genera i codici per sempre
    SecureScreen()
    state.otpauthUri?.let { uri ->
        OutlinedButton(
            onClick = { runCatching { uriHandler.openUri(uri) }.onFailure { onOpenFailed(UiMessage(Res.string.error_mfa_no_app)) } },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(Res.string.mfa_setup_open_app), fontWeight = FontWeight.SemiBold) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.mfa_setup_key), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // niente SelectionContainer: la copia passa solo dal pulsante, che segna gli appunti come sensibili
        Text(secret.chunked(4).joinToString(" "), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
        TextButton(
            onClick = { scope.launch { clipboard.setClipEntry(sensitiveClipEntry(secret)) } },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.mfa_setup_copy_key), fontWeight = FontWeight.SemiBold) }
    }
}

// Il codice che il server ha mandato all'email dell'account con lo setup (ADR-0026 del backend): prova che chi attiva
// la verifica possiede l'email
@Composable
private fun EmailCode(state: MfaUiState, viewModel: MfaViewModel) {
    FormTextField(
        value = state.emailCode,
        onValueChange = viewModel::onEmailCodeChange,
        label = stringResource(Res.string.mfa_email_code_label),
        supporting = stringResource(Res.string.mfa_email_code_hint),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
    )
}

// I codici di recupero, mostrati una volta sola: niente screenshot, copia segnata come sensibile, "Fine" solo dopo
// "Li ho salvati"
@Composable
private fun RecoveryCodes(state: MfaUiState, viewModel: MfaViewModel, onBack: () -> Unit) {
    SecureScreen()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    FormScaffold(
        onBack = onBack,
        title = Res.string.mfa_codes_title,
        body = Res.string.mfa_codes_body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(stringResource(Res.string.mfa_codes_done), onClick = viewModel::back, enabled = state.savedConfirmed)
            Spacer(Modifier.size(12.dp))
        },
    ) {
        GroupCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.recoveryCodes.forEach { Text(it, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace) }
            }
        }
        TextButton(
            onClick = { scope.launch { clipboard.setClipEntry(sensitiveClipEntry(state.recoveryCodes.joinToString("\n"))) } },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.mfa_codes_copy), fontWeight = FontWeight.SemiBold) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = state.savedConfirmed, role = Role.Checkbox, onValueChange = viewModel::onSavedChange),
        ) {
            Checkbox(checked = state.savedConfirmed, onCheckedChange = null)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(Res.string.mfa_codes_saved), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
