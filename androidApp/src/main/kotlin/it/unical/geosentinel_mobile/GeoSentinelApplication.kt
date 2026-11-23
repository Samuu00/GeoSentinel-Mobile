// Classe Application dell'app Android: il primo codice che gira all'avvio del processo. Avvia Koin con la
// configurazione presa da BuildConfig e prepara le notifiche push (canale delle notifiche e token di Firebase).
package it.unical.geosentinel_mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.di.initKoin
import it.unical.geosentinel_mobile.core.di.pushManager

/**
 * Punto d'ingresso del processo Android. Koin si avvia qui e non in MainActivity, perché l'Activity viene
 * ricreata a ogni rotazione e un secondo avvio di Koin lancerebbe KoinApplicationAlreadyStartedException.
 */
class GeoSentinelApplication : Application() {
    /** Avvia Koin con indirizzo dell'API, modalità debug e client ID di Google (null se non configurato), poi le push. */
    override fun onCreate() {
        super.onCreate()
        initKoin(
            context = this,
            config = AppConfig(
                apiBaseUrl = BuildConfig.API_BASE_URL,
                isDebug = BuildConfig.DEBUG,
                googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { null },
            ),
        )
        startPush()
    }

    /**
     * Crea il canale delle notifiche SOS (da Android 8) e collega Firebase a PushManager. onNewToken di Firebase
     * arriva solo quando il token viene creato o rinnovato, quindi quello attuale si chiede a ogni avvio. Senza
     * google-services.json Firebase non è inizializzato e le push restano spente.
     */
    private fun startPush() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.sos_channel_id),
                getString(R.string.sos_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.sos_channel_description) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        if (FirebaseApp.getApps(this).isEmpty()) return
        val messaging = FirebaseMessaging.getInstance()
        messaging.token.addOnSuccessListener { token -> pushManager().onNewToken(token) }
        // A fine sessione il token viene invalidato, così le push dell'utente uscito non arrivano più a questo telefono,
        // e se ne chiede uno nuovo, che l'app registrerà al prossimo login.
        pushManager().tokenRevoker = {
            messaging.deleteToken().addOnCompleteListener {
                messaging.token.addOnSuccessListener { token -> pushManager().onNewToken(token) }
            }
        }
    }
}
