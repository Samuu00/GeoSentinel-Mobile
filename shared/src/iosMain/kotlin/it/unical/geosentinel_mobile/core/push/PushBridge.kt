package it.unical.geosentinel_mobile.core.push

import it.unical.geosentinel_mobile.core.di.pushManager

/**
 * Ponte per l'AppDelegate in Swift (M7.1): `PushBridge.shared.onNewToken(token:)` ecc. Riceve lo `userInfo` della
 * notifica così com'è ([AnyHashable: Any] in Swift) e ne tiene solo `type` e `alertId`, il payload del backend.
 */
object PushBridge {
    fun onNewToken(token: String) = pushManager().onNewToken(token)

    fun onMessage(userInfo: Map<Any?, *>) = pushManager().onMessage(userInfo.payload())

    fun onNotificationOpened(userInfo: Map<Any?, *>) = pushManager().onNotificationOpened(userInfo.payload())
}

private fun Map<Any?, *>.payload(): Map<String, String?> =
    mapOf("type" to this["type"] as? String, "alertId" to this["alertId"] as? String)
