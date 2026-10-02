package it.unical.geosentinel_mobile.core.session

import io.ktor.http.HttpStatusCode
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

    private val backend = FakeBackend()
    private val store = FakeSecureTokenStore()

    private fun refreshCalls() = backend.requests.count { it.startsWith("POST /api/v1/auth/refresh") }

    // ---- avvio ----------------------------------------------------------------------------------------------

    @Test
    fun `senza token salvati si parte da ospite`() = runTest {
        val (session, _) = sessionWith(backend, store)

        session.restore()

        assertEquals(SessionState.Anonymous, session.state.value)
        assertEquals(UserRole.GUEST, session.state.value.role)
    }

    @Test
    fun `con token salvati si riprende la sessione e il ruolo arriva da users me`() = runTest {
        store.tokens = backend.issueFor("rescuer@test.dev")
        val (session, _) = sessionWith(backend, store)

        session.restore()

        assertEquals(UserRole.RESCUE_OPERATOR, session.state.value.role)
    }

    @Test
    fun `avvio senza rete con token salvati resta loggato con la vista da escursionista`() = runTest {
        store.tokens = backend.issueFor("rescuer@test.dev")
        backend.offline = true
        val (session, _) = sessionWith(backend, store)

        session.restore()

        assertEquals(SessionState.LoggedIn(profile = null), session.state.value)
        assertEquals(UserRole.HIKER, session.state.value.role)
        assertNotNull(store.tokens, "senza rete la sessione non si perde")
    }

    // ---- login e registrazione ------------------------------------------------------------------------------

    @Test
    fun `il login salva i token e legge il profilo con il bearer nuovo`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()

        val result = session.login(" hiker@test.dev ", "password-giusta-1")

        assertTrue(result.isSuccess)
        assertEquals("a1", store.tokens?.accessToken)
        val profile = assertIs<SessionState.LoggedIn>(session.state.value).profile
        assertEquals(UserProfile("id-hiker@test.dev", "hiker@test.dev", "Nome", UserRole.HIKER), profile)
        assertTrue("GET /api/v1/users/me Bearer a1" in backend.requests)
        // login e registrazione partono senza bearer (plugin Auth spento su /auth/login)
        assertTrue("POST /api/v1/auth/login -" in backend.requests)
    }

    @Test
    fun `password sbagliata, 401 e nessuna sessione`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()

        val error = (session.login("hiker@test.dev", "sbagliata").exceptionOrNull() as ApiException).error

        assertIs<ApiError.Unauthorized>(error)
        assertEquals(SessionState.Anonymous, session.state.value)
        assertNull(store.tokens)
        assertEquals(0, refreshCalls(), "un 401 del login non fa partire un refresh")
    }

    @Test
    fun `dopo logout e login di un altro utente parte il bearer nuovo, non quello in cache nel client`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()
        session.login("hiker@test.dev", "password-giusta-1")
        session.logout()

        session.login("rescuer@test.dev", "password-giusta-2")

        assertEquals("a2", store.tokens?.accessToken)
        assertTrue("GET /api/v1/users/me Bearer a2" in backend.requests)
        assertEquals(UserRole.RESCUE_OPERATOR, session.state.value.role)
    }

    @Test
    fun `dopo una sessione scaduta il nuovo login usa i token nuovi, non quelli rimasti nel client`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        val (session, _) = sessionWith(backend, store)
        session.restore() // il plugin Auth carica a1 e lo tiene in memoria
        backend.refreshStatus = HttpStatusCode.Unauthorized
        session.refreshTokens("r1") // scaduta: dentro il refresh la cache del client non si tocca
        backend.refreshStatus = HttpStatusCode.OK

        session.login("rescuer@test.dev", "password-giusta-2")

        assertTrue("GET /api/v1/users/me Bearer a2" in backend.requests)
        assertEquals(UserRole.RESCUE_OPERATOR, session.state.value.role)
    }

    @Test
    fun `un ruolo nuovo del server diventa la vista da escursionista`() = runTest {
        backend.roles["hiker@test.dev"] = "GUIDA_ALPINA"
        val (session, _) = sessionWith(backend, store)
        session.restore()

        session.login("hiker@test.dev", "password-giusta-1")

        assertEquals(UserRole.HIKER, session.state.value.role)
    }

    @Test
    fun `la registrazione apre la sessione`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()

        val result = session.register(RegisterForm("nuovo@test.dev", "password-lunga-123", "Nuovo", kotlinx.datetime.LocalDate(1990, 1, 1)))

        assertTrue(result.isSuccess)
        assertEquals(UserRole.HIKER, session.state.value.role)
        assertNotNull(store.tokens)
    }

    // ---- logout ---------------------------------------------------------------------------------------------

    @Test
    fun `il logout manda il refresh token al server e svuota la sessione`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()
        session.login("hiker@test.dev", "password-giusta-1")

        session.logout()

        assertTrue("POST /api/v1/auth/logout Bearer a1" in backend.requests)
        assertNull(store.tokens)
        assertEquals(SessionState.Anonymous, session.state.value)
    }

    @Test
    fun `il logout senza rete svuota comunque la sessione locale`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()
        session.login("hiker@test.dev", "password-giusta-1")
        backend.offline = true

        session.logout()

        assertNull(store.tokens)
        assertEquals(SessionState.Anonymous, session.state.value)
    }

    // ---- refresh --------------------------------------------------------------------------------------------

    @Test
    fun `due refresh in parallelo fuori dal client fanno una sola chiamata al server`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        val (session, _) = sessionWith(backend, store)

        val results = listOf(async { session.refreshTokens("r1") }, async { session.refreshTokens("r1") }).awaitAll()

        assertEquals(1, refreshCalls())
        assertEquals(results[0], results[1])
        assertEquals("r2", store.tokens?.refreshToken)
    }

    @Test
    fun `un refresh con un token già sostituito restituisce quelli salvati senza chiamare il server`() = runTest {
        backend.issueFor("hiker@test.dev")
        store.tokens = backend.issueFor("hiker@test.dev") // salvati: a2/r2
        val (session, _) = sessionWith(backend, store)

        val tokens = session.refreshTokens("r1")

        assertEquals("r2", tokens?.refreshToken)
        assertEquals(0, refreshCalls())
    }

    @Test
    fun `un 401 del refresh chiude la sessione come scaduta`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        backend.refreshStatus = HttpStatusCode.Unauthorized
        val (session, _) = sessionWith(backend, store)
        session.restore()

        assertNull(session.refreshTokens("r1"))

        assertEquals(SessionState.Expired, session.state.value)
        assertEquals(UserRole.GUEST, session.state.value.role)
        assertNull(store.tokens)
    }

    @Test
    fun `un refresh senza rete non tocca i token salvati`() = runTest {
        val saved = backend.issueFor("hiker@test.dev")
        store.tokens = saved
        val (session, _) = sessionWith(backend, store)
        backend.offline = true

        assertNull(session.refreshTokens("r1"))

        assertEquals(saved, store.tokens, "si riprova con lo stesso refresh token (periodo di grazia ADR-0019)")
    }

    @Test
    fun `un 503 del refresh non chiude la sessione`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        backend.refreshStatus = HttpStatusCode.ServiceUnavailable
        val (session, _) = sessionWith(backend, store)

        assertNull(session.refreshTokens("r1"))

        assertNotNull(store.tokens)
    }

    @Test
    fun `restore e chiamato due volte non rilegge la sessione`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()
        store.tokens = backend.issueFor("hiker@test.dev")

        session.restore()

        assertEquals(SessionState.Anonymous, session.state.value)
    }
}
