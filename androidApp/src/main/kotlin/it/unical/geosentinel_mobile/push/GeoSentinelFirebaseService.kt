// Servizio di Firebase Cloud Messaging: riceve il token del dispositivo e i messaggi push quando l'app è in primo
// piano, e in quel caso mostra la notifica di sistema per le allerte SOS.
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
 * Servizio delle push FCM. Con l'app in background o chiusa la notifica la mostra FCM con il testo del server, e
 * il tocco apre MainActivity con `type` e `alertId` negli extra. Questo servizio riceve il messaggio solo con l'app
 * in primo piano: allora la notifica la mostra l'app, con gli stessi extra, così il tocco segue la stessa strada.
 */
class GeoSentinelFirebaseService : FirebaseMessagingService() {

    /** Inoltra a PushManager il nuovo token del dispositivo, che verrà registrato sul backend. */
    override fun onNewToken(token: String) {
        pushManager().onNewToken(token)
    }

    /**
     * Gestisce una push ricevuta in primo piano: un tipo sconosciuto o un contenuto incompleto vengono ignorati,
     * altrimenti PushManager viene avvisato e si mostra la notifica.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val event = PushEvent.fromPayload(message.data) ?: return
        pushManager().onMessage(message.data)
        showNotification(event, message.data)
    }

    /** Costruisce e mostra la notifica di un evento SOS con titolo e testo localizzati. */
    private fun showNotification(event: PushEvent, data: Map<String, String>) {
        val (title, body) = when (event) {
            is PushEvent.SosRaised -> R.string.push_sos_raised_title to R.string.push_sos_raised_body
            is PushEvent.SosDispatched -> R.string.push_sos_dispatched_title to R.string.push_sos_dispatched_body
            is PushEvent.SosResolved -> R.string.push_sos_resolved_title to R.string.push_sos_resolved_body
        }
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { data.forEach { (key, value) -> putExtra(key, value) } }
        // Un id per allerta: una notifica ripetuta per la stessa allerta sostituisce la prima invece di aggiungersi.
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
            // Sulla schermata di blocco compare solo "GeoSentinel": chi guarda il telefono bloccato non scopre che c'è
            // un'emergenza in corso.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(this, getString(R.string.sos_channel_id))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.push_public_body))
                    .build(),
            )
            .build()
        // Senza il permesso delle notifiche (Android 13 in poi) il sistema la scarta da solo.
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
