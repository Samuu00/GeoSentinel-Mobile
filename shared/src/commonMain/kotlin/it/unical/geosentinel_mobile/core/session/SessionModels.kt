// Modelli della sessione: la coppia di token e il contratto del repository che li rinnova.
// Sono usati dal client HTTP autenticato (plugin `Auth` di Ktor) e da `SessionManager`.
package it.unical.geosentinel_mobile.core.session

import kotlin.time.Instant

/**
 * La coppia di token restituita dal backend al login o al refresh.
 *
 * @property accessToken il JWT da mandare come bearer nelle richieste autenticate.
 * @property refreshToken il token da usare una sola volta per ottenere una nuova coppia.
 * @property accessExpiresAt l'istante in cui scade l'access token.
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
) {
    // Il toString generato di una data class stamperebbe tutti i campi: i token non devono mai finire
    // in un log o in un crash report, quindi qui compare solo la scadenza.
    override fun toString() = "AuthTokens(accessExpiresAt=$accessExpiresAt)"
}

/** Accesso ai token per il client autenticato: lettura e rinnovo. */
interface SessionRepository {
    /** Restituisce i token salvati, o null se l'utente non è autenticato. */
    suspend fun getTokens(): AuthTokens?

    /**
     * Chiama `POST /auth/refresh` e salva la nuova coppia di token. Può usare il client condiviso, perché su
     * `/auth/login`, `/auth/register` e `/auth/refresh` il plugin `Auth` è disattivato (vedi `HttpClientFactory`).
     *
     * Il refresh token vale una sola volta e il server revoca l'intera sessione se ne riceve uno già usato. Per questo:
     * - la chiamata è protetta da un `Mutex`; dentro il lock si rileggono i token salvati e, se sono già diversi da
     *   [refreshToken] (li ha rinnovati un'altra richiesta nel frattempo), si restituiscono senza chiamare il server;
     * - esiste una sola istanza (singleton in Koin) e un solo client autenticato in tutta l'app.
     *
     * Restituisce null se non è stato possibile ottenere token nuovi. La sessione viene svuotata solo se il server
     * risponde 401 (sessione scaduta o revocata): un errore di rete, frequente in montagna, non deve far uscire
     * l'utente. In quel caso i token salvati restano, e il backend accetta di nuovo lo stesso refresh token per
     * 60 secondi nel caso in cui la risposta sia andata persa.
     */
    suspend fun refreshTokens(refreshToken: String): AuthTokens?
}
