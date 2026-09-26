package it.unical.geosentinel_mobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform