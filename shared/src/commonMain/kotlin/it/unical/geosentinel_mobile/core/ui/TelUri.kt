package it.unical.geosentinel_mobile.core.ui

// Fuori da DialHelper.kt: lì c'è l'expect, e su Android il file actual con lo stesso nome darebbe la stessa classe JVM
/**
 * L'URI `tel:` con solo cifre e il "+" iniziale: il numero arriva dal server (es. il telefono di un rifugio, testo
 * scritto dal gestore) e nel compositore non deve entrare altro (niente `*` o `#` dei codici di servizio).
 */
internal fun telUri(phoneNumber: String): String =
    "tel:" + phoneNumber.trim().let { n -> (if (n.startsWith("+")) "+" else "") + n.filter(Char::isDigit) }
