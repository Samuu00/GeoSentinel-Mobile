// Implementazione Android della protezione dei contenuti sensibili.
// Usa il flag FLAG_SECURE della finestra e, per gli appunti, il segnale di contenuto sensibile di Android 13.
package it.unical.geosentinel_mobile.core.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext

/**
 * Aggiunge FLAG_SECURE alla finestra dell'Activity mentre il composable è visibile e lo toglie quando esce
 * dalla composizione. Con il flag attivo il sistema blocca screenshot e registrazioni e mostra un riquadro
 * vuoto nell'elenco delle app recenti. Se non si trova un'Activity (per esempio in anteprima) non fa nulla.
 */
@Composable
actual fun SecureScreen() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * Crea una voce di testo per gli appunti. Da Android 13 la segna con EXTRA_IS_SENSITIVE, così il sistema
 * non ne mostra il contenuto nell'anteprima che compare dopo la copia.
 */
actual fun sensitiveClipEntry(text: String): ClipEntry {
    val clip = ClipData.newPlainText("GeoSentinel", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    return ClipEntry(clip)
}

/** Risale la catena dei ContextWrapper fino all'Activity; null se il Context non appartiene a un'Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
