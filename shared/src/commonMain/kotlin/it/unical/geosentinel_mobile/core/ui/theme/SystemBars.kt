// Dichiarazione comune per l'aspetto delle barre di sistema (barra di stato e di navigazione).
// Ogni piattaforma fornisce la propria implementazione, perché l'API per cambiare il colore delle icone
// di sistema è diversa su Android e su iOS.
package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.runtime.Composable

/**
 * Allinea il colore delle icone di sistema (ora, batteria, rete) al tema scelto nell'app.
 *
 * Di base quelle icone seguono il tema del sistema operativo e non quello dell'app: se l'utente forza
 * il tema chiaro o scuro, senza questo allineamento le icone diventerebbero invisibili sullo sfondo.
 *
 * @param mode la scelta dell'utente (Sistema, Chiaro, Scuro).
 * @param darkTheme vero se l'app sta effettivamente usando il tema scuro.
 */
@Composable
expect fun SystemBarsAppearance(mode: ThemeMode, darkTheme: Boolean)
