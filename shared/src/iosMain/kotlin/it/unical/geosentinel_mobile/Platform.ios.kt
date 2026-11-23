// Implementazione iOS di getPlatform(), dichiarata in commonMain/Platform.kt.
package it.unical.geosentinel_mobile

import platform.UIKit.UIDevice

/** Piattaforma iOS: il nome è letto da UIDevice (nome del sistema e versione). */
class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

/** Restituisce la descrizione della piattaforma iOS. */
actual fun getPlatform(): Platform = IOSPlatform()