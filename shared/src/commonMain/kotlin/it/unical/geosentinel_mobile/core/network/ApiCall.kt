// Esecuzione delle chiamate alle API e conversione degli errori HTTP in ApiError.
// Tutti i repository passano da qui, così il modo in cui si interpretano risposte, errori di rete e risposte
// fuori contratto è uguale in tutta l'app.
package it.unical.geosentinel_mobile.core.network

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import it.unical.geosentinel_mobile.api.infrastructure.HttpResponse
import it.unical.geosentinel_mobile.api.model.ProblemDetail
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.client.statement.HttpResponse as KtorResponse

/** Parser per i ProblemDetail: ignora i campi che il client non conosce. */
private val problemJson = Json { ignoreUnknownKeys = true }

/**
 * Esegue una chiamata del client generato e ne restituisce il risultato come `Result`.
 *
 * Il client generato non lancia eccezioni sui 4xx e 5xx: restituisce una risposta con `success = false`, quindi
 * l'errore si legge dalla risposta e diventa un [ApiException] con l'[ApiError] corrispondente. Solo gli errori di
 * rete (`IOException`, compresi i timeout, anche durante la lettura del corpo) diventano [ApiError.Network]. La
 * cancellazione della coroutine si propaga sempre. Un corpo 2xx che non si deserializza è un bug del contratto e
 * lancia [ContractViolationException], senza il messaggio originale che potrebbe contenere dati personali.
 */
suspend fun <T : Any> apiCall(block: suspend () -> HttpResponse<T>): Result<T> = try {
    val response = block()
    if (response.success) {
        Result.success(response.body())
    } else {
        Result.failure(ApiException(response.response.toApiError()))
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

/**
 * Esegue [block] trasformando in un fallimento qualsiasi eccezione, compresa [ContractViolationException]: una
 * risposta fuori contratto non deve far chiudere l'app, ma diventare un errore come gli altri (la sezione non
 * viene mostrata o compare un messaggio). La cancellazione della coroutine si propaga.
 */
suspend fun <T> safely(block: suspend () -> Result<T>): Result<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Legge il corpo di una risposta di errore come ProblemDetail e lo converte in [ApiError].
 * Se il corpo è vuoto o non è JSON si decide solo in base allo stato HTTP.
 */
suspend fun KtorResponse.toApiError(): ApiError {
    // Il backend risponde con application/problem+json, ma ContentNegotiation è registrato solo per
    // application/json: per questo si legge il testo e lo si decodifica a mano.
    val problem = try {
        problemJson.decodeFromString<ProblemDetail>(bodyAsText())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null // Corpo vuoto o non JSON (per esempio la pagina di errore di un proxy).
    }
    return apiErrorOf(status.value, problem, headers[HttpHeaders.RetryAfter])
}

/**
 * Costruisce l'[ApiError] da stato HTTP, ProblemDetail e header `Retry-After`.
 *
 * Nella guida all'API ogni slug ha uno stato fisso: lo stato sceglie il tipo di errore e lo slug (l'ultima parte
 * dell'URI in `type`) lo precisa. Se manca, lo slug vale "unknown". I campi `title` e `detail` non si usano mai,
 * perché sono testo libero. Gli stati 4xx non elencati diventano [ApiError.BusinessRule].
 */
internal fun apiErrorOf(status: Int, problem: ProblemDetail?, retryAfter: String?): ApiError {
    val traceId = problem?.traceId
    val slug = problem?.type?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "unknown"
    return when {
        status == 400 -> ApiError.Validation(problem?.errors.orEmpty().associate { it.field to it.message }, traceId)
        status == 401 -> ApiError.Unauthorized(traceId, slug.takeIf { problem != null })
        status == 403 -> ApiError.Forbidden(traceId)
        status == 404 -> ApiError.NotFound(traceId)
        status == 409 -> ApiError.Conflict(slug, traceId)
        status == 429 -> ApiError.RateLimited(retryAfter?.trim()?.toLongOrNull(), traceId)
        status >= 500 -> ApiError.Server(status, traceId)
        else -> ApiError.BusinessRule(slug, traceId)
    }
}
