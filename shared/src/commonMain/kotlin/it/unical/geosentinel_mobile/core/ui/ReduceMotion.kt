// Dichiarazione comune per l'impostazione di accessibilità che riduce le animazioni.
// I componenti animati dell'app la consultano per rispettare la preferenza dell'utente.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable

/**
 * Restituisce vero se l'utente ha chiesto meno animazioni: "Riduci movimento" su iOS, animazioni disattivate
 * nelle opzioni sviluppatore o di accessibilità su Android.
 *
 * Quando è attivo, le sagome di caricamento non pulsano e la spunta di conferma non viene disegnata
 * progressivamente: gli elementi compaiono direttamente.
 */
@Composable
internal expect fun rememberReduceMotion(): Boolean
