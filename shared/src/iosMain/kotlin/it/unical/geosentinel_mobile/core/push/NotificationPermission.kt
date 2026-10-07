package it.unical.geosentinel_mobile.core.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

// Già concesso o già rifiutato: iOS non mostra di nuovo il dialogo. La registrazione APNs la fa l'AppDelegate.
@Composable
actual fun RequestNotificationPermission() {
    LaunchedEffect(Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> }
    }
}
