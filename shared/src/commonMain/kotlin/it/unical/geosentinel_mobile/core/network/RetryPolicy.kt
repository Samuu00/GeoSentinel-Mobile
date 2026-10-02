package it.unical.geosentinel_mobile.core.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException

object GeoSentinelRetryPolicy {
    private const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"

    // Oltre questa attesa non si ritenta da soli: l'errore arriva alla UI con il tempo d'attesa (es. login bloccato)
    const val MAX_RETRY_AFTER_SECONDS = 10L

    private val retryableStatuses = setOf(
        HttpStatusCode.TooManyRequests,     // rate-limit-exceeded
        HttpStatusCode.Conflict,            // solo resource-busy ha Retry-After, gli altri 409 no
        HttpStatusCode.ServiceUnavailable,  // storage-unavailable
    )

    // Lo slug sta nel body (ProblemDetail.type), che qui non si può leggere: tra i 409 il backend manda
    // Retry-After solo con resource-busy, quindi è l'header a distinguerli
    fun shouldRetry(response: HttpResponse): Boolean {
        if (response.status !in retryableStatuses) return false
        val seconds = retryAfterSeconds(response) ?: return false
        return seconds <= MAX_RETRY_AFTER_SECONDS
    }

    // Errori di rete: si ritenta solo se la richiesta è idempotente per metodo o ha una chiave esplicita
    fun shouldRetryOnException(request: HttpRequestBuilder, cause: Throwable): Boolean {
        if (cause is CancellationException) return false
        val method = request.method
        val isIdempotentMethod = method == HttpMethod.Get || method == HttpMethod.Put || method == HttpMethod.Delete
        return isIdempotentMethod || request.headers.contains(HEADER_IDEMPOTENCY_KEY)
    }

    fun computeDelayMillis(response: HttpResponse?): Long? =
        response?.let { retryAfterSeconds(it) }?.times(1000L)

    private fun retryAfterSeconds(response: HttpResponse): Long? =
        response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
}
