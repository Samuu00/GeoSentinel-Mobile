package it.unical.geosentinel_mobile.core.config

data class AppConfig(
    val apiBaseUrl: String,
    val isDebug: Boolean,
    // client ID "Web" per il login con Google (Android); null: login Google non configurato, il pulsante non c'è
    val googleWebClientId: String? = null,
)
