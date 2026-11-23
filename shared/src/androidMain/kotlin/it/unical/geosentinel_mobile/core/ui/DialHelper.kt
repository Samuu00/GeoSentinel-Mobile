// Implementazione Android dell'apertura del compositore telefonico.
// Usa l'intent ACTION_DIAL, che mostra il numero senza avviare la chiamata.
package it.unical.geosentinel_mobile.core.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.net.Uri

/** Restituisce la funzione che apre il compositore con il numero; viene ricreata solo se cambia il Context. */
@Composable
actual fun rememberDialer(): (phoneNumber: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { phoneNumber ->
            try {
                // ACTION_DIAL non richiede il permesso CALL_PHONE: apre il compositore e la chiamata la avvia l'utente.
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(telUri(phoneNumber))))
            } catch (_: ActivityNotFoundException) {
                // Nessuna app telefono installata (per esempio un tablet senza SIM): non c'è nulla da aprire.
            }
        }
    }
}
