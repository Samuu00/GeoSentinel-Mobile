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

@Composable
actual fun SystemBarsAppearance(mode: ThemeMode, darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    // al cambio di tema (setApplicationNightMode, qui sotto) il sistema rimette le icone di default: leggere la
    // configurazione fa ricomporre e riapplicare la scelta anche quando [darkTheme] non è cambiato
    val uiMode = LocalConfiguration.current.uiMode
    SideEffect {
        uiMode.hashCode()
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    // Android 12+: il sistema ricorda la scelta del Profilo, così all'avvio successivo lo sfondo prima di Compose
    // (values-night) è già quello giusto, senza un lampo chiaro prima dell'app scura o il contrario
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
