package it.unical.geosentinel_mobile.core.ui

// Apple Mappe con la destinazione: si apre l'app, non Safari
actual fun directionsUri(lat: Double, lon: Double): String = "maps://?daddr=$lat,$lon"
