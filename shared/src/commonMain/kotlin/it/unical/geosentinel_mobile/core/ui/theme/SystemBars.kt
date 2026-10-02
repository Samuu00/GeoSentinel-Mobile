package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.runtime.Composable

// Le icone di stato (ora, batteria) seguono il tema del sistema, non quello dell'app:
// con il tema forzato vanno allineate, altrimenti diventano invisibili sullo sfondo.
@Composable
expect fun SystemBarsAppearance(mode: ThemeMode, darkTheme: Boolean)
