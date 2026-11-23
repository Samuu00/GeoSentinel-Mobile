// Contratto comune del login con Google. Il selettore di account è diverso su ogni piattaforma (Credential
// Manager su Android, SDK GoogleSignIn su iOS), quindi qui si dichiarano solo l'esito e una funzione `expect`;
// le implementazioni stanno in androidMain e iosMain.
package it.unical.geosentinel_mobile.auth.ui

import androidx.compose.runtime.Composable

/** Esito del selettore di account Google della piattaforma, letto da [LoginViewModel]. */
sealed interface GoogleSignInResult {
    /**
     * L'utente ha scelto un account: [idToken] va inviato al backend. Il `toString` non lo stampa perché l'ID token
     * vale come credenziale per circa un'ora e non deve finire nei log.
     */
    class Token(val idToken: String) : GoogleSignInResult {
        override fun toString() = "Token(…)"
    }

    /** L'utente ha chiuso il selettore: non si mostra alcun messaggio. */
    data object Cancelled : GoogleSignInResult

    /** Login con Google non possibile: nessun account sul dispositivo, Play Services assenti o errore dell'SDK. */
    data object Unavailable : GoogleSignInResult
}

/**
 * Prepara il selettore di account Google e restituisce una funzione che lo apre e attende l'ID token da inviare a
 * `POST /auth/google`.
 *
 * Restituisce null quando il login con Google non è configurato su questa piattaforma (su Android manca
 * [webClientId], su iOS manca il ponte verso l'SDK nativo): in quel caso il pulsante "Continua con Google" non si
 * mostra.
 */
@Composable
expect fun rememberGoogleSignIn(webClientId: String?): (suspend () -> GoogleSignInResult)?
