// Test di SessionManager, che gestisce la sessione dell'utente: ripristino all'avvio, login, registrazione,
// logout, refresh dei token (uno alla volta), operazioni sull'account, login con Google e recupero della password.
// Molti test riguardano la concorrenza: refresh, logout e cambio password non devono corrompere la sessione.
package it.unical.geosentinel_mobile.core.session

import io.ktor.http.HttpStatusCode
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.io.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifica gli stati della sessione e la gestione dei token contro il backend finto e uno store in memoria. */
class SessionManagerTest {

    private val backend = FakeBackend()
    /** Store dei token in memoria, ispezionato dai test per controllare cosa viene salvato. */
    private val store = FakeSecureTokenStore()

    /** Numero di richieste di refresh arrivate al server finto. */
    private fun refreshCalls() = backend.requests.count { it.startsWith("POST /api/v1/auth/refresh") }


    /** Senza token salvati l'app parte da ospite. */
    @Test
    fun `senza token salvati si parte da ospite`() = runTest {
        val (session, _) = sessionWith(backend, store)

        session.restore()

        assertEquals(SessionState.Anonymous, session.state.value)
        assertEquals(UserRole.GUEST, session.state.value.role)
    }

    /** Con token salvati la sessione viene ripresa e il ruolo arriva da GET /users/me. */
    @Test
    fun `con token salvati si riprende la sessione e il ruolo arriva da users me`() = runTest {
        store.tokens = backend.issueFor("rescuer@test.dev")
        val (session, _) = sessionWith(backend, store)

        session.restore()

        assertEquals(UserRole.RESCUE_OPERATOR, session.state.value.role)
    }

    /**
     * All'avvio senza rete, con token salvati, l'utente resta autenticato senza profilo e vede l'app come un
     * escursionista: un errore di rete non deve mai far perdere la sessione.
     */
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


    /**
     * Il login (con l'email ripulita dagli spazi) salva i token e legge il profilo con il nuovo bearer. La richiesta
     * di login parte senza bearer, perché su quella rotta il plugin Auth è spento.
     */
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
        assertTrue("POST /api/v1/auth/login -" in backend.requests)
    }

    /** Con una password sbagliata il login fallisce con 401, non apre la sessione e non fa partire nessun refresh. */
    @Test
    fun `password sbagliata - 401 e nessuna sessione`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()

        val error = (session.login("hiker@test.dev", "sbagliata").exceptionOrNull() as ApiException).error

        assertIs<ApiError.Unauthorized>(error)
        assertEquals(SessionState.Anonymous, session.state.value)
        assertNull(store.tokens)
        assertEquals(0, refreshCalls(), "un 401 del login non fa partire un refresh")
    }

    /** Dopo il logout e il login di un altro utente parte il bearer nuovo, non quello rimasto in memoria nel client. */
    @Test
    fun `dopo logout e login di un altro utente parte il bearer nuovo - non quello in cache nel client`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()
        session.login("hiker@test.dev", "password-giusta-1")
        session.logout()

        session.login("rescuer@test.dev", "password-giusta-2")

        assertEquals("a2", store.tokens?.accessToken)
        assertTrue("GET /api/v1/users/me Bearer a2" in backend.requests)
        assertEquals(UserRole.RESCUE_OPERATOR, session.state.value.role)
    }

    /** Dopo una sessione scaduta il nuovo login usa i token nuovi, non quelli rimasti nella cache del client. */
    @Test
    fun `dopo una sessione scaduta il nuovo login usa i token nuovi - non quelli rimasti nel client`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        val (session, _) = sessionWith(backend, store)
        // Il plugin Auth carica a1 e lo tiene in memoria.
        session.restore()
        backend.refreshStatus = HttpStatusCode.Unauthorized
        // La sessione scade, ma dentro il refresh la cache del client non viene toccata.
        session.refreshTokens("r1")
        backend.refreshStatus = HttpStatusCode.OK

        session.login("rescuer@test.dev", "password-giusta-2")

        assertTrue("GET /api/v1/users/me Bearer a2" in backend.requests)
        assertEquals(UserRole.RESCUE_OPERATOR, session.state.value.role)
    }

    /** Un ruolo sconosciuto inviato dal server viene trattato come quello di un escursionista. */
    @Test
    fun `un ruolo nuovo del server diventa la vista da escursionista`() = runTest {
        backend.roles["hiker@test.dev"] = "GUIDA_ALPINA"
        val (session, _) = sessionWith(backend, store)
        session.restore()

        session.login("hiker@test.dev", "password-giusta-1")

        assertEquals(UserRole.HIKER, session.state.value.role)
    }

    /** La registrazione apre la sessione con il ruolo HIKER e salva i token. */
    @Test
    fun `la registrazione apre la sessione`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()

        val result = session.register(RegisterForm("nuovo@test.dev", "password-lunga-123", "Nuovo", kotlinx.datetime.LocalDate(1990, 1, 1)))

        assertTrue(result.isSuccess)
        assertEquals(UserRole.HIKER, session.state.value.role)
        assertNotNull(store.tokens)
    }


    /** Il logout invia al server la richiesta di revoca e poi svuota la sessione e lo store. */
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

    /** Anche senza rete il logout svuota la sessione locale. */
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


    /**
     * Due refresh in parallelo producono una sola chiamata al server e lo stesso risultato: il refresh token vale una
     * volta sola, quindi un secondo uso lo farebbe rifiutare.
     */
    @Test
    fun `due refresh in parallelo fuori dal client fanno una sola chiamata al server`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        val (session, _) = sessionWith(backend, store)

        val results = listOf(async { session.refreshTokens("r1") }, async { session.refreshTokens("r1") }).awaitAll()

        assertEquals(1, refreshCalls())
        assertEquals(results[0], results[1])
        assertEquals("r2", store.tokens?.refreshToken)
    }

    /**
     * Se il refresh token richiesto è già stato sostituito, si restituiscono i token salvati senza chiamare il server:
     * un'altra richiesta ha già fatto il refresh.
     */
    @Test
    fun `un refresh con un token già sostituito restituisce quelli salvati senza chiamare il server`() = runTest {
        backend.issueFor("hiker@test.dev")
        store.tokens = backend.issueFor("hiker@test.dev")
        val (session, _) = sessionWith(backend, store)

        val tokens = session.refreshTokens("r1")

        assertEquals("r2", tokens?.refreshToken)
        assertEquals(0, refreshCalls())
    }

    /** Un 401 del refresh chiude la sessione come scaduta e cancella i token. */
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

    /**
     * Un refresh fallito per la rete non tocca i token salvati: si riproverà con lo stesso refresh token, che il
     * server accetta ancora per un breve periodo di grazia.
     */
    @Test
    fun `un refresh senza rete non tocca i token salvati`() = runTest {
        val saved = backend.issueFor("hiker@test.dev")
        store.tokens = saved
        val (session, _) = sessionWith(backend, store)
        backend.offline = true

        assertNull(session.refreshTokens("r1"))

        assertEquals(saved, store.tokens, "si riprova con lo stesso refresh token (periodo di grazia ADR-0019)")
    }

    /** Un 503 del refresh è un problema temporaneo del server e non chiude la sessione. */
    @Test
    fun `un 503 del refresh non chiude la sessione`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        backend.refreshStatus = HttpStatusCode.ServiceUnavailable
        val (session, _) = sessionWith(backend, store)

        assertNull(session.refreshTokens("r1"))

        assertNotNull(store.tokens)
    }

    /** restore lavora una volta sola: una seconda chiamata non rilegge lo store. */
    @Test
    fun `restore e chiamato due volte non rilegge la sessione`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()
        store.tokens = backend.issueFor("hiker@test.dev")

        session.restore()

        assertEquals(SessionState.Anonymous, session.state.value)
    }


    /** Crea una sessione e fa l'accesso con le credenziali indicate. */
    private suspend fun TestScope.loggedIn(email: String = "hiker@test.dev", password: String = "password-giusta-1"): SessionManager {
        val (session, _) = sessionWith(backend, store)
        session.restore()
        assertTrue(session.login(email, password).isSuccess)
        return session
    }

    /** Il cambio del nome aggiorna il profilo in sessione e il nome viene inviato senza spazi ai lati. */
    @Test
    fun `il cambio del nome aggiorna il profilo in sessione`() = runTest {
        val session = loggedIn()

        assertTrue(session.updateDisplayName("  Anna  ").isSuccess)

        assertEquals("Anna", assertIs<SessionState.LoggedIn>(session.state.value).profile?.displayName)
        assertEquals("Anna", backend.names["hiker@test.dev"], "il nome si manda senza spazi ai lati")
    }

    /** Dopo il cambio password i token nuovi vengono salvati e le richieste successive usano il nuovo bearer. */
    @Test
    fun `cambio password - token nuovi salvati e le richieste dopo usano il bearer nuovo`() = runTest {
        val session = loggedIn()

        assertTrue(session.changePassword("password-giusta-1", "nuova-password-123").isSuccess)
        assertEquals("a2", store.tokens?.accessToken)

        // Se il client non dimenticasse il token in memoria, il plugin invierebbe ancora a1, già revocato: ci sarebbe un
        // 401 in più (assorbito da refreshTokens perché i token salvati sono già cambiati) e il token vecchio tornerebbe
        // in rete.
        assertTrue(session.updateDisplayName("Anna").isSuccess)
        assertEquals(listOf("PATCH /api/v1/users/me Bearer a2"), backend.requests.filter { it.startsWith("PATCH") })
        assertEquals(0, refreshCalls())
        assertIs<SessionState.LoggedIn>(session.state.value)
    }

    /**
     * Il cambio password revoca i token degli altri dispositivi: al primo 401 un altro telefono torna al login,
     * mentre questo resta connesso.
     */
    @Test
    fun `cambio password - un altro dispositivo torna al login al primo 401`() = runTest {
        val otherStore = FakeSecureTokenStore(backend.issueFor("hiker@test.dev"))
        val session = loggedIn()

        assertTrue(session.changePassword("password-giusta-1", "nuova-password-123").isSuccess)

        val (other, _) = sessionWith(backend, otherStore)
        other.restore()
        assertEquals(SessionState.Expired, other.state.value)
        assertNull(otherStore.tokens)
        assertIs<SessionState.LoggedIn>(session.state.value, "questo telefono resta connesso")
    }

    /** Con la password attuale sbagliata il server risponde 422 business-rule e i token restano quelli di prima. */
    @Test
    fun `password attuale sbagliata - 422 business-rule e i token restano`() = runTest {
        val session = loggedIn()
        val before = store.tokens

        val result = session.changePassword("password-sbagliata", "nuova-password-123")

        val error = assertIs<ApiError.BusinessRule>(assertIs<ApiException>(result.exceptionOrNull()).error)
        assertEquals("business-rule", error.slug)
        assertEquals(before, store.tokens)
        assertIs<SessionState.LoggedIn>(session.state.value)
    }

    /** Dopo la cancellazione dell'account si torna ospiti e le vecchie credenziali non funzionano più. */
    @Test
    fun `cancellazione - si torna ospiti e le vecchie credenziali non valgono piu`() = runTest {
        val session = loggedIn()

        assertTrue(session.deleteAccount().isSuccess)

        assertEquals(SessionState.Anonymous, session.state.value)
        assertNull(store.tokens)
        val login = session.login("hiker@test.dev", "password-giusta-1")
        assertIs<ApiError.Unauthorized>(assertIs<ApiException>(login.exceptionOrNull()).error)
    }

    /** Il personale non può cancellare il proprio account: 409 account-managed e la sessione resta. */
    @Test
    fun `cancellazione del personale - 409 account-managed e la sessione resta`() = runTest {
        val session = loggedIn("rescuer@test.dev", "password-giusta-2")

        val result = session.deleteAccount()

        val error = assertIs<ApiError.Conflict>(assertIs<ApiException>(result.exceptionOrNull()).error)
        assertEquals("account-managed", error.slug)
        assertEquals(UserRole.RESCUE_OPERATOR, session.state.value.role)
        assertNotNull(store.tokens)
    }


    /** Un logout fatto mentre un refresh è in corso non deve far rivivere la sessione quando il refresh termina. */
    @Test
    fun `un logout durante un refresh non fa rivivere la sessione`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        val (session, _) = sessionWith(backend, store)
        session.restore()

        val refresh = async { session.refreshTokens("r1") }
        // A questo punto il refresh è in corso.
        testScheduler.runCurrent()
        session.logout()
        refresh.await()

        assertNull(store.tokens)
        assertEquals(SessionState.Anonymous, session.state.value)
    }

    /** Il 401 di un refresh partito prima di un nuovo login non chiude la sessione aperta da quel login. */
    @Test
    fun `un 401 di un refresh vecchio non chiude la sessione aperta dopo con un login`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        backend.refreshStatus = HttpStatusCode.Unauthorized
        val (session, _) = sessionWith(backend, store)
        session.restore()

        val refresh = async { session.refreshTokens("r1") }
        testScheduler.runCurrent()
        assertTrue(session.login("rescuer@test.dev", "password-giusta-2").isSuccess)
        refresh.await()

        assertEquals(UserRole.RESCUE_OPERATOR, session.state.value.role)
        assertNotNull(store.tokens)
    }

    /**
     * Se il cambio password viene annullato a metà (per esempio con Indietro durante l'invio), i token nuovi vengono
     * salvati comunque: l'operazione gira in un contesto non annullabile, perché il server ha già revocato i vecchi.
     */
    @Test
    fun `cambio password annullato a metà salva comunque i token nuovi`() = runTest {
        val session = loggedIn()

        val job = launch(start = CoroutineStart.UNDISPATCHED) { session.changePassword("password-giusta-1", "password-nuova-1") }
        // Annullamento simulato, come un tocco su Indietro durante l'invio; join termina solo quando la parte non
        // annullabile è conclusa.
        job.cancel()
        job.join()

        assertEquals("r2", store.tokens?.refreshToken)
        assertIs<SessionState.LoggedIn>(session.state.value)
    }

    /** Se la cancellazione dell'account viene annullata a metà, la sessione locale viene chiusa comunque. */
    @Test
    fun `cancellazione annullata a metà chiude comunque la sessione locale`() = runTest {
        val session = loggedIn()

        val job = launch(start = CoroutineStart.UNDISPATCHED) { session.deleteAccount() }
        job.cancel()
        job.join()

        assertNull(store.tokens)
        assertEquals(SessionState.Anonymous, session.state.value)
    }

    /** Se i token non si possono salvare il login fallisce senza crash e l'utente resta ospite. */
    @Test
    fun `token non salvabili - il login fallisce senza crash e si resta ospiti`() = runTest {
        val (session, _) = sessionWith(backend, FailingStore())
        session.restore()

        val result = session.login("hiker@test.dev", "password-giusta-1")

        assertTrue(result.isFailure)
        assertEquals(SessionState.Anonymous, session.state.value)
    }

    /** Se lo store non si può leggere all'avvio si parte da ospite senza crash. */
    @Test
    fun `store illeggibile all'avvio - si parte da ospite senza crash`() = runTest {
        val (session, _) = sessionWith(backend, FailingStore())

        session.restore()

        assertEquals(SessionState.Anonymous, session.state.value)
    }

    /** Due chiamate parallele a restore leggono il profilo una sola volta. */
    @Test
    fun `restore chiamato due volte in parallelo legge il profilo una volta sola`() = runTest {
        store.tokens = backend.issueFor("hiker@test.dev")
        val (session, _) = sessionWith(backend, store)

        listOf(async { session.restore() }, async { session.restore() }).awaitAll()

        assertEquals(1, backend.requests.count { it.startsWith("GET /api/v1/users/me") })
    }

    /**
     * Durante un cambio password il server revoca i token vecchi, quindi un refresh in corso riceve 401: questo 401
     * non deve chiudere la sessione, che riceverà i token nuovi dal cambio password.
     */
    @Test
    fun `un refresh rifiutato durante il cambio password non chiude la sessione`() = runTest {
        val session = loggedIn()

        val change = async { session.changePassword("password-giusta-1", "password-nuova-1") }
        // Il cambio password è in corso e il server ha già revocato i token vecchi.
        testScheduler.runCurrent()
        backend.refreshStatus = HttpStatusCode.Unauthorized
        assertNull(session.refreshTokens("r1"))

        assertTrue(change.await().isSuccess)
        assertIs<SessionState.LoggedIn>(session.state.value)
        assertEquals("r2", store.tokens?.refreshToken)
    }

    /** Finito il cambio password (anche se fallito), un 401 del refresh torna a chiudere la sessione come scaduta. */
    @Test
    fun `fuori da un cambio password un 401 del refresh chiude ancora la sessione`() = runTest {
        val session = loggedIn()
        session.changePassword("password-sbagliata", "password-nuova-1")
        backend.refreshStatus = HttpStatusCode.Unauthorized

        session.refreshTokens("r1")

        assertEquals(SessionState.Expired, session.state.value)
    }

    /** Store che fallisce sempre, come con il disco pieno, un file danneggiato o il Keychain non disponibile. */
    private class FailingStore : SecureTokenStore {
        /** Fallisce sempre la lettura. */
        override suspend fun read(): AuthTokens? = throw IOException("store illeggibile")
        /** Fallisce sempre la scrittura. */
        override suspend fun write(tokens: AuthTokens) = throw IOException("disco pieno")
        /** Fallisce sempre la cancellazione. */
        override suspend fun clear() = throw IOException("disco pieno")
    }


    /**
     * Al primo accesso con Google senza data di nascita il server chiede la data; con la data la sessione si apre su
     * un account senza password. La rotta è pubblica e parte sempre senza bearer.
     */
    @Test
    fun `primo accesso con Google senza data chiede la data e con la data apre la sessione senza password`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()

        val first = session.loginWithGoogle("google:nuovo@gmail.com")
        assertEquals("birth-date-required", ((first.exceptionOrNull() as ApiException).error as ApiError.BusinessRule).slug)
        assertEquals(SessionState.Anonymous, session.state.value)

        assertTrue(session.loginWithGoogle("google:nuovo@gmail.com", LocalDate(1990, 5, 1)).isSuccess)
        val profile = (session.state.value as SessionState.LoggedIn).profile!!
        assertEquals(false, profile.hasPassword)
        assertTrue(backend.requests.filter { "/auth/google" in it }.all { it.endsWith(" -") })
    }

    /** Un ID token di Google rifiutato non apre la sessione e non salva token. */
    @Test
    fun `token Google rifiutato non apre la sessione`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()

        val result = session.loginWithGoogle("falso")

        assertTrue((result.exceptionOrNull() as ApiException).error is ApiError.Unauthorized)
        assertEquals(SessionState.Anonymous, session.state.value)
        assertNull(store.tokens)
    }

    /**
     * Il recupero della password funziona senza bearer: la richiesta del codice risponde allo stesso modo anche per
     * un'email sconosciuta (così non rivela quali account esistono), un codice sbagliato viene rifiutato e con quello
     * giusto la nuova password vale per il login.
     */
    @Test
    fun `reset della password senza bearer e con il codice giusto la password nuova vale`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()

        assertTrue(session.requestPasswordReset(" hiker@test.dev ").isSuccess)
        assertTrue(session.requestPasswordReset("nessuno@test.dev").isSuccess)
        val wrong = session.confirmPasswordReset("hiker@test.dev", "SBAGLIATO", "nuova-password-lunga")
        assertEquals("invalid-reset-code", ((wrong.exceptionOrNull() as ApiException).error as ApiError.BusinessRule).slug)
        assertTrue(session.confirmPasswordReset("hiker@test.dev", " ABCD1234EFGH5678 ", "nuova-password-lunga").isSuccess)

        assertTrue(session.login("hiker@test.dev", "nuova-password-lunga").isSuccess)
        assertTrue(backend.requests.filter { "/password-reset/" in it }.all { it.endsWith(" -") })
    }

    /**
     * Impostare la password con il codice via email revoca tutte le sessioni dell'utente, quindi anche quella di
     * questo telefono si chiude come scaduta.
     */
    @Test
    fun `password impostata dal profilo chiude la sessione di questo telefono come scaduta`() = runTest {
        val (session, _) = sessionWith(backend, store)
        session.restore()
        session.login("hiker@test.dev", "password-giusta-1")

        session.requestPasswordReset("hiker@test.dev")
        session.confirmPasswordReset("hiker@test.dev", "ABCD1234EFGH5678", "nuova-password-lunga")

        assertEquals(SessionState.Expired, session.state.value)
        assertNull(store.tokens)
    }
}
