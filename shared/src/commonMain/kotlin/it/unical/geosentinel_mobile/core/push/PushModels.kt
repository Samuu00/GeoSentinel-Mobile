// Modelli delle notifiche push.
// Il backend invia notifiche minime, con il solo tipo di evento e l'id dell'allerta SOS: i dettagli l'app li
// rilegge dalle API, così nella notifica non viaggiano dati personali.
package it.unical.geosentinel_mobile.core.push

/**
 * Riceve i token FCM dal codice di piattaforma (FirebaseMessagingService su Android, AppDelegate su iOS).
 * La implementa `PushManager`.
 */
interface PushTokenSink {
    /** Chiamata quando Firebase assegna o rinnova il token del dispositivo. */
    fun onNewToken(token: String)
}

/**
 * Un evento ricevuto via push, riferito sempre a un'allerta SOS.
 * Il payload contiene solo `type` e `alertId`; tutto il resto si legge dall'API.
 */
sealed interface PushEvent {
    /** Identificativo (UUID) dell'allerta a cui si riferisce l'evento. */
    val alertId: String

    /** È stato lanciato un nuovo SOS: interessa gli operatori del soccorso. */
    data class SosRaised(override val alertId: String) : PushEvent
    /** È stata inviata una squadra: interessa chi ha lanciato l'SOS. */
    data class SosDispatched(override val alertId: String) : PushEvent
    /** L'allerta è stata chiusa. */
    data class SosResolved(override val alertId: String) : PushEvent

    companion object {
        // L'id delle allerte è un UUID. Il payload può arrivare anche da un intent inviato da un'altra app
        // (MainActivity è esportata): tutto ciò che non è un UUID viene scartato, così non può finire nel
        // percorso di una richiesta autenticata.
        private val AlertIdPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /**
         * Costruisce l'evento dal payload della notifica. Restituisce null se l'id non è un UUID valido,
         * se manca un campo o se il tipo è sconosciuto (per esempio introdotto da una versione più recente
         * del backend): in tutti questi casi la notifica viene ignorata.
         */
        fun fromPayload(data: Map<String, String?>): PushEvent? {
            val alertId = data["alertId"]?.takeIf { AlertIdPattern.matches(it) } ?: return null
            return when (data["type"]) {
                "SOS_RAISED" -> SosRaised(alertId)
                "SOS_DISPATCHED" -> SosDispatched(alertId)
                "SOS_RESOLVED" -> SosResolved(alertId)
                else -> null
            }
        }
    }
}
