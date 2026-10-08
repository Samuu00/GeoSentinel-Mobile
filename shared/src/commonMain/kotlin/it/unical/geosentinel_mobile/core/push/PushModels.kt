package it.unical.geosentinel_mobile.core.push

// Chiamata dal codice di piattaforma (FirebaseMessagingService su Android, AppDelegate su iOS) con il token FCM
interface PushTokenSink {
    fun onNewToken(token: String)
}

/** Il payload ha solo `type` e `alertId` (ADR-0016 del backend): i dettagli si rileggono dall'API. */
sealed interface PushEvent {
    val alertId: String

    data class SosRaised(override val alertId: String) : PushEvent
    data class SosDispatched(override val alertId: String) : PushEvent
    data class SosResolved(override val alertId: String) : PushEvent

    companion object {
        // L'id delle allerte è un UUID. Il payload può arrivare anche da un intent di un'altra app (MainActivity è
        // esportata): qualunque altra cosa si scarta, così non finisce nel path di una richiesta autenticata
        private val AlertIdPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        // Un type sconosciuto (versione nuova del backend) o un payload incompleto si ignorano
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
