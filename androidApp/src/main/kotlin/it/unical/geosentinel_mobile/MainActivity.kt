// Activity unica dell'app Android: abilita il disegno fino ai bordi dello schermo, mostra l'interfaccia Compose
// condivisa (App) e inoltra a PushManager le notifiche toccate dall'utente.
package it.unical.geosentinel_mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.unical.geosentinel_mobile.core.di.pushManager

/** Activity che ospita tutta l'interfaccia Compose e riceve i tocchi sulle notifiche push. */
class MainActivity : ComponentActivity() {
    /** Abilita l'edge-to-edge, gestisce l'eventuale notifica che ha aperto l'app e mostra l'interfaccia. */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Solo alla prima creazione: dopo una rotazione l'intent è lo stesso e la notifica è già stata aperta.
        if (savedInstanceState == null) openPush(intent)

        setContent {
            App()
        }
    }

    /** Riceve il tocco su una notifica quando l'app è già aperta (launchMode singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPush(intent)
    }

    /**
     * Passa a PushManager gli extra dell'intent, che sono il contenuto della push (type e alertId), sia per le
     * notifiche mostrate da FCM sia per quelle mostrate dall'app. Se l'app viene riaperta dalle app recenti l'intent è
     * quello vecchio e la notifica viene ignorata.
     */
    private fun openPush(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        pushManager().onNotificationOpened(mapOf("type" to intent.getStringExtra("type"), "alertId" to intent.getStringExtra("alertId")))
    }
}
