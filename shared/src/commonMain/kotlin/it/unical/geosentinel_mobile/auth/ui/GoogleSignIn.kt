package it.unical.geosentinel_mobile.auth.ui

import androidx.compose.runtime.Composable

// Esito del selettore di account Google della piattaforma
sealed interface GoogleSignInResult {
    // l'ID token non si stampa: vale circa un'ora come credenziale
    class Token(val idToken: String) : GoogleSignInResult {
        override fun toString() = "Token(…)"
    }
    // l'utente ha chiuso il selettore: nessun messaggio
    data object Cancelled : GoogleSignInResult
    // nessun account Google sul dispositivo, Play Services assenti o errore del SDK
    data object Unavailable : GoogleSignInResult
}

/**
 * Apre il selettore di account Google e restituisce l'ID token per `POST /auth/google` (ADR-0024 del backend).
 * Null se il login con Google non è configurato su questa piattaforma ([webClientId] assente, o iOS finché non c'è
 * l'SDK GoogleSignIn): allora il pulsante non si mostra.
 */
@Composable
expect fun rememberGoogleSignIn(webClientId: String?): (suspend () -> GoogleSignInResult)?
