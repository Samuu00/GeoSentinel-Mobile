// Punto di accesso condiviso per l'azione "Indicazioni".
// Qualsiasi schermata (rifugio, POI, pericolo) può chiedere un percorso verso un luogo senza conoscere
// la navigazione dell'app: basta leggere questa CompositionLocal e chiamare la funzione che contiene.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Azione "Indicazioni" verso un punto con un nome: porta l'utente sulla mappa e chiede il percorso al motore
 * di instradamento del backend (`GET /trails/route`), senza mai aprire l'app di mappe del telefono.
 *
 * La funzione riceve il nome del luogo e le sue coordinate. La fornisce `App`; fuori da `App`
 * (anteprime, test) il valore predefinito non fa nulla.
 */
val LocalDirections = staticCompositionLocalOf<(name: String, lat: Double, lon: Double) -> Unit> { { _, _, _ -> } }
