package it.unical.geosentinel_mobile.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/** Esito di una GET condizionale: corpo nuovo (con il suo ETag) o "non è cambiato niente" (304). */
sealed interface Conditional<out T> {
    data class Modified<T>(val body: T, val etag: String?) : Conditional<T>
    data object NotModified : Conditional<Nothing>
}

/**
 * GET con `If-None-Match` (M3.3): il client generato non ha l'header, perché lo schema non lo dice (lo dice la guida
 * all'API: ETag debole e `Cache-Control: no-cache` su `/trails` e `/pois`). Stesso client condiviso (bearer, retry,
 * log) e stessi errori di [apiCall]: 304 è un successo, IOException è [ApiError.Network], un 2xx fuori contratto
 * lancia [ContractViolationException].
 */
suspend inline fun <reified T : Any> HttpClient.conditionalGet(url: String, etag: String?): Result<Conditional<T>> = try {
    val response = get(url) { if (etag != null) header(HttpHeaders.IfNoneMatch, etag) }
    when {
        response.status == HttpStatusCode.NotModified -> Result.success(Conditional.NotModified)
        response.status.isSuccess() -> Result.success(Conditional.Modified(response.body<T>(), response.headers[HttpHeaders.ETag]))
        else -> Result.failure(ApiException(response.toApiError()))
    }
} catch (e: CancellationException) {
    throw e
} catch (_: IOException) {
    Result.failure(ApiException(ApiError.Network))
} catch (e: SerializationException) {
    throw ContractViolationException(e)
} catch (e: ContentConvertException) {
    throw ContractViolationException(e)
}
