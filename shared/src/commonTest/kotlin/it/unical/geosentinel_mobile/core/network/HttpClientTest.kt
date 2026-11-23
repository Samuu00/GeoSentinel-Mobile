// Test della configurazione del client HTTP creato da HttpClientFactory: refresh del token su 401 (uno solo alla
// volta), bearer inviato solo al backend, redirect non seguiti, ripetizione automatica delle richieste secondo le
// regole dell'API e tolleranza verso i valori di enum sconosciuti.
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

/** Indirizzo del backend finto: solo verso questo schema, host e porta il client invia il bearer. */
private const val BASE_URL = "http://api.test"

/** Verifica i plugin di autenticazione e di retry del client HTTP con un motore finto. */
@OptIn(ExperimentalCoroutinesApi::class)
class HttpClientTest {

    /**
     * Repository della sessione finto. Parte con un access token "old"; il refresh restituisce [refreshResult] e conta
     * quante volte viene chiamato.
     */
    private class FakeSession : SessionRepository {
        /** Token correnti della sessione. */
        var tokens: AuthTokens? = AuthTokens(accessToken = "old", refreshToken = "r1", accessExpiresAt = Instant.fromEpochSeconds(0))
        /** Token restituiti dal refresh; null simula un refresh fallito. */
        var refreshResult: AuthTokens? = AuthTokens(accessToken = "new", refreshToken = "r2", accessExpiresAt = Instant.fromEpochSeconds(0))
        /** Numero di refresh richiesti. */
        var refreshCalls = 0

        /** Restituisce i token correnti. */
        override suspend fun getTokens() = tokens

        /**
         * Simula un refresh che dura 100 ms, così le richieste parallele arrivano mentre il refresh è in corso, e salva i
         * nuovi token se il refresh riesce.
         */
        override suspend fun refreshTokens(refreshToken: String): AuthTokens? {
            refreshCalls++
            delay(100)
            return refreshResult?.also { tokens = it }
        }
    }

    /**
     * Crea il client dell'app sopra un motore finto che risponde con [handler]. Il motore gira sul dispatcher del test,
     * quindi le attese tra un tentativo e l'altro usano il tempo virtuale.
     */
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

    /** Risposta 401 con l'header WWW-Authenticate che fa scattare il refresh nel plugin Auth. */
    private fun MockRequestHandleScope.unauthorized() =
        respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))

    /** Risposta vuota con lo stato e gli header indicati. */
    private fun MockRequestHandleScope.status(code: HttpStatusCode, vararg headers: Pair<String, String>) =
        respond("", code, headersOf(*headers.map { it.first to listOf(it.second) }.toTypedArray()))


    /** Dopo un 401 il client fa il refresh e ripete la richiesta una sola volta con il nuovo token. */
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

    /** Due richieste parallele che ricevono 401 producono un solo refresh: il refresh token vale una volta sola. */
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

    /**
     * Se il refresh non restituisce token nuovi il 401 arriva al chiamante, ma il client non svuota la sessione:
     * decidere se la sessione è finita spetta al repository, che lo fa solo su un 401 del refresh e mai per un errore
     * di rete.
     */
    @Test
    fun `se il refresh non da token nuovi resta il 401 e il client non svuota la sessione`() = runTest {
        val session = FakeSession().apply { refreshResult = null }
        var calls = 0
        val client = client(session) { calls++; unauthorized() }

        val response = client.get("/api/v1/users/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, calls)
        assertEquals(1, session.refreshCalls)
        assertNotNull(session.tokens)
    }

    /** Una richiesta verso un altro host (lo storage delle foto) parte senza bearer. */
    @Test
    fun `il token va solo al backend e non ad altri host`() = runTest {
        var authorization: String? = "non letto"
        val client = client { request -> authorization = request.headers[HttpHeaders.Authorization]; status(HttpStatusCode.OK) }

        client.get("http://storage.test/bucket/foto.jpg")

        assertNull(authorization)
    }

    /** Un 401 da un altro host non fa partire il refresh e la richiesta non viene ripetuta con il token. */
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

    /** Lo stesso host su una porta diversa è considerato un altro server e non riceve il bearer. */
    @Test
    fun `il token non va allo stesso host su un'altra porta`() = runTest {
        var authorization: String? = "non letto"
        val client = client { request -> authorization = request.headers[HttpHeaders.Authorization]; status(HttpStatusCode.OK) }

        client.get("http://api.test:9000/bucket/foto.jpg")

        assertNull(authorization)
    }

    /** Un 401 sul login significa credenziali errate: non fa partire il refresh e la richiesta parte una volta sola senza bearer. */
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

    /** I redirect non vengono seguiti, così il bearer non può essere inoltrato a un host diverso indicato da Location. */
    @Test
    fun `un redirect non si segue`() = runTest {
        var calls = 0
        val client = client { calls++; status(HttpStatusCode.Found, HttpHeaders.Location to "http://altro.test/x") }

        val response = client.get("/api/v1/trails")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(1, calls)
    }


    /** Un 429 con Retry-After di 2 secondi viene ripetuto dopo almeno 2 secondi (di tempo virtuale). */
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

    /** Un 409 con Retry-After (risorsa occupata) su una POST con Idempotency-Key viene ripetuto. */
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

    /** 422 e 409 senza Retry-After sono risposte definitive e non vengono ripetuti. */
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

    /** Se il Retry-After supera il limite di attesa del client la risposta arriva subito al chiamante invece di bloccarlo. */
    @Test
    fun `un Retry-After oltre il limite non blocca la richiesta`() = runTest {
        var calls = 0
        val client = client { calls++; status(HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "600") }

        val response = client.post("/api/v1/auth/login")

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(1, calls)
    }

    /**
     * Un errore di rete si ritenta su una GET, che è idempotente, e su una POST con Idempotency-Key; una POST senza
     * chiave non si ripete, perché il server potrebbe averla già eseguita.
     */
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


    /**
     * Un valore di enum sconosciuto e un campo in più nella risposta non fanno fallire la deserializzazione: il ruolo
     * diventa il valore di riserva generato dal client.
     */
    @Test
    fun `un valore di enum sconosciuto non fa fallire la deserializzazione`() = runTest {
        val client = client {
            respond(
                """{"id":"u1","email":"a@b.c","displayName":"A","role":"SUPER_ADMIN","hasPassword":true,"mfaEnabled":false,"createdAt":"2026-10-02T10:00:00Z","nuovoCampo":1}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val profile = UserProfileApi(BASE_URL, client).getMyProfile().body()

        assertEquals(UserProfileResponse.Role.unknown_default_open_api, profile.role)
    }
}
