// Costruzione dell'URI `tel:` usato per aprire il compositore del telefono.
// Sta in un file separato da DialHelper.kt perché lì c'è la dichiarazione `expect`: su Android il file
// `actual` con lo stesso nome genererebbe la stessa classe JVM e le due andrebbero in conflitto.
package it.unical.geosentinel_mobile.core.ui

/**
 * Restituisce l'URI `tel:` per [phoneNumber] tenendo solo le cifre e l'eventuale "+" iniziale.
 *
 * Il numero arriva dal server (per esempio il telefono di un rifugio, scritto a mano dal gestore), quindi
 * viene ripulito: nel compositore non devono finire altri caratteri, in particolare `*` e `#` dei codici
 * di servizio dell'operatore.
 */
internal fun telUri(phoneNumber: String): String =
    "tel:" + phoneNumber.trim().let { n -> (if (n.startsWith("+")) "+" else "") + n.filter(Char::isDigit) }
