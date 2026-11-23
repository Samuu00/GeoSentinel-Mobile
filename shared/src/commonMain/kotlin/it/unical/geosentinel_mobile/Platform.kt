// Dichiarazione comune della piattaforma su cui gira l'app. È un esempio del meccanismo expect/actual di Kotlin
// Multiplatform: qui c'è solo il contratto, le implementazioni stanno in androidMain e iosMain.
package it.unical.geosentinel_mobile

/** Descrive il sistema operativo corrente. */
interface Platform {
    /** Nome e versione del sistema, per esempio "Android 35" o "iOS 18.1". */
    val name: String
}

/** Restituisce la piattaforma corrente; ogni target fornisce la propria implementazione (actual). */
expect fun getPlatform(): Platform