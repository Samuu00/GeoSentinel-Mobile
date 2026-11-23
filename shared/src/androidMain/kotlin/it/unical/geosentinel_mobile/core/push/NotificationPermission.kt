// Implementazione Android della richiesta del permesso delle notifiche.
// Da Android 13 (API 33) mostrare notifiche richiede il permesso runtime POST_NOTIFICATIONS.
package it.unical.geosentinel_mobile.core.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Se il permesso non è ancora concesso, mostra il dialogo di sistema per POST_NOTIFICATIONS.
 * L'esito non viene usato: se l'utente rifiuta, le notifiche semplicemente non compaiono.
 */
@Composable
actual fun RequestNotificationPermission() {
    // Fino ad Android 12 le notifiche non richiedono alcun permesso runtime.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
