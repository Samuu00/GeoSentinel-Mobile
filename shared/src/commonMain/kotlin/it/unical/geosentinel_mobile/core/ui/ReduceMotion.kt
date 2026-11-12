package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable

/**
 * "Riduci movimento" del sistema (iOS) o animazioni spente nelle opzioni sviluppatore / accessibilità (Android).
 * Se è attivo niente respiro delle sagome di caricamento e niente spunta disegnata: le cose compaiono e basta.
 */
@Composable
internal expect fun rememberReduceMotion(): Boolean
