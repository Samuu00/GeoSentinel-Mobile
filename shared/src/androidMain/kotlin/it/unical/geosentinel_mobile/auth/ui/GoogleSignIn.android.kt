package it.unical.geosentinel_mobile.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

// Credential Manager con il flusso del pulsante "Accedi con Google": mostra sempre il selettore degli account.
// Il contesto deve essere l'Activity (il selettore è un suo foglio), ed è quello di LocalContext.
@Composable
actual fun rememberGoogleSignIn(webClientId: String?): (suspend () -> GoogleSignInResult)? {
    val context = LocalContext.current
    if (webClientId == null) return null
    return remember(context, webClientId) {
        {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
            try {
                val credential = CredentialManager.create(context).getCredential(context, request).credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                } else {
                    GoogleSignInResult.Unavailable
                }
            } catch (_: GetCredentialCancellationException) {
                GoogleSignInResult.Cancelled
            } catch (_: GetCredentialException) {
                GoogleSignInResult.Unavailable
            } catch (_: GoogleIdTokenParsingException) {
                GoogleSignInResult.Unavailable
            }
        }
    }
}
