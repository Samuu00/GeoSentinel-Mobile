// Implementazione iOS del controllo "Riduci movimento".
// Legge l'impostazione di accessibilità di UIKit con cui l'utente chiede meno animazioni.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** Restituisce vero se in iOS è attivo "Riduci movimento"; il valore viene letto una volta e ricordato. */
@Composable
internal actual fun rememberReduceMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
