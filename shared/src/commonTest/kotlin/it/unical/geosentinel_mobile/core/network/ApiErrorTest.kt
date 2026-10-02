package it.unical.geosentinel_mobile.core.network

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_conflict
import geosentinel_mobile.shared.generated.resources.error_generic
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.UserProfileApi
import it.unical.geosentinel_mobile.api.model.FieldError
import it.unical.geosentinel_mobile.api.model.ProblemDetail
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.session.AuthTokens
import it.unical.geosentinel_mobile.core.session.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BASE_URL = "http://api.test"

class ApiErrorTest {

    private fun problem(status: Int, slug: String, errors: List<FieldError>? = null) = ProblemDetail(
        status = status,
        title = "Titolo in inglese che non si usa",
        type = "https://geosentinel.dev/problems/$slug",
        errors = errors,
        traceId = "trace-$slug",
    )

    // Tabella della guida all'API (../GeoSentinel-Backend/docs/api-guide.md, "Errori"): slug, status, ramo atteso
    private val guide: List<Triple<String, Int, ApiError>> = listOf(
        Triple("validation-error", 400, ApiError.Validation(emptyMap(), "trace-validation-error")),
        Triple("unauthorized", 401, ApiError.Unauthorized("trace-unauthorized")),
        Triple("forbidden", 403, ApiError.Forbidden("trace-forbidden")),
        Triple("not-found", 404, ApiError.NotFound("trace-not-found")),
        Triple("conflict", 409, ApiError.Conflict("conflict", "trace-conflict")),
        Triple("capacity-exceeded", 409, ApiError.Conflict("capacity-exceeded", "trace-capacity-exceeded")),
        Triple("alert-already-taken", 409, ApiError.Conflict("alert-already-taken", "trace-alert-already-taken")),
        Triple("concurrent-modification", 409, ApiError.Conflict("concurrent-modification", "trace-concurrent-modification")),
        Triple("resource-busy", 409, ApiError.Conflict("resource-busy", "trace-resource-busy")),
        Triple("account-managed", 409, ApiError.Conflict("account-managed", "trace-account-managed")),
        Triple("business-rule", 422, ApiError.BusinessRule("business-rule", "trace-business-rule")),
        Triple("idempotency-key-reused", 422, ApiError.BusinessRule("idempotency-key-reused", "trace-idempotency-key-reused")),
        Triple("age-requirement-not-met", 422, ApiError.BusinessRule("age-requirement-not-met", "trace-age-requirement-not-met")),
        Triple("media-quota-exceeded", 422, ApiError.BusinessRule("media-quota-exceeded", "trace-media-quota-exceeded")),
        Triple("media-unsupported-type", 415, ApiError.BusinessRule("media-unsupported-type", "trace-media-unsupported-type")),
        Triple("media-payload-too-large", 413, ApiError.BusinessRule("media-payload-too-large", "trace-media-payload-too-large")),
        Triple("precondition-required", 428, ApiError.BusinessRule("precondition-required", "trace-precondition-required")),
        Triple("rate-limit-exceeded", 429, ApiError.RateLimited(30, "trace-rate-limit-exceeded")),
        Triple("storage-unavailable", 503, ApiError.Server(503, "trace-storage-unavailable")),
        Triple("internal-error", 500, ApiError.Server(500, "trace-internal-error")),
    )

    // ---- mappatura ProblemDetail -> ApiError -----------------------------------------------------------------

    @Test
    fun `ogni slug della guida finisce nel ramo giusto di ApiError`() {
        for ((slug, status, expected) in guide) {
            assertEquals(expected, apiErrorOf(status, problem(status, slug), retryAfter = "30"), slug)
        }
    }

    @Test
    fun `ogni slug della guida ha un messaggio proprio, non quello generico`() {
        val generic = setOf(Res.string.error_generic, Res.string.error_conflict)
        for ((slug, _, error) in guide) {
            if (slug == "conflict") continue // il 409 generico usa proprio il messaggio generico dei conflitti
            assertTrue(error.messageResource() !in generic, "manca il messaggio per $slug")
        }
    }

    @Test
    fun `uno slug sconosciuto ha il messaggio generico del suo ramo`() {
        val conflict = apiErrorOf(409, problem(409, "nuovo-slug"), null)
        val business = apiErrorOf(422, problem(422, "nuovo-slug"), null)

        assertEquals(ApiError.Conflict("nuovo-slug", "trace-nuovo-slug"), conflict)
        assertEquals(Res.string.error_conflict, conflict.messageResource())
        assertEquals(Res.string.error_generic, business.messageResource())
    }

    @Test
    fun `gli errori per campo diventano una mappa campo - messaggio`() {
        val errors = listOf(FieldError("email", "must be a well-formed email"), FieldError("password", "size must be 12-72"))

        val error = apiErrorOf(400, problem(400, "validation-error", errors), null)

        assertEquals(
            mapOf("email" to "must be a well-formed email", "password" to "size must be 12-72"),
            assertIs<ApiError.Validation>(error).fieldErrors,
        )
    }

    @Test
    fun `senza ProblemDetail decide lo status`() {
        assertEquals(ApiError.Server(502, null), apiErrorOf(502, null, null))
        assertEquals(ApiError.Unauthorized(null), apiErrorOf(401, null, null))
        assertEquals(ApiError.RateLimited(null, null), apiErrorOf(429, null, "non-un-numero"))
        assertEquals(ApiError.BusinessRule("unknown", null), apiErrorOf(418, null, null))
    }

    // ---- apiCall con il client generato ----------------------------------------------------------------------

    private val noSession = object : SessionRepository {
        override suspend fun getTokens(): AuthTokens? = null
        override suspend fun refreshTokens(refreshToken: String): AuthTokens? = null
    }

    private fun usersApi(engine: MockEngine) =
        UserProfileApi(BASE_URL, HttpClientFactory.createHttpClient(AppConfig(BASE_URL, isDebug = false), noSession, engine))

    private fun respondingWith(status: HttpStatusCode, body: String, contentType: String = "application/problem+json") =
        MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }

    @Test
    fun `una risposta 2xx diventa un successo`() = runTest {
        val api = usersApi(respondingWith(
            HttpStatusCode.OK,
            """{"id":"u1","email":"a@b.c","displayName":"A","role":"HIKER","createdAt":"2026-10-02T10:00:00Z"}""",
            contentType = "application/json",
        ))

        val result = apiCall { api.getMyProfile() }

        assertEquals("u1", result.getOrThrow().id)
    }

    @Test
    fun `un errore di validazione arriva con i campi`() = runTest {
        val api = usersApi(respondingWith(
            HttpStatusCode.BadRequest,
            """{"type":"https://geosentinel.dev/problems/validation-error","title":"Bad Request","status":400,
               "errors":[{"field":"displayName","message":"must not be blank"}],"traceId":"t1"}""",
        ))

        val error = assertIs<ApiException>(apiCall { api.getMyProfile() }.exceptionOrNull()).error

        assertEquals(ApiError.Validation(mapOf("displayName" to "must not be blank"), "t1"), error)
    }

    @Test
    fun `un 429 porta il Retry-After`() = runTest {
        // oltre i 10 s del retry automatico: arriva alla UI
        val api = usersApi(MockEngine {
            respond(
                """{"type":"https://geosentinel.dev/problems/rate-limit-exceeded","title":"Too Many Requests","status":429}""",
                HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType to listOf("application/problem+json"), HttpHeaders.RetryAfter to listOf("60")),
            )
        })

        val error = assertIs<ApiException>(apiCall { api.getMyProfile() }.exceptionOrNull()).error

        assertEquals(ApiError.RateLimited(60, null), error)
    }

    @Test
    fun `una pagina HTML di un proxy diventa un errore del server`() = runTest {
        val api = usersApi(respondingWith(HttpStatusCode.BadGateway, "<html>Bad Gateway</html>", contentType = "text/html"))

        val error = assertIs<ApiException>(apiCall { api.getMyProfile() }.exceptionOrNull()).error

        assertEquals(ApiError.Server(502, null), error)
    }

    @Test
    fun `un errore di rete diventa Network`() = runTest {
        val api = usersApi(MockEngine { throw IOException("rete assente") })

        val error = assertIs<ApiException>(apiCall { api.getMyProfile() }.exceptionOrNull()).error

        assertEquals(ApiError.Network, error)
    }

    @Test
    fun `la cancellazione non diventa un errore`() = runTest {
        assertFailsWith<CancellationException> {
            apiCall<Unit> { throw CancellationException("schermata chiusa") }
        }
    }

    @Test
    fun `un 2xx fuori contratto diventa ContractViolationException senza il JSON ricevuto`() = runTest {
        val api = usersApi(respondingWith(HttpStatusCode.OK, """{"id":"u1","email":"mario@esempio.it"}""", contentType = "application/json"))

        val failure = assertFailsWith<ContractViolationException> { apiCall { api.getMyProfile() } }

        assertNull(failure.cause)
        assertFalse("mario@esempio.it" in failure.message.orEmpty())
    }

    @Test
    fun `il messaggio di ApiException non contiene i testi del server`() {
        val exception = ApiException(ApiError.Validation(mapOf("email" to "mario@esempio.it is taken"), "t1"))

        assertEquals("API error: Validation, traceId=t1", exception.message)
    }
}
