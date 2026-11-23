// Implementazione Android del login con Google (vedi rememberGoogleSignIn in commonMain). Usa Credential Manager per
// ottenere un ID token di Google, che poi il codice comune invia al backend.
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

/**
 * Prepara il login con Google tramite Credential Manager, nel flusso del pulsante "Accedi con Google" che mostra sempre
 * il selettore degli account. Il contesto usato è quello di LocalContext, cioè l'Activity: il selettore viene mostrato
 * come un suo foglio. Restituisce null se [webClientId] non è configurato (in quel caso il pulsante non compare);
 * altrimenti una funzione sospesa che restituisce il token, l'annullamento da parte dell'utente o l'indisponibilità.
 */
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
