// GET condizionali con ETag per la cache del catalogo (sentieri e POI).
// Se i dati sul server non sono cambiati, il backend risponde 304 senza corpo e l'app usa la copia salvata
// in Room: si risparmiano banda e batteria, che in montagna sono preziose.
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

/** Esito di una GET condizionale: un corpo nuovo con il suo ETag, oppure "non è cambiato nulla" (304). */
sealed interface Conditional<out T> {
    /** Il server ha inviato dati nuovi; [etag] va salvato per la richiesta successiva. */
    data class Modified<T>(val body: T, val etag: String?) : Conditional<T>
    /** Il server ha risposto 304: la copia in cache è ancora valida. */
    data object NotModified : Conditional<Nothing>
}

/**
 * Esegue una GET su [url] con l'header `If-None-Match` quando si conosce già un [etag].
 *
 * Il client generato non permette di aggiungere quell'header, perché lo schema OpenAPI non lo prevede (lo descrive
 * la guida all'API: ETag debole e `Cache-Control: no-cache` su `/trails` e `/pois`). Per questo la richiesta si
 * fa a mano, ma con lo stesso client condiviso (bearer, retry, log) e la stessa gestione degli errori di [apiCall]:
 * il 304 è un successo, una `IOException` diventa [ApiError.Network] e un 2xx con un corpo fuori contratto lancia
 * [ContractViolationException].
 */
suspend inline fun <reified T : Any> HttpClient.conditionalGet(url: String, etag: String?): Result<Conditional<T>> = try {
    val response = get(url) { if (etag != null) header(HttpHeaders.IfNoneMatch, etag) }
    when {
        response.status == HttpStatusCode.NotModified -> Result.success(Conditional.NotModified)
        response.status.isSuccess() -> Result.success(Conditional.Modified(response.body<T>(), response.headers[HttpHeaders.ETag]))
        else -> Result.failure(ApiException(response.toApiError()))
    }
} catch (e: CancellationException) {
    // La cancellazione della coroutine va sempre propagata, mai trasformata in un errore.
    throw e
} catch (_: IOException) {
    Result.failure(ApiException(ApiError.Network))
} catch (e: SerializationException) {
    throw ContractViolationException(e)
} catch (e: ContentConvertException) {
    throw ContractViolationException(e)
}
