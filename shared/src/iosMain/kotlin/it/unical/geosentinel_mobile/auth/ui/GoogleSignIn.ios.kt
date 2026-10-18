package it.unical.geosentinel_mobile.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Ponte verso l'SDK GoogleSignIn (Swift, `AppDelegate`), come `PushBridge` per Firebase. Swift imposta il launcher
 * solo se `GIDClientID` (client OAuth di tipo iOS) è nell'Info.plist; senza, il pulsante non compare.
 */
object GoogleSignInBridge {
    private var launcher: ((GoogleSignInCallback) -> Unit)? = null

    fun setLauncher(launcher: (GoogleSignInCallback) -> Unit) {
        this.launcher = launcher
    }

    internal val current get() = launcher
}

// Una sola risposta per tentativo: le chiamate dopo la prima si ignorano
class GoogleSignInCallback internal constructor(private val onResult: (GoogleSignInResult) -> Unit) {
    private var done = false

    fun onToken(idToken: String) = finish(GoogleSignInResult.Token(idToken))
    fun onCancelled() = finish(GoogleSignInResult.Cancelled)
    fun onFailed() = finish(GoogleSignInResult.Unavailable)

    private fun finish(result: GoogleSignInResult) {
        if (done) return
        done = true
        onResult(result)
    }
}

// webClientId non serve: su iOS l'audience dell'ID token è il client iOS, che il backend ha in GOOGLE_CLIENT_IDS
@Composable
actual fun rememberGoogleSignIn(webClientId: String?): (suspend () -> GoogleSignInResult)? {
    val launcher = GoogleSignInBridge.current ?: return null
    return remember(launcher) {
        {
            suspendCancellableCoroutine { continuation ->
                launcher(GoogleSignInCallback { result -> if (continuation.isActive) continuation.resume(result) })
            }
        }
    }
}
