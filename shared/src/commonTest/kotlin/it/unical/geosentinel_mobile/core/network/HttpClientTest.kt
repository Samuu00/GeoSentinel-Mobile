package it.unical.geosentinel_mobile.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.UserProfileApi
import it.unical.geosentinel_mobile.api.model.UserProfileResponse
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.session.AuthTokens
import it.unical.geosentinel_mobile.core.session.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BASE_URL = "http://api.test"

@OptIn(ExperimentalCoroutinesApi::class)
class HttpClientTest {

    private class FakeSession : SessionRepository {
        var tokens: AuthTokens? = AuthTokens(accessToken = "old", refreshToken = "r1", accessExpiresAt = Instant.fromEpochSeconds(0))
        var refreshResult: AuthTokens? = AuthTokens(accessToken = "new", refreshToken = "r2", accessExpiresAt = Instant.fromEpochSeconds(0))
        var refreshCalls = 0

        override suspend fun getTokens() = tokens

        override suspend fun refreshTokens(refreshToken: String): AuthTokens? {
            refreshCalls++
            delay(100) // così le richieste parallele arrivano mentre il refresh è in corso
            return refreshResult?.also { tokens = it }
        }
    }

    // Il motore gira sul dispatcher del test: i delay del retry usano il tempo virtuale
    private fun TestScope.client(
        session: SessionRepository = FakeSession(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        })
        return HttpClientFactory.createHttpClient(AppConfig(BASE_URL, isDebug = false), session, engine)
    }

    private fun MockRequestHandleScope.unauthorized() =
        respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))

    private fun MockRequestHandleScope.status(code: HttpStatusCode, vararg headers: Pair<String, String>) =
        respond("", code, headersOf(*headers.map { it.first to listOf(it.second) }.toTypedArray()))

    // ---- refresh ---------------------------------------------------------------------------------------------

    @Test
    fun `un 401 seguito da un refresh riuscito ripete la richiesta una volta`() = runTest {
        val session = FakeSession()
        val sentTokens = mutableListOf<String?>()
        val client = client(session) { request ->
            sentTokens += request.headers[HttpHeaders.Authorization]
            if (request.headers[HttpHeaders.Authorization] == "Bearer new") status(HttpStatusCode.OK) else unauthorized()
        }

        val response = client.get("/api/v1/trails")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf<String?>("Bearer old", "Bearer new"), sentTokens)
        assertEquals(1, session.refreshCalls)
    }

    @Test
    fun `due richieste parallele con 401 fanno un solo refresh`() = runTest {
        val session = FakeSession()
        val client = client(session) { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer new") status(HttpStatusCode.OK) else unauthorized()
        }

        val responses = listOf(
            async { client.get("/api/v1/trails/1") },
            async { client.get("/api/v1/trails/2") },
        ).awaitAll()

        assertTrue(responses.all { it.status == HttpStatusCode.OK })
        assertEquals(1, session.refreshCalls)
    }

    @Test
    fun `se il refresh non da token nuovi resta il 401 e il client non svuota la sessione`() = runTest {
        val session = FakeSession().apply { refreshResult = null }
        var calls = 0
        val client = client(session) { calls++; unauthorized() }

        val response = client.get("/api/v1/users/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, calls)
        assertEquals(1, session.refreshCalls)
        // decidere se la sessione è finita spetta al repository (solo su 401 del refresh, non su un errore di rete)
        assertNotNull(session.tokens)
    }

    @Test
    fun `il token va solo al backend e non ad altri host`() = runTest {
        var authorization: String? = "non letto"
        val client = client { request -> authorization = request.headers[HttpHeaders.Authorization]; status(HttpStatusCode.OK) }

        client.get("http://storage.test/bucket/foto.jpg")

        assertNull(authorization)
    }

    @Test
    fun `un 401 da un altro host non fa rimandare la richiesta con il token`() = runTest {
        val session = FakeSession()
        val sent = mutableListOf<String?>()
        val client = client(session) { request -> sent += request.headers[HttpHeaders.Authorization]; unauthorized() }

        val response = client.get("http://storage.test/bucket/foto.jpg")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(listOf<String?>(null), sent)
        assertEquals(0, session.refreshCalls)
    }

    @Test
    fun `il token non va allo stesso host su un'altra porta`() = runTest {
        var authorization: String? = "non letto"
        val client = client { request -> authorization = request.headers[HttpHeaders.Authorization]; status(HttpStatusCode.OK) }

        client.get("http://api.test:9000/bucket/foto.jpg")

        assertNull(authorization)
    }

    @Test
    fun `un 401 sul login non fa partire il refresh e non manda il bearer`() = runTest {
        val session = FakeSession()
        val sent = mutableListOf<String?>()
        val client = client(session) { request -> sent += request.headers[HttpHeaders.Authorization]; unauthorized() }

        val response = client.post("/api/v1/auth/login")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(listOf<String?>(null), sent, "il login parte una volta sola e senza bearer")
        assertEquals(0, session.refreshCalls)
    }

    @Test
    fun `un redirect non si segue`() = runTest {
        var calls = 0
        val client = client { calls++; status(HttpStatusCode.Found, HttpHeaders.Location to "http://altro.test/x") }

        val response = client.get("/api/v1/trails")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(1, calls)
    }

    // ---- retry -----------------------------------------------------------------------------------------------

    @Test
    fun `un 429 con Retry-After 2 si ripete dopo almeno 2 secondi`() = runTest {
        var calls = 0
        val client = client {
            calls++
            if (calls == 1) status(HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "2") else status(HttpStatusCode.OK)
        }

        val start = currentTime
        val response = client.get("/api/v1/trails")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, calls)
        assertTrue(currentTime - start >= 2_000, "atteso almeno 2000 ms, passati ${currentTime - start}")
    }

    @Test
    fun `un 409 resource-busy con Retry-After si ripete`() = runTest {
        var calls = 0
        val client = client {
            calls++
            if (calls == 1) status(HttpStatusCode.Conflict, HttpHeaders.RetryAfter to "2") else status(HttpStatusCode.Created)
        }

        val response = client.post("/api/v1/bookings") { header("Idempotency-Key", "k") }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(2, calls)
    }

    @Test
    fun `422 e 409 senza Retry-After non si ripetono`() = runTest {
        for (code in listOf(HttpStatusCode.UnprocessableEntity, HttpStatusCode.Conflict)) {
            var calls = 0
            val client = client { calls++; status(code) }

            val response = client.post("/api/v1/bookings")

            assertEquals(code, response.status)
            assertEquals(1, calls, "$code non va ripetuto")
        }
    }

    @Test
    fun `un Retry-After oltre il limite non blocca la richiesta`() = runTest {
        var calls = 0
        val client = client { calls++; status(HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "600") }

        val response = client.post("/api/v1/auth/login")

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(1, calls)
    }

    @Test
    fun `un errore di rete si ritenta su una GET e su una POST con Idempotency-Key - non su una POST senza`() = runTest {
        var calls = 0
        val client = client {
            calls++
            if (calls % 2 == 1) throw IOException("rete assente") else status(HttpStatusCode.OK)
        }

        assertEquals(HttpStatusCode.OK, client.get("/api/v1/trails").status)
        assertEquals(2, calls)

        assertEquals(HttpStatusCode.OK, client.post("/api/v1/sos") { header("Idempotency-Key", "k") }.status)
        assertEquals(4, calls)

        assertFailsWith<IOException> { client.post("/api/v1/hazards") }
        assertEquals(5, calls)
    }

    // ---- enum ------------------------------------------------------------------------------------------------

    @Test
    fun `un valore di enum sconosciuto non fa fallire la deserializzazione`() = runTest {
        val client = client {
            respond(
                """{"id":"u1","email":"a@b.c","displayName":"A","role":"SUPER_ADMIN","createdAt":"2026-10-02T10:00:00Z","nuovoCampo":1}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val profile = UserProfileApi(BASE_URL, client).getMyProfile().body()

        assertEquals(UserProfileResponse.Role.unknown_default_open_api, profile.role)
    }
}
