// Implementazione Android del controllo sulle animazioni ridotte.
// Android non ha un interruttore "Riduci movimento" come iOS: si usa la scala delle animazioni di sistema.
package it.unical.geosentinel_mobile.core.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Restituisce vero se la scala delle animazioni di sistema è 0, cioè se l'utente ha attivato
 * "Rimuovi animazioni" (accessibilità) o ha azzerato la scala nelle opzioni sviluppatore.
 * Se l'impostazione non esiste si assume la scala normale (1).
 */
@Composable
internal actual fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
