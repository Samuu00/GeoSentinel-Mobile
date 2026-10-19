package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry

/**
 * Finché è nella composizione, lo schermo non finisce in screenshot, registrazioni e anteprime dell'elenco delle app:
 * `FLAG_SECURE` su Android, una copertura quando l'app esce dal primo piano su iOS. Per i codici di recupero (M10.2).
 */
@Composable
expect fun SecureScreen()

/** Testo per gli appunti segnato come sensibile (Android 13+: il sistema non lo mostra nell'anteprima). */
expect fun sensitiveClipEntry(text: String): ClipEntry
