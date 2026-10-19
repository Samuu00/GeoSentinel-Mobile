package it.unical.geosentinel_mobile.staff.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.auth_email
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_body
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_button
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_confirm
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_confirm_body
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_confirm_title
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_done_body
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_done_button
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_done_title
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_title
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Azzeramento della verifica in due passaggi (M10.3): email, conferma, esito
@Composable
fun MfaResetScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<MfaResetViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }

    if (state.done) {
        FormScaffold(
            onBack = onBack,
            title = Res.string.staff_mfa_reset_done_title,
            body = Res.string.staff_mfa_reset_done_body,
            actions = {
                PrimaryPillButton(stringResource(Res.string.staff_mfa_reset_done_button), onClick = onBack)
                Spacer(Modifier.size(12.dp))
            },
        ) {}
        return
    }

    FormScaffold(
        onBack = onBack,
        title = Res.string.staff_mfa_reset_title,
        body = Res.string.staff_mfa_reset_body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(
                stringResource(Res.string.staff_mfa_reset_button),
                onClick = { confirming = true },
                enabled = state.canSubmit,
                loading = state.busy,
            )
            Spacer(Modifier.size(12.dp))
        },
    ) {
        FormTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(Res.string.auth_email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.staff_mfa_reset_confirm_title)) },
            text = { Text(stringResource(Res.string.staff_mfa_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    viewModel.submit()
                }) { Text(stringResource(Res.string.staff_mfa_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(Res.string.auth_cancel)) } },
        )
    }
}
