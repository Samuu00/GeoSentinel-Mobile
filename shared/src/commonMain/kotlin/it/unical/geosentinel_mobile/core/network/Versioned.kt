package it.unical.geosentinel_mobile.core.network

import io.ktor.http.HttpHeaders
import it.unical.geosentinel_mobile.api.infrastructure.HttpResponse

// Una risposta con la sua versione (l'ETag): serve per le modifiche con `If-Match` (ticket, M8.2)
data class Versioned<T>(val value: T, val etag: String?)

// Come apiCall, ma tiene anche l'ETag della risposta
suspend fun <T : Any> apiCallVersioned(block: suspend () -> HttpResponse<T>): Result<Versioned<T>> {
    var etag: String? = null
    return apiCall {
        block().also { etag = it.response.headers[HttpHeaders.ETag] }
    }.map { Versioned(it, etag) }
}
