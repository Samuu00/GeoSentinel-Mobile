// Configurazione dell'app letta all'avvio dalla piattaforma.
// Su Android i valori arrivano da BuildConfig, su iOS da Info.plist; il codice condiviso li riceve
// già pronti in questa classe e la passa a Koin, che la rende disponibile a chi ne ha bisogno.
package it.unical.geosentinel_mobile.core.config

/**
 * Parametri di configurazione che cambiano tra le build (debug, staging, release) e tra le piattaforme.
 *
 * @property apiBaseUrl indirizzo base del backend; è anche l'unico host a cui viene inviato il token di accesso.
 * @property isDebug vero nelle build di sviluppo: abilita, per esempio, i log di livello Debug.
 */
data class AppConfig(
    val apiBaseUrl: String,
    val isDebug: Boolean,
    // Client ID di tipo "Web" usato dal login con Google su Android. Se è null il login con Google
    // non è configurato e il relativo pulsante non viene mostrato.
    val googleWebClientId: String? = null,
)
