package it.unical.geosentinel_mobile.auth.ui

import androidx.compose.runtime.Composable

// ponytail: su iOS serve l'SDK GoogleSignIn (SPM nel progetto Xcode, URL scheme del client iOS) e un ponte da Swift,
// come per Firebase: si fa con il Mac. Fino ad allora niente pulsante, si entra con email e password.
@Composable
actual fun rememberGoogleSignIn(webClientId: String?): (suspend () -> GoogleSignInResult)? = null
