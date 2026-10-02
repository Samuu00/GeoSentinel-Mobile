package it.unical.geosentinel_mobile.core.session

import kotlin.time.Instant

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
) {
    // Un data class stampa tutti i campi: i token non devono finire in un log o in un crash report
    override fun toString() = "AuthTokens(accessExpiresAt=$accessExpiresAt)"
}

interface SessionRepository {
    suspend fun getTokens(): AuthTokens?

    /**
     * Chiama `POST /auth/refresh` e salva la coppia nuova. Può usare il client condiviso: su `/auth/login`,
     * `/auth/register` e `/auth/refresh` il plugin `Auth` è spento (`HttpClientFactory`).
     * Il refresh token vale una volta sola e il server revoca la sessione se ne vede uno già usato, quindi:
     * - un `Mutex` attorno alla chiamata; dentro il lock si rileggono i token salvati, e se sono già diversi da
     *   [refreshToken] (li ha rinnovati qualcun altro) si restituiscono senza chiamare il server;
     * - una sola istanza (singleton in Koin) e un solo client autenticato per tutta l'app.
     * Restituisce null se non ci sono token nuovi. Svuota la sessione solo se il server risponde 401 (sessione
     * scaduta o revocata): un errore di rete non deve far uscire l'utente, in montagna capita spesso, e i token
     * salvati restano (il backend accetta di nuovo il refresh token per 60 s se la risposta si è persa, ADR-0019).
     */
    suspend fun refreshTokens(refreshToken: String): AuthTokens?
}
