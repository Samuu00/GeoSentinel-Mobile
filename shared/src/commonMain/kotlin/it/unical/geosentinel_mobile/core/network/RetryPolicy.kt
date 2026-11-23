// Regole con cui il client HTTP ripete automaticamente una richiesta fallita.
// Vengono usate dal plugin HttpRequestRetry configurato in `HttpClientFactory`. Si ripetono solo i casi
// in cui ripetere è sicuro e utile; tutti gli altri errori arrivano subito al chiamante.
package it.unical.geosentinel_mobile.core.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException

/**
 * Politica dei tentativi per il client dell'app.
 *
 * Si ritenta solo su `429`, su `409 resource-busy` e su `503 storage-unavailable`, rispettando l'attesa indicata
 * dal server in `Retry-After`. Dopo un errore di rete si ritenta solo se la richiesta è idempotente.
 */
object GeoSentinelRetryPolicy {
    /** Header con cui il client rende idempotente una richiesta POST (per esempio l'invio di un SOS). */
    private const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"

    // Attesa massima, in secondi, che il client accetta di fare da solo. Oltre questa soglia non si ritenta
    // e l'errore arriva alla UI con il tempo d'attesa (per esempio il login bloccato dopo troppi tentativi).
    const val MAX_RETRY_AFTER_SECONDS = 10L

    /** Codici di stato per cui un nuovo tentativo può avere senso. */
    private val retryableStatuses = setOf(
        HttpStatusCode.TooManyRequests,     // rate-limit-exceeded
        HttpStatusCode.Conflict,            // tra i 409 solo resource-busy porta Retry-After
        HttpStatusCode.ServiceUnavailable,  // storage-unavailable
    )

    /**
     * Decide se ripetere una richiesta che ha ricevuto una risposta.
     *
     * Lo slug dell'errore sta nel corpo (`ProblemDetail.type`), che a questo livello non si può leggere. Il backend
     * però manda `Retry-After` soltanto con `resource-busy` tra i 409, quindi è la presenza dell'header a
     * distinguere i casi: senza `Retry-After`, o con un'attesa oltre [MAX_RETRY_AFTER_SECONDS], non si ritenta.
     */
    fun shouldRetry(response: HttpResponse): Boolean {
        if (response.status !in retryableStatuses) return false
        val seconds = retryAfterSeconds(response) ?: return false
        return seconds <= MAX_RETRY_AFTER_SECONDS
    }

    /**
     * Decide se ripetere una richiesta fallita per un errore di rete (nessuna risposta ricevuta).
     *
     * Il server potrebbe aver già eseguito la richiesta, quindi si ritenta solo se ripeterla non produce effetti
     * doppi: metodi idempotenti per definizione (GET, PUT, DELETE) oppure richieste con una `Idempotency-Key`,
     * che il server usa per riconoscere i duplicati. Una cancellazione della coroutine non si ritenta mai.
     */
    fun shouldRetryOnException(request: HttpRequestBuilder, cause: Throwable): Boolean {
        if (cause is CancellationException) return false
        val method = request.method
        val isIdempotentMethod = method == HttpMethod.Get || method == HttpMethod.Put || method == HttpMethod.Delete
        return isIdempotentMethod || request.headers.contains(HEADER_IDEMPOTENCY_KEY)
    }

    /** Attesa prima del prossimo tentativo, in millisecondi, presa da `Retry-After`; null se non indicata. */
    fun computeDelayMillis(response: HttpResponse?): Long? =
        response?.let { retryAfterSeconds(it) }?.times(1000L)

    /** Legge `Retry-After` come numero di secondi non negativo; null se manca o non è un numero. */
    private fun retryAfterSeconds(response: HttpResponse): Long? =
        response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
}
