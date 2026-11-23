// Implementazione iOS dell'allineamento tra tema dell'app e aspetto del sistema.
// Su iOS non si cambiano le icone di stato una per una: si forza lo stile chiaro o scuro dell'intera finestra.
package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle

/**
 * Imposta `overrideUserInterfaceStyle` della finestra principale in base alla scelta dell'utente.
 *
 * Con [ThemeMode.SYSTEM] la forzatura viene rimossa: se restasse, `isSystemInDarkTheme` leggerebbe lo stile
 * imposto dall'app e non più quello del sistema. Il parametro [darkTheme] qui non serve, perché lo stile della
 * finestra determina già il colore delle icone.
 *
 * Limite noto: `keyWindow` è deprecata ma è sufficiente finché l'app ha una sola scena; con più finestre
 * bisognerebbe passare dalla `UIWindowScene`.
 */
@Composable
actual fun SystemBarsAppearance(mode: ThemeMode, darkTheme: Boolean) {
    LaunchedEffect(mode) {
        UIApplication.sharedApplication.keyWindow?.overrideUserInterfaceStyle = when (mode) {
            ThemeMode.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
    }
}
