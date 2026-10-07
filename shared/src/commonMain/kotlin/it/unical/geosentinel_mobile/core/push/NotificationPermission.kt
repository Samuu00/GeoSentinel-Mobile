package it.unical.geosentinel_mobile.core.push

import androidx.compose.runtime.Composable

/**
 * Chiede il permesso delle notifiche quando entra nella composizione, se non c'è già (M7.1). App lo mette solo quando
 * serve: l'operatore da loggato, l'escursionista dal primo SOS. Rifiutato, il sistema non lo ripropone all'infinito.
 */
@Composable
expect fun RequestNotificationPermission()
