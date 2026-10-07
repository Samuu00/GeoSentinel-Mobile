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

// Koin parte qui e non in MainActivity: l'Activity si ricrea a ogni rotazione,
// e un secondo startKoin lancerebbe KoinApplicationAlreadyStartedException.
class GeoSentinelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            context = this,
            config = AppConfig(apiBaseUrl = BuildConfig.API_BASE_URL, isDebug = BuildConfig.DEBUG),
        )
        startPush()
    }

    // Push (M7.1). onNewToken arriva solo quando FCM crea o rinnova il token: quello attuale si chiede a ogni avvio.
    // Senza google-services.json Firebase non parte (FirebaseApp assente) e le push restano spente.
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
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> pushManager().onNewToken(token) }
    }
}
