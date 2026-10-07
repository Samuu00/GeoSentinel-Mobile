package it.unical.geosentinel_mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.unical.geosentinel_mobile.core.di.pushManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Solo al primo avvio: dopo una rotazione l'intent è lo stesso e la notifica è già stata aperta
        if (savedInstanceState == null) openPush(intent)

        setContent {
            App()
        }
    }

    // Tocco su una notifica con l'app già aperta (launchMode singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPush(intent)
    }

    // Gli extra sono il payload della push (type, alertId), sia dalla notifica di FCM sia dalla nostra.
    // Riaperta dalle app recenti, l'intent è quello vecchio: niente deep link.
    private fun openPush(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        pushManager().onNotificationOpened(mapOf("type" to intent.getStringExtra("type"), "alertId" to intent.getStringExtra("alertId")))
    }
}
