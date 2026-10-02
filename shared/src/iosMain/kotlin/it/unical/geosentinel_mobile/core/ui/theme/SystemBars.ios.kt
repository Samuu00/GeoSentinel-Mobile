package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle

// Su iOS si forza lo stile della finestra; con SYSTEM si toglie la forzatura,
// altrimenti isSystemInDarkTheme leggerebbe il nostro override e non il sistema.
// ponytail: keyWindow è deprecata ma basta con una sola scena; con più finestre serve la UIWindowScene
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
