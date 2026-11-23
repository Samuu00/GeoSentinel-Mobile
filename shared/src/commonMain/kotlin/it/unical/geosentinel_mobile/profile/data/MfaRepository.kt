// Accesso alle API della verifica in due passaggi (TOTP) per l'utente loggato: attivazione, disattivazione e
// rigenerazione dei codici di recupero. Lo usa MfaViewModel, dalla schermata del Profilo.
package it.unical.geosentinel_mobile.profile.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.TwoFactorAuthenticationApi
import it.unical.geosentinel_mobile.api.model.MfaCodeRequest
import it.unical.geosentinel_mobile.api.model.MfaEnableRequest
import it.unical.geosentinel_mobile.api.model.MfaSetupResponse
import it.unical.geosentinel_mobile.core.network.apiCall

/**
 * Repository della verifica in due passaggi dell'utente loggato.
 *
 * Chiave segreta, URI `otpauth` e codici di recupero restano solo in memoria: non si salvano in cache e non si
 * stampano. Un codice sbagliato produce un 422 `invalid-mfa-code`, troppi tentativi un 429. Il client HTTP
 * autenticato arriva come funzione e si crea alla prima chiamata.
 */
class MfaRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val api by lazy { TwoFactorAuthenticationApi(apiBaseUrl, client) }

    /**
     * Avvia l'attivazione: il server genera la chiave segreta e invia un codice per email. Se si ripete prima di
     * confermare, la chiave precedente viene sostituita e arriva un nuovo codice. Risponde 409 `mfa-already-enabled`
     * se la verifica è già attiva.
     */
    suspend fun startSetup(): Result<MfaSetupResponse> = apiCall { api.startMfaSetup() }

    /**
     * Conferma l'attivazione con il primo codice dell'app di autenticazione e il codice ricevuto per email (valido
     * 15 minuti). Se uno dei due è sbagliato il server risponde 422 `invalid-mfa-code` senza dire quale.
     *
     * @return i codici di recupero, che il server restituisce solo questa volta.
     */
    suspend fun enable(code: String, emailCode: String): Result<List<String>> =
        apiCall { api.enableMfa(MfaEnableRequest(code.trim(), emailCode.trim())) }.map { it.codes }

    /** Disattiva la verifica in due passaggi; [code] può essere un codice dell'app o un codice di recupero. */
    suspend fun disable(code: String): Result<Unit> = apiCall { api.disableMfa(MfaCodeRequest(code.trim())) }

    /**
     * Genera nuovi codici di recupero confermando con un codice dell'app (non con un codice di recupero). I codici
     * precedenti smettono di valere.
     */
    suspend fun regenerateRecoveryCodes(code: String): Result<List<String>> =
        apiCall { api.regenerateRecoveryCodes(MfaCodeRequest(code.trim())) }.map { it.codes }
}
