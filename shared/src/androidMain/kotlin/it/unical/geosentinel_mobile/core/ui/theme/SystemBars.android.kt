// Implementazione Android dell'allineamento tra tema dell'app e barre di sistema.
// Imposta il colore delle icone di stato e di navigazione e comunica al sistema la scelta del tema,
// così anche la schermata di avvio usa i colori giusti.
package it.unical.geosentinel_mobile.core.ui.theme

import android.app.Activity
import android.app.UiModeManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Rende chiare le icone di sistema sul tema scuro e scure sul tema chiaro, e su Android 12 e successivi
 * salva la scelta del tema presso il sistema con `setApplicationNightMode`. Nell'anteprima dell'editor non fa nulla.
 */
@Composable
actual fun SystemBarsAppearance(mode: ThemeMode, darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    // Quando cambia il tema con setApplicationNightMode (più sotto) il sistema ripristina le icone predefinite.
    // Leggere la configurazione fa ricomporre questo composable e riapplicare la scelta anche quando
    // darkTheme non è cambiato.
    val uiMode = LocalConfiguration.current.uiMode
    SideEffect {
        // Lettura senza effetto: serve solo a rendere esplicita la dipendenza da uiMode.
        uiMode.hashCode()
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    // Da Android 12 il sistema ricorda la scelta fatta nel Profilo: all'avvio successivo lo sfondo mostrato
    // prima di Compose (le risorse values-night) è già quello giusto, senza un lampo chiaro prima dell'app
    // scura o viceversa.
    LaunchedEffect(mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        val night = when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        view.context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(night)
    }
}
