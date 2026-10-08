package it.unical.geosentinel_mobile.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import it.unical.geosentinel_mobile.MainActivity
import it.unical.geosentinel_mobile.R
import it.unical.geosentinel_mobile.core.di.pushManager
import it.unical.geosentinel_mobile.core.push.PushEvent

/**
 * Push FCM (M7.1). Con l'app in background (o chiusa) la notifica la mostra FCM con il testo del server, e il tocco
 * apre MainActivity con `type` e `alertId` negli extra. Qui si arriva solo con l'app in primo piano: la notifica la
 * mostriamo noi, con gli stessi extra, così il tocco segue la stessa strada.
 */
class GeoSentinelFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        pushManager().onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // type sconosciuto o payload incompleto: niente notifica
        val event = PushEvent.fromPayload(message.data) ?: return
        pushManager().onMessage(message.data)
        showNotification(event, message.data)
    }

    private fun showNotification(event: PushEvent, data: Map<String, String>) {
        val (title, body) = when (event) {
            is PushEvent.SosRaised -> R.string.push_sos_raised_title to R.string.push_sos_raised_body
            is PushEvent.SosDispatched -> R.string.push_sos_dispatched_title to R.string.push_sos_dispatched_body
            is PushEvent.SosResolved -> R.string.push_sos_resolved_title to R.string.push_sos_resolved_body
        }
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { data.forEach { (key, value) -> putExtra(key, value) } }
        // Un id per allerta: una notifica doppia (ADR-0016) sostituisce la prima invece di aggiungersi
        val id = event.alertId.hashCode()
        val pending = PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, getString(R.string.sos_channel_id))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(title))
            .setContentText(getString(body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(body)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            // Sulla schermata di blocco solo "GeoSentinel": chi guarda il telefono bloccato non scopre un'emergenza in corso
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(this, getString(R.string.sos_channel_id))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.push_public_body))
                    .build(),
            )
            .build()
        // Senza il permesso (Android 13+) il sistema la scarta da solo
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
