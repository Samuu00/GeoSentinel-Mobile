package it.unical.geosentinel_mobile.profile.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.TwoFactorAuthenticationApi
import it.unical.geosentinel_mobile.api.model.MfaCodeRequest
import it.unical.geosentinel_mobile.api.model.MfaEnableRequest
import it.unical.geosentinel_mobile.api.model.MfaSetupResponse
import it.unical.geosentinel_mobile.core.network.apiCall

/**
 * Verifica in due passaggi dell'utente loggato (M10.2, ADR-0025 del backend). Chiave, URI e codici di recupero
 * restano solo in memoria: niente cache, niente log. Codice sbagliato: 422 `invalid-mfa-code`; troppi: 429.
 */
class MfaRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val api by lazy { TwoFactorAuthenticationApi(apiBaseUrl, client) }

    // Ripetuta senza conferma sostituisce il segreto e manda un codice nuovo per email; 409 mfa-already-enabled se è
    // già attiva
    suspend fun startSetup(): Result<MfaSetupResponse> = apiCall { api.startMfaSetup() }

    // Il primo TOTP più il codice arrivato per email (ADR-0026 del backend, vale 15 minuti) la attivano; i 10 codici di
    // recupero arrivano una volta sola. Uno dei due sbagliato: 422 invalid-mfa-code, senza dire quale
    suspend fun enable(code: String, emailCode: String): Result<List<String>> =
        apiCall { api.enableMfa(MfaEnableRequest(code.trim(), emailCode.trim())) }.map { it.codes }

    // TOTP o codice di recupero
    suspend fun disable(code: String): Result<Unit> = apiCall { api.disableMfa(MfaCodeRequest(code.trim())) }

    // Solo TOTP: i codici vecchi smettono di valere
    suspend fun regenerateRecoveryCodes(code: String): Result<List<String>> =
        apiCall { api.regenerateRecoveryCodes(MfaCodeRequest(code.trim())) }.map { it.codes }
}
