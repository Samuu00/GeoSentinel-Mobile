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

object HttpClientFactory {
    fun createHttpClient(
        config: AppConfig,
        sessionRepository: SessionRepository,
        engine: HttpClientEngine
    ): HttpClient {
        val apiUrl = Url(config.apiBaseUrl)

        // Il bearer va solo al backend: stesso schema, host e porta (niente downgrade a http, niente altri host)
        fun isApi(url: Url) = url.protocol == apiUrl.protocol && url.host == apiUrl.host && url.port == apiUrl.port

        return HttpClient(engine) {
            // L'API non fa redirect: seguirli potrebbe portare richieste (e header) altrove
            followRedirects = false

            // Content-Type lo mette il client generato solo sulle richieste con un body
            defaultRequest {
                url(config.apiBaseUrl)
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    }
                )
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 10_000L
                requestTimeoutMillis = 30_000L
                socketTimeoutMillis = 30_000L
            }

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

            // Il plugin Auth, su un 401 di una richiesta partita senza token, la rimanda CON il token. Quindi non
            // basta non mandarlo: per gli altri host (storage, URL firmati) e per login, registrazione e refresh
            // il plugin si spegne del tutto. Lì un 401 è definitivo (es. password sbagliata): rimandare la richiesta
            // raddoppierebbe i tentativi e un refresh consumerebbe il refresh token, che vale una volta sola.
            // Va installato prima di Auth, che legge il flag quando aggiunge l'header.
            install(createClientPlugin("AuthScope") {
                onRequest { request, _ ->
                    val url = request.url.build()
                    if (!isApi(url) || isPublicAuthPath(url.encodedPath)) request.attributes.put(AuthCircuitBreaker, Unit)
                }
            })

            install(Auth) {
                bearer {
                    loadTokens {
                        val tokens = sessionRepository.getTokens() ?: return@loadTokens null
                        BearerTokens(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken
                        )
                    }

                    // Il plugin fa un solo refresh alla volta: le richieste in parallelo aspettano questo
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

                    // Token anche sulle GET pubbliche: il rate limit conta per utente (le esclusioni sono sopra)
                    sendWithoutRequest { true }
                }
            }

            if (config.isDebug) {
                install(Logging) {
                    level = LogLevel.INFO
                    logger = object : Logger {
                        override fun log(message: String) {
                            KermitLogger.withTag("HTTP").d { message }
                        }
                    }
                    sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
                    // Niente posizione (lat/lon/bbox) né token push (/devices/{token}) né /auth nei log
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
     * Client per lo storage delle foto (M4.2): niente `Auth` (il bearer non deve arrivare a MinIO/S3), niente
     * redirect, niente log (il modulo contiene la firma) e tempi più lunghi per l'upload di qualche MB.
     */
    fun createStorageClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000L
            requestTimeoutMillis = 120_000L
            socketTimeoutMillis = 60_000L
        }
    }

    // Le uniche rotte /auth senza bearer: un 401 lì non è un token scaduto
    private val publicAuthPaths = listOf("/auth/login", "/auth/register", "/auth/refresh")

    private fun isPublicAuthPath(path: String) = publicAuthPaths.any { path.endsWith(it) }
}
