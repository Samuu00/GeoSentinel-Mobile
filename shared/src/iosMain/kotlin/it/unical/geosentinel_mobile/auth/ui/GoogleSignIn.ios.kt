// Implementazione iOS del login con Google. L'SDK GoogleSignIn è una libreria Swift e non si può chiamare direttamente
// da Kotlin: il lato Swift registra qui un launcher e il codice comune lo usa attraverso rememberGoogleSignIn.
package it.unical.geosentinel_mobile.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Ponte verso l'SDK GoogleSignIn, usato dal codice Swift in `AppDelegate` (lo stesso schema di `PushBridge` per
 * Firebase). Swift imposta il launcher solo se nell'Info.plist c'è `GIDClientID` (il client OAuth di tipo iOS); se manca,
 * il launcher resta null e il pulsante di Google non compare.
 */
object GoogleSignInBridge {
    private var launcher: ((GoogleSignInCallback) -> Unit)? = null

    /** Chiamata da Swift all'avvio: registra la funzione che apre il login di Google e risponde sul callback. */
    fun setLauncher(launcher: (GoogleSignInCallback) -> Unit) {
        this.launcher = launcher
    }

    /** Il launcher registrato, oppure null se Google non è configurato. */
    internal val current get() = launcher
}

/**
 * Callback passato a Swift per un singolo tentativo di login. Accetta una sola risposta: le chiamate successive alla
 * prima vengono ignorate, così la coroutine in attesa non viene ripresa due volte.
 */
class GoogleSignInCallback internal constructor(private val onResult: (GoogleSignInResult) -> Unit) {
    private var done = false

    /** Login riuscito: [idToken] è l'ID token di Google da inviare al backend. */
    fun onToken(idToken: String) = finish(GoogleSignInResult.Token(idToken))
    /** L'utente ha chiuso il login senza scegliere un account. */
    fun onCancelled() = finish(GoogleSignInResult.Cancelled)
    /** Errore dell'SDK: per l'app il login con Google non è disponibile. */
    fun onFailed() = finish(GoogleSignInResult.Unavailable)

    /** Consegna il risultato solo la prima volta. */
    private fun finish(result: GoogleSignInResult) {
        if (done) return
        done = true
        onResult(result)
    }
}

/**
 * Prepara il login con Google su iOS. Restituisce null se Swift non ha registrato un launcher; altrimenti una funzione
 * sospesa che apre il login e attende la risposta del callback. [webClientId] qui non serve: su iOS l'audience dell'ID
 * token è il client iOS, che il backend conosce nella variabile GOOGLE_CLIENT_IDS.
 */
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
