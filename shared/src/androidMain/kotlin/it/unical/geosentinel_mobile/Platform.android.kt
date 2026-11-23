// Implementazione Android di getPlatform(), dichiarata in commonMain/Platform.kt.
package it.unical.geosentinel_mobile

import android.os.Build

/** Piattaforma Android: il nome contiene il livello di API del dispositivo. */
class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

/** Restituisce la descrizione della piattaforma Android. */
actual fun getPlatform(): Platform = AndroidPlatform()