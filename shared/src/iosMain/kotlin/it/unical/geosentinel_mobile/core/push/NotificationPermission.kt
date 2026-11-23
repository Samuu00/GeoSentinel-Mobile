// Implementazione iOS della richiesta del permesso delle notifiche.
// Si limita al dialogo di autorizzazione: la registrazione presso APNs la esegue l'AppDelegate in Swift.
package it.unical.geosentinel_mobile.core.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Chiede l'autorizzazione per avvisi, suoni e badge. Se l'utente ha già risposto (sì o no), iOS non mostra
 * di nuovo il dialogo, quindi la richiesta si può fare ogni volta senza disturbare. L'esito non serve qui.
 */
@Composable
actual fun RequestNotificationPermission() {
    LaunchedEffect(Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> }
    }
}
