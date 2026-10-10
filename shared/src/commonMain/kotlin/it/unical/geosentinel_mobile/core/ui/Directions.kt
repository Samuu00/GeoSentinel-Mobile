package it.unical.geosentinel_mobile.core.ui

/**
 * L'indirizzo che apre le mappe del telefono con le indicazioni fino a ([lat], [lon]): `geo:` su Android (sceglie l'utente
 * l'app), Apple Mappe su iOS. Si apre con `LocalUriHandler`.
 */
expect fun directionsUri(lat: Double, lon: Double): String
