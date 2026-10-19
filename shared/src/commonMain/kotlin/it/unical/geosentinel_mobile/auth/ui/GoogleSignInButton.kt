package it.unical.geosentinel_mobile.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_cancel
import geosentinel_mobile.shared.generated.resources.auth_google_birth_date_title
import geosentinel_mobile.shared.generated.resources.mfa_login_body
import geosentinel_mobile.shared.generated.resources.mfa_login_body_recovery
import geosentinel_mobile.shared.generated.resources.mfa_login_button
import geosentinel_mobile.shared.generated.resources.mfa_login_title
import geosentinel_mobile.shared.generated.resources.auth_google_button
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.resolve
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Continua con Google" con il suo flusso: selettore di account, data di nascita al primo accesso, errori sotto il
 * pulsante. Non disegna nulla se il login Google non è configurato su questa piattaforma. A login riuscito la sessione
 * cambia e chi mostra questo blocco (il Profilo da ospite) passa da solo alla vista dell'utente.
 */
@Composable
fun ContinueWithGoogle(modifier: Modifier = Modifier) {
    val googleSignIn = rememberGoogleSignIn(koinInject<AppConfig>().googleWebClientId) ?: return
    val viewModel = koinViewModel<LoginViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GoogleSignInButton(
            onClick = {
                viewModel.onGoogleStarted()
                scope.launch { viewModel.onGoogleResult(googleSignIn()) }
            },
            loading = state.submitting,
        )
        FormErrorBanner(state.error?.resolve())
    }

    // Account con la verifica in due passaggi: il codice in un dialogo, il Profilo resta sotto
    if (state.mfaStep) {
        AlertDialog(
            onDismissRequest = viewModel::onMfaCancelled,
            title = { Text(stringResource(Res.string.mfa_login_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (state.recoveryCode) Res.string.mfa_login_body_recovery else Res.string.mfa_login_body))
                    FormErrorBanner(state.error?.resolve())
                    MfaCodeFields(state, viewModel)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::submitCode, enabled = state.canSubmitCode) { Text(stringResource(Res.string.mfa_login_button)) }
            },
            dismissButton = { TextButton(onClick = viewModel::onMfaCancelled) { Text(stringResource(Res.string.auth_cancel)) } },
        )
    }

    if (state.askBirthDate) {
        BirthDatePickerDialog(
            title = Res.string.auth_google_birth_date_title,
            onPicked = viewModel::onGoogleBirthDate,
            onDismiss = viewModel::onBirthDateDismissed,
        )
    }
}

// Pulsante secondo le linee guida di Google (Sign in with Google, forma a pillola): logo "G" a colori a sinistra del
// testo, fondo bianco con bordo grigio in chiaro, quasi nero con bordo grigio in scuro. Colori del brand, non del tema:
// sono un'eccezione alla regola dei colori solo da MaterialTheme, come le anteprime della mappa.
@Composable
private fun GoogleSignInButton(onClick: () -> Unit, loading: Boolean) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val container = if (dark) Color(0xFF131314) else Color(0xFFFFFFFF)
    val content = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    val stroke = if (dark) Color(0xFF8E918F) else Color(0xFF747775)
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
        shape = CircleShape,
        border = BorderStroke(1.dp, stroke),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content.copy(alpha = 0.6f),
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = content)
        } else {
            // il logo è decorativo: il testo dice già "Google"
            Image(GoogleLogo, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            // Roboto Medium come chiedono le linee guida: il font di sistema (Roboto su Android)
            Text(
                stringResource(Res.string.auth_google_button),
                style = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.1.sp),
            )
        }
    }
}

// La "G" ufficiale, dal kit di Google (viewBox 48×48)
private val GoogleLogo: ImageVector by lazy {
    fun ImageVector.Builder.part(color: Long, d: String) =
        addPath(pathData = addPathNodes(d), fill = SolidColor(Color(color)))
    ImageVector.Builder("GoogleG", 20.dp, 20.dp, 48f, 48f)
        .part(0xFFEA4335, "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z")
        .part(0xFF4285F4, "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z")
        .part(0xFFFBBC05, "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z")
        .part(0xFF34A853, "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z")
        .build()
}
