package it.unical.geosentinel_mobile.core.ui

// geo: con la query sul punto: Google Maps (o un'altra app di mappe) mette il segnaposto e offre le indicazioni
actual fun directionsUri(lat: Double, lon: Double): String = "geo:$lat,$lon?q=$lat,$lon"
