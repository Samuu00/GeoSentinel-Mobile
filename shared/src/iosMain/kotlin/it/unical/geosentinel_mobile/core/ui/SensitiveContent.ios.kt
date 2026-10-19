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

// iOS non ha un FLAG_SECURE: l'istantanea per il selettore delle app si fa dopo WillResignActive, quindi lì si copre
// la finestra con un fondo pieno e al ritorno lo si toglie. Gli screenshot fatti dall'utente non si possono bloccare.
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
            // nero pieno: systemBackgroundColor (categoria di UIColor) non è visibile da Kotlin/Native
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

@OptIn(ExperimentalComposeUiApi::class)
actual fun sensitiveClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)
