// Dichiarazione comune per la richiesta del permesso delle notifiche.
// Le notifiche push servono agli operatori (nuove allerte) e a chi ha lanciato un SOS (aggiornamenti
// sulla squadra); l'implementazione cambia tra Android e iOS.
package it.unical.geosentinel_mobile.core.push

import androidx.compose.runtime.Composable

/**
 * Chiede il permesso delle notifiche nel momento in cui entra nella composizione, se non è già stato concesso.
 *
 * `App` lo inserisce solo quando serve davvero: per l'operatore subito dopo il login, per l'escursionista
 * al primo SOS. Se l'utente rifiuta, è il sistema a non riproporre la richiesta all'infinito.
 */
@Composable
expect fun RequestNotificationPermission()
