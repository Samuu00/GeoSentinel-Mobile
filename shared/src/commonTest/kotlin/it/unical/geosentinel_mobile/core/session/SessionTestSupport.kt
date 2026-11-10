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

const val TEST_BASE_URL = "http://api.test"
const val MFA_SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
const val MFA_SETUP_CODE = "123456"
// ADR-0026: codice mandato per email dallo setup, come lo mostra il server
const val MFA_EMAIL_CODE = "ABCDEFGH-23456789"

class FakeSecureTokenStore(var tokens: AuthTokens? = null) : SecureTokenStore {
    override suspend fun read() = tokens
    override suspend fun write(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}

/**
 * Backend finto per /auth e /users/me. Le password valide sono in [users] (email -> password); ogni login emette
 * una coppia nuova "a<n>"/"r<n>". Il profilo si legge dal bearer, come sul server.
 */
class FakeBackend {
    val users = mutableMapOf("hiker@test.dev" to "password-giusta-1", "rescuer@test.dev" to "password-giusta-2")
    val roles = mutableMapOf("hiker@test.dev" to "HIKER", "rescuer@test.dev" to "RESCUE_OPERATOR")
    val names = mutableMapOf<String, String>() // nome visualizzato, se cambiato (altrimenti "Nome")
    // Login Google (ADR-0024): un ID token "google:<email>" è valido, ogni altro dà 401. Gli account creati da Google
    // non hanno password. Reset (M7.2.2): l'ultimo codice mandato per email (solo agli account esistenti).
    val googleOnly = mutableSetOf<String>()
    val resetCodes = mutableMapOf<String, String>()
    // Verifica in due passaggi (M10): email -> il TOTP valido adesso (un codice vale una volta sola, come sul server) e
    // i codici di recupero. Il setup dà sempre MFA_SECRET; enable accetta MFA_SETUP_CODE con MFA_EMAIL_CODE.
    val mfaTotp = mutableMapOf<String, String>()
    val mfaRecovery = mutableMapOf<String, MutableSet<String>>()
    private val usedTotp = mutableSetOf<String>()
    // foto profilo (M11): email -> avatarMediaId
    val avatars = mutableMapOf<String, String>()
    var offline = false
    var refreshStatus = HttpStatusCode.OK
    var loginStatus: HttpStatusCode? = null // forza una risposta (es. 429)
    var registerProblem: Pair<HttpStatusCode, String>? = null // status + ProblemDetail
    val requests = mutableListOf<String>() // "METODO path bearer"
    // Rotte in più (catalogo, M3.2): null = non gestita, si risponde 404
    var extraRoutes: (suspend MockRequestHandleScope.(request: HttpRequestData, bearerOwner: String?) -> HttpResponseData?)? = null
    private val owners = mutableMapOf<String, String>() // access token -> email
    private val refreshOwners = mutableMapOf<String, String>()
    private var counter = 0

    fun issueFor(email: String): AuthTokens {
        counter++
        owners["a$counter"] = email
        refreshOwners["r$counter"] = email
        return AuthTokens("a$counter", "r$counter", kotlin.time.Instant.fromEpochSeconds(1_800_000_000))
    }

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
                delay(100) // così due refresh in parallelo si sovrappongono
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
                // collegato: la password non vale più
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
            // Come il backend: password attuale errata -> 422 business-rule; riuscito -> tutti i token dell'utente
            // revocati (anche degli altri dispositivi) e una coppia nuova per chi ha cambiato la password
            request.method == HttpMethod.Post && path == "/api/v1/users/me/password" -> {
                delay(200) // così un refresh può accavallarsi
                val email = owners[bearer?.removePrefix("Bearer ")] ?: return problem(HttpStatusCode.Unauthorized, "unauthorized")
                if (users[email] != field(body, "currentPassword")) return problem(HttpStatusCode.UnprocessableEntity, "business-rule")
                // ADR-0026: con la 2FA attiva serve anche il codice; assente o sbagliato -> 422 invalid-mfa-code
                checkMfa(email, body)?.let { return problem(HttpStatusCode.UnprocessableEntity, "invalid-mfa-code") }
                users[email] = field(body, "newPassword")
                revokeAll(email)
                tokens(issueFor(email))
            }
            // Solo HIKER (il personale riceve 409 account-managed); dopo, l'account non esiste più
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
            // Come il backend: solo ADMIN, 404 senza 2FA, 422 su sé stesso; chiude le sessioni dell'utente
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

    // null: si entra. Senza codice mfa-required; TOTP già usato o codice sconosciuto invalid-mfa-code
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

    private fun recoveryJson(email: String): String {
        val codes = (1..10).map { "REC0-$it" }
        mfaRecovery[email] = codes.toMutableSet()
        return """{"codes":[${codes.joinToString(",") { "\"$it\"" }}]}"""
    }

    private fun revokeAll(email: String) {
        owners.values.removeAll { it == email }
        refreshOwners.values.removeAll { it == email }
    }

    private fun profileJson(email: String) =
        """{"id":"id-$email","email":"$email","displayName":"${names[email] ?: "Nome"}","role":"${roles.getValue(email)}","hasPassword":${email !in googleOnly},""" +
            """"mfaEnabled":${email in mfaTotp},${avatars[email]?.let { "\"avatarMediaId\":\"$it\"," }.orEmpty()}"createdAt":"2026-10-02T10:00:00Z"}"""

    private fun MockRequestHandleScope.tokens(tokens: AuthTokens) = respond(tokenJson(tokens), HttpStatusCode.OK, json)

    private fun tokenJson(tokens: AuthTokens) =
        """{"accessToken":"${tokens.accessToken}","refreshToken":"${tokens.refreshToken}","accessExpiresAt":"2027-01-15T08:00:00Z","tokenType":"Bearer"}"""

    fun MockRequestHandleScope.problem(status: HttpStatusCode, slug: String, retryAfter: String? = null) = respond(
        """{"type":"https://geosentinel.dev/problems/$slug","title":"x","status":${status.value}}""",
        status,
        if (retryAfter == null) problemJson else headersOf(HttpHeaders.ContentType to listOf("application/problem+json"), HttpHeaders.RetryAfter to listOf(retryAfter)),
    )

    // Il JSON delle richieste è piatto: basta trovare il valore della chiave
    private fun field(body: String, name: String) = Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1).orEmpty()

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val problemJson = headersOf(HttpHeaders.ContentType, "application/problem+json")
}

// SessionManager e client collegati come in Koin, con il motore finto sul dispatcher del test (tempo virtuale)
fun TestScope.sessionWith(
    backend: FakeBackend,
    store: SecureTokenStore = FakeSecureTokenStore(),
    pushToken: suspend () -> String? = { null },
): Pair<SessionManager, HttpClient> {
    lateinit var client: HttpClient
    val manager = SessionManager(store, { client }, TEST_BASE_URL, pushToken)
    val engine = MockEngine(MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler { request -> with(backend) { handle(request) } }
    })
    client = HttpClientFactory.createHttpClient(AppConfig(TEST_BASE_URL, isDebug = false), manager, engine)
    return manager to client
}
