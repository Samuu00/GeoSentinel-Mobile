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
        // Un type sconosciuto (versione nuova del backend) o un payload incompleto si ignorano
        fun fromPayload(data: Map<String, String?>): PushEvent? {
            val alertId = data["alertId"]?.takeIf { it.isNotBlank() } ?: return null
            return when (data["type"]) {
                "SOS_RAISED" -> SosRaised(alertId)
                "SOS_DISPATCHED" -> SosDispatched(alertId)
                "SOS_RESOLVED" -> SosResolved(alertId)
                else -> null
            }
        }
    }
}
