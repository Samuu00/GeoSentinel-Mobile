// Implementazione iOS dell'apertura del compositore telefonico.
// Passa l'URI `tel:` a UIApplication, che mostra la conferma di sistema prima di chiamare.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** Restituisce la funzione che apre il numero con `openURL`; la funzione viene creata una volta e ricordata. */
@Composable
actual fun rememberDialer(): (phoneNumber: String) -> Unit = remember {
    { phoneNumber ->
        // Non si usa canOpenURL: per lo schema "tel" richiederebbe di dichiarare LSApplicationQueriesSchemes
        // in Info.plist. Sul simulatore, che non ha la telefonia, openURL semplicemente non fa nulla.
        NSURL.URLWithString(telUri(phoneNumber))?.let { url ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }
}
