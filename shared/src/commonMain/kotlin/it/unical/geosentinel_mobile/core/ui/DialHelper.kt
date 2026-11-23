// Dichiarazione comune dell'apertura del compositore telefonico.
// Usata da "Chiama 112" nella schermata SOS e da "Chiama" nella pagina del rifugio.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable

/**
 * Restituisce una funzione che apre il compositore del telefono con il numero già scritto (URI `tel:`).
 *
 * La chiamata non parte da sola: è l'utente a confermarla. È un Composable perché su Android serve il
 * Context dell'Activity. Su un dispositivo senza telefonia (tablet, simulatore) la funzione non fa nulla.
 */
@Composable
expect fun rememberDialer(): (phoneNumber: String) -> Unit
