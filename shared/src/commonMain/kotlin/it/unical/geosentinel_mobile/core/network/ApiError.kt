package it.unical.geosentinel_mobile.core.network

// Il ProblemDetail non si riscrive a mano: si usa quello generato (it.unical.geosentinel_mobile.api.model.ProblemDetail)
sealed interface ApiError {
    val traceId: String?

    // validation-error: campo -> messaggio del server (errors[].field / errors[].message)
    data class Validation(
        val fieldErrors: Map<String, String>,
        override val traceId: String?
    ) : ApiError

    // slug: mfa-required e invalid-mfa-code distinguono la verifica in due passaggi (M10) da credenziali o token sbagliati
    data class Unauthorized(override val traceId: String?, val slug: String? = null) : ApiError
    data class Forbidden(override val traceId: String?) : ApiError
    data class NotFound(override val traceId: String?) : ApiError

    // conflict, capacity-exceeded, alert-already-taken, concurrent-modification, resource-busy, account-managed
    data class Conflict(
        val slug: String,
        override val traceId: String?
    ) : ApiError

    // business-rule, idempotency-key-reused, age-requirement-not-met, media-*, precondition-required
    data class BusinessRule(
        val slug: String,
        override val traceId: String?
    ) : ApiError

    data class RateLimited(
        val retryAfterSeconds: Long?,
        override val traceId: String?
    ) : ApiError

    // internal-error, storage-unavailable e ogni 5xx
    data class Server(
        val status: Int,
        override val traceId: String?
    ) : ApiError

    data object Network : ApiError {
        override val traceId: String? = null
    }
}

// Nel messaggio solo il tipo e il traceId: i testi del server (fieldErrors) non devono finire in log o crash report
class ApiException(val error: ApiError) :
    Exception("API error: ${error::class.simpleName}, traceId=${error.traceId}")

/**
 * Una risposta 2xx che non rispetta il contratto: è un bug, non un errore dell'utente o della rete.
 * Il messaggio di kotlinx.serialization contiene un pezzo del JSON ricevuto (email, posizione, note mediche):
 * qui restano solo i tipi, e la causa originale non si allega.
 */
class ContractViolationException(cause: Throwable) :
    Exception("Risposta del server fuori contratto (${cause::class.simpleName})")

// Per l'outbox (prenotazioni M5.2, SOS M6.1): senza risposta definitiva l'azione resta in coda. Rete, 429, 409
// resource-busy, 5xx (il client ha già ritentato quelli che il contratto permette) e 401 (sessione da rinnovare: dopo
// il login dello stesso utente la chiave vale ancora). Ogni altro 4xx è definitivo e ripeterlo uguale non serve.
fun ApiError.isTransient(): Boolean = when (this) {
    ApiError.Network, is ApiError.RateLimited, is ApiError.Server, is ApiError.Unauthorized -> true
    is ApiError.Conflict -> slug == "resource-busy"
    else -> false
}
