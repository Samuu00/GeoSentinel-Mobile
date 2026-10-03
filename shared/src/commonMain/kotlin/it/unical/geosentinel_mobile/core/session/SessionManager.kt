package it.unical.geosentinel_mobile.core.session

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import it.unical.geosentinel_mobile.api.AuthenticationApi
import it.unical.geosentinel_mobile.api.DevicesApi
import it.unical.geosentinel_mobile.api.UserProfileApi
import it.unical.geosentinel_mobile.api.model.ChangePasswordRequest
import it.unical.geosentinel_mobile.api.model.LoginRequest
import it.unical.geosentinel_mobile.api.model.RefreshRequest
import it.unical.geosentinel_mobile.api.model.RegisterRequest
import it.unical.geosentinel_mobile.api.model.TokenResponse
import it.unical.geosentinel_mobile.api.model.UpdateProfileRequest
import it.unical.geosentinel_mobile.api.model.UserProfileResponse
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.apiCall
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

sealed interface SessionState {
    // all'avvio, finché non si è letto lo store
    data object Loading : SessionState
    data object Anonymous : SessionState
    // profile null: sessione salvata ma profilo non ancora letto (es. avvio senza rete)
    data class LoggedIn(val profile: UserProfile?) : SessionState
    // il refresh è stato rifiutato (sessione oltre i 30 giorni o revocata): si mostra "accedi di nuovo"
    data object Expired : SessionState
}

// Il ruolo serve solo a decidere cosa mostrare. Senza profilo (offline) si mostra la vista da escursionista.
val SessionState.role: UserRole
    get() = if (this is SessionState.LoggedIn) profile?.role ?: UserRole.HIKER else UserRole.GUEST

data class UserProfile(val id: String, val email: String, val displayName: String, val role: UserRole)

class RegisterForm(val email: String, val password: String, val displayName: String, val birthDate: LocalDate) {
    override fun toString() = "RegisterForm(…)" // contiene password ed email
}

/**
 * Unica proprietaria della sessione (singleton Koin). Implementa [SessionRepository] per il plugin `Auth` di
 * [HttpClient]: il client arriva come funzione perché a sua volta dipende da questa classe.
 */
class SessionManager(
    private val store: SecureTokenStore,
    httpClient: () -> HttpClient,
    apiBaseUrl: String,
    // M7: il token push registrato, da togliere al logout. Finché le push non ci sono, nessuno.
    private val pushToken: suspend () -> String? = { null },
) : SessionRepository {

    private val client by lazy(httpClient)
    private val authApi by lazy { AuthenticationApi(apiBaseUrl, client) }
    private val usersApi by lazy { UserProfileApi(apiBaseUrl, client) }
    private val devicesApi by lazy { DevicesApi(apiBaseUrl, client) }

    // Il refresh token vale una volta sola: un refresh alla volta in tutta l'app, non solo dentro il client
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    // All'avvio. Una seconda chiamata (es. Activity ricreata) non fa nulla.
    suspend fun restore() {
        if (_state.value != SessionState.Loading) return
        if (store.read() == null) {
            _state.value = SessionState.Anonymous
            return
        }
        _state.value = SessionState.LoggedIn(null)
        loadProfile()
    }

    suspend fun login(email: String, password: String): Result<Unit> =
        apiCall { authApi.login(LoginRequest(email.trim(), password)) }.map { startSession(it) }

    // La soglia di età la controlla il server (422 age-requirement-not-met)
    suspend fun register(form: RegisterForm): Result<Unit> =
        apiCall {
            authApi.register(RegisterRequest(form.birthDate, form.displayName.trim(), form.email.trim(), form.password))
        }.map { startSession(it) }

    // Nell'ordine del contratto: unregisterDevice, logout, pulizia locale. La pulizia locale avviene comunque,
    // anche senza rete o con la coroutine annullata.
    suspend fun logout() {
        try {
            pushToken()?.let { token -> apiCall { devicesApi.unregisterDevice(token) } }
            store.read()?.let { tokens -> apiCall { authApi.logout(RefreshRequest(tokens.refreshToken)) } }
        } finally {
            endSession(SessionState.Anonymous)
        }
    }

    // ---- profilo e account (M2.3): stanno qui perché cambiano la sessione ----

    // Il profilo restituito dal server prende il posto di quello in sessione: il Profilo mostra subito il nome nuovo
    suspend fun updateDisplayName(displayName: String): Result<Unit> =
        apiCall { usersApi.updateMyProfile(UpdateProfileRequest(displayName.trim())) }.map { profile ->
            if (_state.value is SessionState.LoggedIn) _state.value = SessionState.LoggedIn(profile.toUserProfile())
        }

    // Il server ruota i token e invalida quelli degli altri dispositivi (che al primo 401 tornano al login).
    // Qui si salvano i nuovi e si svuota la cache del plugin Auth: con il refresh token vecchio, già sostituito,
    // il server vedrebbe un riuso e revocherebbe anche questa sessione. Password attuale errata: 422 business-rule.
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        apiCall { usersApi.changeMyPassword(ChangePasswordRequest(currentPassword, newPassword)) }.map { response ->
            store.write(response.toAuthTokens())
            clearClientTokens()
        }

    // Solo per HIKER: il personale riceve 409 account-managed e la sessione resta com'è.
    // Il server annulla prenotazioni future e SOS attivo, toglie i dispositivi e revoca tutti i token:
    // qui basta chiudere la sessione locale.
    suspend fun deleteAccount(): Result<Unit> =
        apiCall { usersApi.deleteMyAccount() }.map { endSession(SessionState.Anonymous) }

    override suspend fun getTokens(): AuthTokens? = store.read()

    override suspend fun refreshTokens(refreshToken: String): AuthTokens? = refreshMutex.withLock {
        val stored = store.read() ?: return@withLock null // logout nel frattempo
        // già rinnovati da un'altra chiamata (o da un login): niente richiesta, il token vecchio non vale più
        if (stored.refreshToken != refreshToken) return@withLock stored

        apiCall { authApi.refreshTokens(RefreshRequest(refreshToken)) }.fold(
            onSuccess = { response -> response.toAuthTokens().also { store.write(it) } },
            onFailure = { failure ->
                // Solo un 401 chiude la sessione. Rete, 429 o 5xx: i token restano e il plugin riproverà
                // (il backend riaccetta il refresh token per 60 s se la risposta si è persa, ADR-0019).
                // Il client non si tocca qui: siamo dentro il suo refresh, che con null non usa più i token.
                if ((failure as? ApiException)?.error is ApiError.Unauthorized) {
                    endSession(SessionState.Expired, clearClient = false)
                }
                null
            },
        )
    }

    private suspend fun startSession(response: TokenResponse) {
        store.write(response.toAuthTokens())
        // Il plugin Auth tiene in memoria i token: senza questo partirebbero quelli della sessione precedente
        clearClientTokens()
        _state.value = SessionState.LoggedIn(null)
        loadProfile()
    }

    // Il ruolo per la UI arriva da /users/me, non dal JWT. Se fallisce (es. rete) resta LoggedIn(null).
    private suspend fun loadProfile() {
        apiCall { usersApi.getMyProfile() }.onSuccess { profile ->
            if (_state.value is SessionState.LoggedIn) _state.value = SessionState.LoggedIn(profile.toUserProfile())
        }
    }

    private suspend fun endSession(next: SessionState, clearClient: Boolean = true) = withContext(NonCancellable) {
        store.clear()
        if (clearClient) clearClientTokens()
        _state.value = next
    }

    private fun clearClientTokens() {
        client.authProvider<BearerAuthProvider>()?.clearToken()
    }
}

private fun TokenResponse.toAuthTokens() = AuthTokens(accessToken, refreshToken, accessExpiresAt)

private fun UserProfileResponse.toUserProfile() = UserProfile(
    id = id,
    email = email,
    displayName = displayName,
    role = when (role) {
        UserProfileResponse.Role.HIKER -> UserRole.HIKER
        UserProfileResponse.Role.PARK_MANAGER -> UserRole.PARK_MANAGER
        UserProfileResponse.Role.RESCUE_OPERATOR -> UserRole.RESCUE_OPERATOR
        UserProfileResponse.Role.ADMIN -> UserRole.ADMIN
        // ruolo nuovo del server: la vista meno privilegiata tra quelle di un utente loggato
        UserProfileResponse.Role.unknown_default_open_api -> UserRole.HIKER
    },
)
