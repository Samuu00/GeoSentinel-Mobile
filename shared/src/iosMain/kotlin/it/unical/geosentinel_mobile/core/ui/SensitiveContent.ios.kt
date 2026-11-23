// Implementazione iOS della protezione dei contenuti sensibili.
// iOS non offre un equivalente di FLAG_SECURE: si può solo coprire il contenuto prima che il sistema
// scatti l'istantanea per il selettore delle app.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * Copre la finestra con una vista piena quando l'app sta per uscire dal primo piano e la rimuove quando torna
 * attiva. L'istantanea per il selettore delle app viene presa dopo `WillResignActive`, quindi mostra la copertura
 * e non i codici. Gli screenshot fatti dall'utente, invece, su iOS non si possono bloccare.
 * Gli osservatori vengono rimossi quando il composable esce dalla composizione.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun SecureScreen() {
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        var cover: UIView? = null
        val resign = center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            @Suppress("DEPRECATION")
            val window = UIApplication.sharedApplication.keyWindow ?: return@addObserverForName
            cover?.removeFromSuperview()
            // Nero pieno: systemBackgroundColor è definito in una categoria di UIColor che Kotlin/Native non vede.
            val view = UIView(frame = window.bounds).apply { backgroundColor = UIColor.blackColor }
            window.addSubview(view)
            cover = view
        }
        val active = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            cover?.removeFromSuperview()
            cover = null
        }
        onDispose {
            center.removeObserver(resign)
            center.removeObserver(active)
            cover?.removeFromSuperview()
        }
    }
}

/** Su iOS non esiste un segnale di testo sensibile per gli appunti: si crea una voce di testo semplice. */
@OptIn(ExperimentalComposeUiApi::class)
actual fun sensitiveClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)
