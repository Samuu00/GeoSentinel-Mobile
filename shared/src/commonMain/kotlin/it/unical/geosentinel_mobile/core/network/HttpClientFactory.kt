// Costruzione dei client HTTP dell'app con Ktor.
// Qui si concentra tutta la configurazione di rete: JSON, timeout, tentativi automatici, autenticazione con
// bearer token e rinnovo della sessione, log in sviluppo. L'app usa un solo client autenticato verso il backend
// e un client separato, senza autenticazione, per caricare le foto sullo storage.
package it.unical.geosentinel_mobile.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.session.SessionRepository
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger as KermitLogger

/** Fabbrica dei client HTTP; i client creati qui sono registrati come singleton in Koin. */
object HttpClientFactory {
    /**
     * Crea il client autenticato verso il backend, condiviso da tutta l'app.
     *
     * @param config fornisce l'indirizzo del backend e dice se la build è di sviluppo.
     * @param sessionRepository fornisce e rinnova i token della sessione.
     * @param engine il motore HTTP della piattaforma (o un motore finto nei test).
     */
    fun createHttpClient(
        config: AppConfig,
        sessionRepository: SessionRepository,
        engine: HttpClientEngine
    ): HttpClient {
        val apiUrl = Url(config.apiBaseUrl)

        /**
         * Vero se [url] punta al backend: stesso schema, host e porta di `apiBaseUrl`. Il bearer token viene inviato
         * solo in questo caso, così non può finire su altri host né passare a http se il backend è in https.
         */
        fun isApi(url: Url) = url.protocol == apiUrl.protocol && url.host == apiUrl.host && url.port == apiUrl.port

        return HttpClient(engine) {
            // L'API non usa redirect: seguirli potrebbe portare richieste, e i relativi header, verso altri host.
            followRedirects = false

            // Ogni richiesta parte dall'indirizzo del backend. Content-Type non si imposta qui: lo aggiunge il
            // client generato, solo sulle richieste che hanno un corpo.
            defaultRequest {
                url(config.apiBaseUrl)
            }

            // JSON tollerante: i campi aggiunti dal server non rompono il client e i null non vengono inviati.
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    }
                )
            }

            // Tempi massimi in millisecondi: 10 s per connettersi, 30 s per la richiesta e tra due pacchetti.
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000L
                requestTimeoutMillis = 30_000L
                socketTimeoutMillis = 30_000L
            }

            // Fino a 3 nuovi tentativi, secondo le regole di GeoSentinelRetryPolicy. L'attesa è quella chiesta dal
            // server con Retry-After; senza indicazione cresce di un secondo a ogni tentativo.
            install(HttpRequestRetry) {
                maxRetries = 3
                retryIf { _, response ->
                    GeoSentinelRetryPolicy.shouldRetry(response)
                }
                retryOnExceptionIf { request, cause ->
                    GeoSentinelRetryPolicy.shouldRetryOnException(request, cause)
                }
                delayMillis { retry ->
                    GeoSentinelRetryPolicy.computeDelayMillis(response) ?: (retry * 1000L)
                }
            }

            // Il plugin Auth, se una richiesta partita senza token riceve un 401, la ripete aggiungendo il token.
            // Per questo non basta evitare di mandarlo: verso gli altri host (storage, URL firmati) e sulle rotte
            // pubbliche di autenticazione (login, anche con Google, registrazione, refresh, reset della password)
            // il plugin viene disattivato del tutto. Su quelle rotte un 401 è definitivo (per esempio una password
            // sbagliata): ripetere la richiesta raddoppierebbe i tentativi e, nel caso del refresh, consumerebbe il
            // refresh token, che vale una volta sola. Questo plugin va installato prima di Auth, che legge il flag
            // AuthCircuitBreaker quando aggiunge l'header.
            install(createClientPlugin("AuthScope") {
                onRequest { request, _ ->
                    val url = request.url.build()
                    if (!isApi(url) || isPublicAuthPath(url.encodedPath)) request.attributes.put(AuthCircuitBreaker, Unit)
                }
            })

            install(Auth) {
                bearer {
                    // Token letti dall'archivio sicuro; null se l'utente non è autenticato.
                    loadTokens {
                        val tokens = sessionRepository.getTokens() ?: return@loadTokens null
                        BearerTokens(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken
                        )
                    }

                    // Rinnovo dei token dopo un 401. Il plugin esegue un solo refresh alla volta e le richieste
                    // in parallelo aspettano il suo risultato; il repository aggiunge il proprio Mutex.
                    refreshTokens {
                        val currentRefreshToken = oldTokens?.refreshToken
                        if (currentRefreshToken.isNullOrBlank()) return@refreshTokens null

                        sessionRepository.refreshTokens(currentRefreshToken)?.let { newTokens ->
                            BearerTokens(
                                accessToken = newTokens.accessToken,
                                refreshToken = newTokens.refreshToken
                            )
                        }
                    }

                    // Il token si manda subito, anche sulle GET pubbliche, perché il backend conta il limite di
                    // richieste per utente. Le esclusioni sono quelle del plugin AuthScope qui sopra.
                    sendWithoutRequest { true }
                }
            }

            // Log delle richieste solo nelle build di sviluppo.
            if (config.isDebug) {
                install(Logging) {
                    level = LogLevel.INFO
                    logger = object : Logger {
                        override fun log(message: String) {
                            KermitLogger.withTag("HTTP").d { message }
                        }
                    }
                    // Il valore dell'header Authorization viene oscurato.
                    sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
                    // Non si registrano le richieste con dati personali: posizione (lat, lon, bbox), token push
                    // (nel percorso di /devices) e qualsiasi rotta /auth.
                    filter { request ->
                        val url = request.url.build()
                        val parameters = url.parameters
                        val path = url.encodedPath
                        listOf("lat", "lon", "bbox").none { parameters.contains(it) } &&
                            "/devices" !in path && "/auth/" !in path
                    }
                }
            }
        }
    }

    /**
     * Crea il client per caricare le foto sullo storage (MinIO o S3) tramite URL firmati. Non ha il plugin `Auth`,
     * perché il bearer token non deve mai arrivare allo storage; non segue redirect; non scrive log, perché la
     * richiesta contiene la firma; ha tempi più lunghi, adatti al caricamento di qualche MB.
     */
    fun createStorageClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000L
            requestTimeoutMillis = 120_000L
            socketTimeoutMillis = 60_000L
        }
    }

    // Rotte /auth che il backend accetta senza bearer (le stesse dell'elenco TOKENLESS_AUTH_PATHS del server):
    // un 401 su queste rotte non indica un token scaduto.
    private val publicAuthPaths = listOf(
        "/auth/login", "/auth/register", "/auth/refresh", "/auth/google",
        "/auth/password-reset/request", "/auth/password-reset/confirm",
    )

    /** Vero se [path] termina con una delle rotte pubbliche di autenticazione. */
    private fun isPublicAuthPath(path: String) = publicAuthPaths.any { path.endsWith(it) }
}
