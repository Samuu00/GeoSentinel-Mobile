package it.unical.geosentinel_mobile.core.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.net.Uri

@Composable
actual fun rememberDialer(): (phoneNumber: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { phoneNumber ->
            try {
                // ACTION_DIAL non richiede permessi: apre il compositore, la chiamata la fa l'utente
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
            } catch (_: ActivityNotFoundException) {
                // nessuna app telefono (es. tablet senza SIM): non c'è niente da aprire
            }
        }
    }
}
