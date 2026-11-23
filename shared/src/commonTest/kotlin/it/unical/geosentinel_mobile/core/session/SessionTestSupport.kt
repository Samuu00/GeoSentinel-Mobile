// Supporto comune ai test della sessione e di tutte le feature: un backend finto che simula le rotte di
// autenticazione, profilo, verifica in due passaggi e amministrazione, uno store dei token in memoria e la funzione
// che collega SessionManager e client HTTP come avviene nell'app. Le altre feature aggiungono le proprie rotte
// tramite FakeBackend.extraRoutes.
package it.unical.geosentinel_mobile.core.session

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.network.HttpClientFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.io.IOException

/** Indirizzo base dell'API finta usato da tutti i test. */
const val TEST_BASE_URL = "http://api.test"
/** Chiave TOTP restituita dal backend finto all'avvio della configurazione della 2FA. */
const val MFA_SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
/** Codice TOTP che il backend finto accetta per completare l'attivazione della 2FA. */
const val MFA_SETUP_CODE = "123456"
/** Codice che il server invia per email durante la configurazione della 2FA, nel formato in cui lo mostra. */
const val MFA_EMAIL_CODE = "ABCDEFGH-23456789"

/** Store dei token in memoria, al posto di Keystore e Keychain; i test possono leggere e impostare [tokens]. */
class FakeSecureTokenStore(var tokens: AuthTokens? = null) : SecureTokenStore {
    /** Restituisce i token salvati, o null se non ce ne sono. */
    override suspend fun read() = tokens
    /** Salva i token. */
    override suspend fun write(tokens: AuthTokens) { this.tokens = tokens }
    /** Cancella i token. */
    override suspend fun clear() { tokens = null }
}

/**
 * Backend finto per le rotte di autenticazione e del profilo utente, usato tramite il MockEngine di Ktor. Le
 * password valide sono in [users] (email -> password); ogni login emette una coppia nuova di token "a<n>" e "r<n>".
 * Come sul server, il profilo si ricava dal bearer della richiesta e il refresh token vale una volta sola.
 */
class FakeBackend {
    /** Credenziali valide: email -> password. */
    val users = mutableMapOf("hiker@test.dev" to "password-giusta-1", "rescuer@test.dev" to "password-giusta-2")
    /** Ruolo di ogni utente, restituito nel profilo. */
    val roles = mutableMapOf("hiker@test.dev" to "HIKER", "rescuer@test.dev" to "RESCUE_OPERATOR")
    /** Nome visualizzato di ogni utente, se è stato cambiato; altrimenti il profilo riporta "Nome". */
    val names = mutableMapOf<String, String>()
    /**
     * Account collegati a Google, che non hanno password. Nel login con Google un ID token nella forma "google:<email>"
     * è valido, ogni altro produce 401.
     */
    val googleOnly = mutableSetOf<String>()
    /** Ultimo codice di recupero della password inviato per email, solo agli account esistenti. */
    val resetCodes = mutableMapOf<String, String>()
    /**
     * Utenti con la 2FA attiva: email -> codice TOTP valido in questo momento. Come sul server, ogni codice TOTP vale
     * una volta sola. La configurazione restituisce sempre MFA_SECRET e l'attivazione accetta MFA_SETUP_CODE insieme a
     * MFA_EMAIL_CODE.
     */
    val mfaTotp = mutableMapOf<String, String>()
    /** Codici di recupero ancora validi di ogni utente con la 2FA. */
    val mfaRecovery = mutableMapOf<String, MutableSet<String>>()
    /** Coppie email e codice TOTP già usati, per rifiutare il riuso di un codice. */
    private val usedTotp = mutableSetOf<String>()
    /** Foto profilo: email -> id del media usato come avatar. */
    val avatars = mutableMapOf<String, String>()
    /** Se vero, ogni richiesta fallisce con un errore di rete. */
    var offline = false
    /** Stato HTTP con cui risponde il refresh; se diverso da 200 il refresh fallisce. */
    var refreshStatus = HttpStatusCode.OK
    /** Se impostato, il login risponde con questo stato (per esempio 429) e un Retry-After di 60 secondi. */
    var loginStatus: HttpStatusCode? = null
    /** Se impostato, la registrazione risponde con questo stato e questo ProblemDetail in JSON. */
    var registerProblem: Pair<HttpStatusCode, String>? = null
    /** Registro delle richieste ricevute, nella forma "METODO percorso bearer" (con "-" se manca il bearer). */
    val requests = mutableListOf<String>()
    /**
     * Rotte aggiuntive installate dai test delle altre feature (catalogo, media, prenotazioni, ...). Ricevono la
     * richiesta e l'email dell'utente del bearer; se restituiscono null la rotta non è gestita e si risponde 404.
     */
    var extraRoutes: (suspend MockRequestHandleScope.(request: HttpRequestData, bearerOwner: String?) -> HttpResponseData?)? = null
    /** Access token validi -> email del proprietario. */
    private val owners = mutableMapOf<String, String>()
    /** Refresh token validi -> email del proprietario; un refresh token si rimuove quando viene usato. */
    private val refreshOwners = mutableMapOf<String, String>()
    /** Contatore usato per numerare le coppie di token emesse. */
    private var counter = 0

    /** Emette e registra una nuova coppia di token per l'utente [email]; i test la usano anche per preparare una sessione salvata. */
    fun issueFor(email: String): AuthTokens {
        counter++
        owners["a$counter"] = email
        refreshOwners["r$counter"] = email
        return AuthTokens("a$counter", "r$counter", kotlin.time.Instant.fromEpochSeconds(1_800_000_000))
    }

    /**
     * Risponde a una richiesta HTTP del client simulando il backend. Registra la richiesta, sceglie la rotta in base a
     * metodo e percorso e, per quelle che non gestisce, passa a [extraRoutes].
     */
    suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        if (offline) throw IOException("rete assente")
        val path = request.url.encodedPath
        val bearer = request.headers[HttpHeaders.Authorization]
        requests += "${request.method.value} $path ${bearer ?: "-"}"
        val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
        return when {
            request.method == HttpMethod.Post && path == "/api/v1/auth/login" -> {
                loginStatus?.let { return problem(it, "rate-limit-exceeded", retryAfter = "60") }
                val email = field(body, "email")
                if (users[email] != field(body, "password")) return problem(HttpStatusCode.Unauthorized, "unauthorized")
                checkMfa(email, body)?.let { return problem(HttpStatusCode.Unauthorized, it) }
                tokens(issueFor(email))
            }
            request.method == HttpMethod.Post && path == "/api/v1/auth/register" -> {
                registerProblem?.let { (status, json) -> return respond(json, status, headersOf(HttpHeaders.ContentType, "application/problem+json")) }
                val email = field(body, "email")
                users[email] = field(body, "password")
                roles[email] = "HIKER"
                respond(tokenJson(issueFor(email)), HttpStatusCode.Created, json)
            }
            request.method == HttpMethod.Post && path == "/api/v1/auth/refresh" -> {
                // Il ritardo fa sì che due refresh lanciati in parallelo si sovrappongano, per verificare che il client ne faccia uno solo.
                delay(100)
                if (refreshStatus != HttpStatusCode.OK) return problem(refreshStatus, "unauthorized")
                val email = refreshOwners.remove(field(body, "refreshToken")) ?: return problem(HttpStatusCode.Unauthorized, "unauthorized")
                tokens(issueFor(email))
            }
            request.method == HttpMethod.Post && path == "/api/v1/auth/logout" -> respond("", HttpStatusCode.NoContent)
            request.method == HttpMethod.Post && path == "/api/v1/auth/google" -> {
                val email = field(body, "idToken").removePrefix("google:").takeIf { field(body, "idToken").startsWith("google:") }
                    ?: return problem(HttpStatusCode.Unauthorized, "unauthorized")
                if (roles[email]?.let { it != "HIKER" } == true) return problem(HttpStatusCode.Conflict, "account-managed")
                val created = email !in roles
                if (!created) checkMfa(email, body)?.let { return problem(HttpStatusCode.Unauthorized, it) }
                if (created) {
                    if (field(body, "birthDate").isEmpty()) return problem(HttpStatusCode.UnprocessableEntity, "birth-date-required")
                    roles[email] = "HIKER"
                }
                // Dopo il collegamento a Google la password dell'account non vale più.
                users.remove(email)
                googleOnly += email
                respond(tokenJson(issueFor(email)), if (created) HttpStatusCode.Created else HttpStatusCode.OK, json)
            }
            request.method == HttpMethod.Post && path == "/api/v1/auth/password-reset/request" -> {
                val email = field(body, "email")
                if (email in roles) resetCodes[email] = "ABCD1234EFGH5678"
                respond("", HttpStatusCode.NoContent)
            }
            request.method == HttpMethod.Post && path == "/api/v1/auth/password-reset/confirm" -> {
                val email = field(body, "email")
                if (resetCodes[email] == null || resetCodes[email] != field(body, "code")) {
                    return problem(HttpStatusCode.UnprocessableEntity, "invalid-reset-code")
                }
                resetCodes.remove(email)
                users[email] = field(body, "newPassword")
                googleOnly -= email
                revokeAll(email)
                respond("", HttpStatusCode.NoContent)
            }
            request.method == HttpMethod.Get && path == "/api/v1/users/me" -> {
                val email = owners[bearer?.removePrefix("Bearer ")] ?: return problem(HttpStatusCode.Unauthorized, "unauthorized")
                respond(profileJson(email), HttpStatusCode.OK, json)
            }
            request.method == HttpMethod.Patch && path == "/api/v1/users/me" -> {
                val email = owners[bearer?.removePrefix("Bearer ")] ?: return problem(HttpStatusCode.Unauthorized, "unauthorized")
                names[email] = field(body, "displayName")
                respond(profileJson(email), HttpStatusCode.OK, json)
            }
            // Come il backend: password attuale errata -> 422 business-rule; se il cambio riesce, tutti i token dell'utente
            // (anche quelli degli altri dispositivi) vengono revocati e chi ha cambiato la password riceve una coppia nuova.
            request.method == HttpMethod.Post && path == "/api/v1/users/me/password" -> {
                // Il ritardo permette a un refresh di accavallarsi con il cambio password.
                delay(200)
                val email = owners[bearer?.removePrefix("Bearer ")] ?: return problem(HttpStatusCode.Unauthorized, "unauthorized")
                if (users[email] != field(body, "currentPassword")) return problem(HttpStatusCode.UnprocessableEntity, "business-rule")
                // Con la 2FA attiva serve anche il codice: se manca o è sbagliato la risposta è 422 invalid-mfa-code.
                checkMfa(email, body)?.let { return problem(HttpStatusCode.UnprocessableEntity, "invalid-mfa-code") }
                users[email] = field(body, "newPassword")
                revokeAll(email)
                tokens(issueFor(email))
            }
            // Cancellazione dell'account: consentita solo agli HIKER (il personale riceve 409 account-managed); dopo,
            // l'account non esiste più.
            request.method == HttpMethod.Delete && path == "/api/v1/users/me" -> {
                val email = owners[bearer?.removePrefix("Bearer ")] ?: return problem(HttpStatusCode.Unauthorized, "unauthorized")
                if (roles[email] != "HIKER") return problem(HttpStatusCode.Conflict, "account-managed")
                users.remove(email)
                revokeAll(email)
                respond("", HttpStatusCode.NoContent)
            }
            path.startsWith("/api/v1/users/me/mfa/") && request.method == HttpMethod.Post -> {
                val email = owners[bearer?.removePrefix("Bearer ")] ?: return problem(HttpStatusCode.Unauthorized, "unauthorized")
                val code = field(body, "code")
                val totpOk = mfaTotp[email] == code
                when (path.removePrefix("/api/v1/users/me/mfa/")) {
                    "setup" -> if (email in mfaTotp) {
                        problem(HttpStatusCode.Conflict, "mfa-already-enabled")
                    } else {
                        respond("""{"secret":"$MFA_SECRET","otpauthUri":"otpauth://totp/GeoSentinel:x?secret=$MFA_SECRET"}""", HttpStatusCode.OK, json)
                    }
                    "enable" -> if (code != MFA_SETUP_CODE || field(body, "emailCode").filter(Char::isLetterOrDigit) != MFA_EMAIL_CODE.filter(Char::isLetterOrDigit)) {
                        problem(HttpStatusCode.UnprocessableEntity, "invalid-mfa-code")
                    } else {
                        mfaTotp[email] = MFA_SETUP_CODE
                        respond(recoveryJson(email), HttpStatusCode.OK, json)
                    }
                    "disable" -> if (totpOk || mfaRecovery[email]?.contains(code) == true) {
                        mfaTotp.remove(email)
                        mfaRecovery.remove(email)
                        respond("", HttpStatusCode.NoContent)
                    } else {
                        problem(HttpStatusCode.UnprocessableEntity, "invalid-mfa-code")
                    }
                    "recovery-codes" -> if (totpOk) respond(recoveryJson(email), HttpStatusCode.OK, json) else problem(HttpStatusCode.UnprocessableEntity, "invalid-mfa-code")
                    else -> problem(HttpStatusCode.NotFound, "not-found")
                }
            }
            // Azzeramento della 2FA, come nel backend: solo ADMIN, 404 se l'utente non ha la 2FA, 422 su sé stessi; chiude
            // tutte le sessioni dell'utente azzerato.
            request.method == HttpMethod.Post && path == "/api/v1/admin/mfa-resets" -> {
                val admin = owners[bearer?.removePrefix("Bearer ")] ?: return problem(HttpStatusCode.Unauthorized, "unauthorized")
                if (roles[admin] != "ADMIN") return problem(HttpStatusCode.Forbidden, "forbidden")
                val email = field(body, "email")
                when {
                    email !in mfaTotp -> problem(HttpStatusCode.NotFound, "not-found")
                    email == admin -> problem(HttpStatusCode.UnprocessableEntity, "business-rule")
                    else -> {
                        mfaTotp.remove(email)
                        mfaRecovery.remove(email)
                        revokeAll(email)
                        respond("", HttpStatusCode.NoContent)
                    }
                }
            }
            else -> extraRoutes?.invoke(this, request, owners[bearer?.removePrefix("Bearer ")]) ?: problem(HttpStatusCode.NotFound, "not-found")
        }
    }

    /**
     * Controlla il codice 2FA nel corpo di un login. Restituisce null se l'accesso è consentito (2FA non attiva, TOTP
     * valido mai usato, o codice di recupero valido, che viene consumato), altrimenti lo slug dell'errore:
     * "mfa-required" se il codice manca, "invalid-mfa-code" se è sbagliato o già usato.
     */
    private fun checkMfa(email: String, body: String): String? {
        val totp = mfaTotp[email] ?: return null
        val code = field(body, "mfaCode")
        return when {
            code.isEmpty() -> "mfa-required"
            code == totp && usedTotp.add("$email:$code") -> null
            mfaRecovery[email]?.remove(code) == true -> null
            else -> "invalid-mfa-code"
        }
    }

    /** Genera dieci nuovi codici di recupero per l'utente, li registra come validi e li restituisce in JSON. */
    private fun recoveryJson(email: String): String {
        val codes = (1..10).map { "REC0-$it" }
        mfaRecovery[email] = codes.toMutableSet()
        return """{"codes":[${codes.joinToString(",") { "\"$it\"" }}]}"""
    }

    /** Revoca tutti i token, di accesso e di refresh, dell'utente indicato. */
    private fun revokeAll(email: String) {
        owners.values.removeAll { it == email }
        refreshOwners.values.removeAll { it == email }
    }

    /** JSON del profilo dell'utente, con ruolo, presenza della password, stato della 2FA e avatar. */
    private fun profileJson(email: String) =
        """{"id":"id-$email","email":"$email","displayName":"${names[email] ?: "Nome"}","role":"${roles.getValue(email)}","hasPassword":${email !in googleOnly},""" +
            """"mfaEnabled":${email in mfaTotp},${avatars[email]?.let { "\"avatarMediaId\":\"$it\"," }.orEmpty()}"createdAt":"2026-10-02T10:00:00Z"}"""

    /** Risposta 200 con la coppia di token. */
    private fun MockRequestHandleScope.tokens(tokens: AuthTokens) = respond(tokenJson(tokens), HttpStatusCode.OK, json)

    /** JSON di una coppia di token come la restituisce l'API. */
    private fun tokenJson(tokens: AuthTokens) =
        """{"accessToken":"${tokens.accessToken}","refreshToken":"${tokens.refreshToken}","accessExpiresAt":"2027-01-15T08:00:00Z","tokenType":"Bearer"}"""

    /** Risposta di errore con un ProblemDetail che contiene lo slug indicato e, se richiesto, l'header Retry-After. */
    fun MockRequestHandleScope.problem(status: HttpStatusCode, slug: String, retryAfter: String? = null) = respond(
        """{"type":"https://geosentinel.dev/problems/$slug","title":"x","status":${status.value}}""",
        status,
        if (retryAfter == null) problemJson else headersOf(HttpHeaders.ContentType to listOf("application/problem+json"), HttpHeaders.RetryAfter to listOf(retryAfter)),
    )

    /**
     * Estrae il valore testuale di un campo dal JSON di una richiesta. Il JSON inviato dal client è piatto, quindi
     * basta cercare la chiave con un'espressione regolare; restituisce una stringa vuota se il campo manca.
     */
    private fun field(body: String, name: String) = Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1).orEmpty()

    /** Header per le risposte JSON. */
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    /** Header per le risposte di errore in formato ProblemDetail. */
    private val problemJson = headersOf(HttpHeaders.ContentType, "application/problem+json")
}

/**
 * Crea SessionManager e HttpClient collegati come avviene in Koin, con il motore finto che usa il dispatcher del
 * test, così anche le richieste seguono il tempo virtuale. [store] e [pushToken] si possono sostituire per i test
 * che ne hanno bisogno.
 *
 * @return la coppia sessione e client autenticato.
 */
fun TestScope.sessionWith(
    backend: FakeBackend,
    store: SecureTokenStore = FakeSecureTokenStore(),
    pushToken: suspend () -> String? = { null },
): Pair<SessionManager, HttpClient> {
    // Il client serve alla sessione (per il refresh) e la sessione al client: si risolve con un riferimento tardivo.
    lateinit var client: HttpClient
    val manager = SessionManager(store, { client }, TEST_BASE_URL, pushToken)
    val engine = MockEngine(MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler { request -> with(backend) { handle(request) } }
    })
    client = HttpClientFactory.createHttpClient(AppConfig(TEST_BASE_URL, isDebug = false), manager, engine)
    return manager to client
}
