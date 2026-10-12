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

private val problemJson = Json { ignoreUnknownKeys = true }

/**
 * Esegue una chiamata del client generato. Il client generato non lancia eccezioni sui 4xx/5xx (restituisce
 * un HttpResponse con `success = false`), quindi l'errore si legge dalla risposta.
 * Solo gli errori di rete (IOException, compresi i timeout, anche durante la lettura del body) diventano
 * [ApiError.Network]; la cancellazione della coroutine si propaga, e un body 2xx che non si deserializza è un bug
 * del contratto: diventa [ContractViolationException], senza il messaggio originale.
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
 * Per i chiamanti di [apiCall]: una risposta fuori contratto ([ContractViolationException]) non deve far cadere l'app,
 * diventa un errore come gli altri (la sezione non si mostra, o compare il messaggio). La cancellazione si propaga.
 */
suspend fun <T> safely(block: suspend () -> Result<T>): Result<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

suspend fun KtorResponse.toApiError(): ApiError {
    // application/problem+json: ContentNegotiation registra solo application/json, quindi si decodifica il testo
    val problem = try {
        problemJson.decodeFromString<ProblemDetail>(bodyAsText())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null // body vuoto o non JSON (es. una pagina d'errore di un proxy): si decide sullo status
    }
    return apiErrorOf(status.value, problem, headers[HttpHeaders.RetryAfter])
}

// Ogni slug della guida ha uno status fisso, quindi lo status sceglie il ramo e lo slug lo precisa.
// title e detail non si usano mai.
internal fun apiErrorOf(status: Int, problem: ProblemDetail?, retryAfter: String?): ApiError {
    val traceId = problem?.traceId
    val slug = problem?.type?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "unknown"
    return when {
        status == 400 -> ApiError.Validation(problem?.errors.orEmpty().associate { it.field to it.message }, traceId)
        status == 401 -> ApiError.Unauthorized(traceId)
        status == 403 -> ApiError.Forbidden(traceId)
        status == 404 -> ApiError.NotFound(traceId)
        status == 409 -> ApiError.Conflict(slug, traceId)
        status == 429 -> ApiError.RateLimited(retryAfter?.trim()?.toLongOrNull(), traceId)
        status >= 500 -> ApiError.Server(status, traceId)
        else -> ApiError.BusinessRule(slug, traceId)
    }
}
