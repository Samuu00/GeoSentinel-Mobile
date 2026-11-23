// Modello degli errori restituiti dalle API del backend.
// Il backend risponde agli errori con un ProblemDetail (RFC 9457) il cui campo `type` contiene uno slug stabile
// (per esempio `capacity-exceeded`). L'app costruisce questi errori solo dallo slug e dallo stato HTTP, mai dai
// testi `title` o `detail`, che possono cambiare. Il ProblemDetail non viene riscritto a mano: si usa la classe
// generata dal contratto OpenAPI.
package it.unical.geosentinel_mobile.core.network

/**
 * Errore di una chiamata alle API, già classificato per il resto dell'app.
 * Ogni caso porta il `traceId` del server, utile per ritrovare la richiesta nei log del backend.
 */
sealed interface ApiError {
    /** Identificativo della richiesta nei log del server; null se l'errore non viene dal server. */
    val traceId: String?

    /**
     * `validation-error`: dati non validi.
     * @property fieldErrors per ogni campo, il messaggio del server (da `errors[].field` ed `errors[].message`).
     */
    data class Validation(
        val fieldErrors: Map<String, String>,
        override val traceId: String?
    ) : ApiError

    /**
     * 401: credenziali o token non validi.
     * @property slug distingue i casi della verifica in due passaggi (`mfa-required`, `invalid-mfa-code`)
     *   da credenziali o token sbagliati.
     */
    data class Unauthorized(override val traceId: String?, val slug: String? = null) : ApiError
    /** 403: l'utente non ha i permessi per questa azione. */
    data class Forbidden(override val traceId: String?) : ApiError
    /** 404: la risorsa non esiste o non è visibile all'utente. */
    data class NotFound(override val traceId: String?) : ApiError

    /**
     * 409: conflitto con lo stato della risorsa. Lo [slug] è uno tra `conflict`, `capacity-exceeded`,
     * `alert-already-taken`, `concurrent-modification`, `resource-busy` e `account-managed`.
     */
    data class Conflict(
        val slug: String,
        override val traceId: String?
    ) : ApiError

    /**
     * Violazione di una regola di dominio o di un requisito della richiesta. Lo [slug] è per esempio `business-rule`,
     * `idempotency-key-reused`, `age-requirement-not-met`, uno dei `media-*` o `precondition-required`.
     */
    data class BusinessRule(
        val slug: String,
        override val traceId: String?
    ) : ApiError

    /**
     * 429: troppe richieste.
     * @property retryAfterSeconds i secondi da attendere indicati dal server in `Retry-After`, se presenti.
     */
    data class RateLimited(
        val retryAfterSeconds: Long?,
        override val traceId: String?
    ) : ApiError

    /** Errore del server: `internal-error`, `storage-unavailable` e ogni altro 5xx. */
    data class Server(
        val status: Int,
        override val traceId: String?
    ) : ApiError

    /** Nessuna risposta: rete assente, timeout o connessione interrotta. */
    data object Network : ApiError {
        override val traceId: String? = null
    }
}

/**
 * Eccezione che trasporta un [ApiError] dentro un `Result`. Il messaggio contiene solo il tipo dell'errore e il
 * traceId: i testi del server (come i `fieldErrors`) possono contenere dati personali e non devono finire in un
 * log o in un crash report.
 */
class ApiException(val error: ApiError) :
    Exception("API error: ${error::class.simpleName}, traceId=${error.traceId}")

/**
 * Una risposta 2xx che non rispetta il contratto: è un bug, non un errore dell'utente o della rete.
 * Il messaggio di kotlinx.serialization riporta un pezzo del JSON ricevuto, che può contenere dati personali
 * (email, posizione, note mediche): per questo qui resta solo il nome del tipo dell'eccezione e la causa
 * originale non viene allegata.
 */
class ContractViolationException(cause: Throwable) :
    Exception("Risposta del server fuori contratto (${cause::class.simpleName})")

/**
 * Dice se l'errore è temporaneo, cioè se un'azione nell'outbox (prenotazione, SOS) deve restare in coda per
 * essere ripetuta con la stessa `Idempotency-Key`.
 *
 * Sono temporanei: errori di rete, 429, 409 `resource-busy`, 5xx (il client ha già fatto i tentativi permessi dal
 * contratto) e 401, perché la sessione va solo rinnovata e dopo un nuovo login dello stesso utente la chiave
 * vale ancora. Ogni altro 4xx è definitivo: ripetere la stessa richiesta darebbe lo stesso risultato.
 */
fun ApiError.isTransient(): Boolean = when (this) {
    ApiError.Network, is ApiError.RateLimited, is ApiError.Server, is ApiError.Unauthorized -> true
    is ApiError.Conflict -> slug == "resource-busy"
    else -> false
}
