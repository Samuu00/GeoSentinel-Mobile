// Ponte tra l'AppDelegate scritto in Swift e il gestore delle notifiche push in Kotlin.
// Firebase e APNs su iOS si configurano in Swift; gli eventi che arrivano lì (nuovo token, messaggio,
// notifica toccata) vengono inoltrati al PushManager condiviso attraverso questo oggetto.
package it.unical.geosentinel_mobile.core.push

import it.unical.geosentinel_mobile.core.di.pushManager

/**
 * Oggetto chiamato da Swift come `PushBridge.shared`. Riceve lo `userInfo` della notifica così com'è
 * (in Swift un dizionario `[AnyHashable: Any]`) e ne conserva solo `type` e `alertId`, cioè il payload
 * che invia il backend.
 */
object PushBridge {
    /** Inoltra un nuovo token FCM, che va registrato sul backend. */
    fun onNewToken(token: String) = pushManager().onNewToken(token)

    /** Inoltra una notifica ricevuta mentre l'app è aperta. */
    fun onMessage(userInfo: Map<Any?, *>) = pushManager().onMessage(userInfo.payload())

    /** Inoltra il tocco su una notifica, che deve aprire la schermata corrispondente. */
    fun onNotificationOpened(userInfo: Map<Any?, *>) = pushManager().onNotificationOpened(userInfo.payload())

    /**
     * Registra la funzione Swift che invalida il token FCM e ne chiede uno nuovo. Il PushManager la chiama
     * a ogni fine sessione (vedi `PushManager.sessionEnded`), così il vecchio token non riceve più notifiche.
     */
    fun setTokenRevoker(revoker: () -> Unit) {
        pushManager().tokenRevoker = revoker
    }
}

/** Estrae dallo `userInfo` i soli campi `type` e `alertId`, come stringhe (null se mancano o non sono testo). */
private fun Map<Any?, *>.payload(): Map<String, String?> =
    mapOf("type" to this["type"] as? String, "alertId" to this["alertId"] as? String)
