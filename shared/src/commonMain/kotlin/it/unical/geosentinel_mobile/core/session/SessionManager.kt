package it.unical.geosentinel_mobile.core.session

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import it.unical.geosentinel_mobile.api.AuthenticationApi
import it.unical.geosentinel_mobile.api.DevicesApi
import it.unical.geosentinel_mobile.api.UserProfileApi
import it.unical.geosentinel_mobile.api.model.ChangePasswordRequest
import it.unical.geosentinel_mobile.api.model.GoogleLoginRequest
import it.unical.geosentinel_mobile.api.model.LoginRequest
import it.unical.geosentinel_mobile.api.model.PasswordResetConfirmRequest
import it.unical.geosentinel_mobile.api.model.PasswordResetRequest
import it.unical.geosentinel_mobile.api.model.RefreshRequest
import it.unical.geosentinel_mobile.api.model.RegisterRequest
import it.unical.geosentinel_mobile.api.model.TokenResponse
import it.unical.geosentinel_mobile.api.model.UpdateProfileRequest
import it.unical.geosentinel_mobile.api.model.UserProfileResponse
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.apiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDate

/**
 * Perché una sessione è finita. Decide cosa si pulisce: con LOGOUT l'utente lascia il telefono (via i suoi dati in coda),
 * con EXPIRED no (non l'ha scelto: rientrando, un SOS in coda deve ripartire).
 */
enum class SessionEnd { LOGOUT, EXPIRED, ACCOUNT_DELETED }

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

// hasPassword false: account aperto con Google (ADR-0024), la password si imposta dal reset via codice.
// avatarMediaId: la foto profilo (M11), null senza foto
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val hasPassword: Boolean = true,
    val mfaEnabled: Boolean = false,
    val avatarMediaId: String? = null,
)

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
    // M7.1: il token push registrato, da togliere al logout (da PushManager; nei test nessuno)
    private val pushToken: suspend () -> String? = { null },
    // A ogni fine sessione, con l'id dell'utente uscito e il motivo: pulizia locale (token push, outbox, cache).
    // Gira con il mutex del refresh preso: niente richieste autenticate qui dentro.
    private val onSessionEnded: suspend (userId: String?, reason: SessionEnd) -> Unit = { _, _ -> },
) : SessionRepository {

    private val client by lazy(httpClient)
    private val authApi by lazy { AuthenticationApi(apiBaseUrl, client) }
    private val usersApi by lazy { UserProfileApi(apiBaseUrl, client) }
    private val devicesApi by lazy { DevicesApi(apiBaseUrl, client) }

    // Il refresh token vale una volta sola: un refresh alla volta in tutta l'app, non solo dentro il client
    private val refreshMutex = Mutex()
    private val restoreMutex = Mutex()
    // Cambi password in corso (letto e scritto solo con refreshMutex preso)
    private var passwordChanges = 0

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    // All'avvio. Una seconda chiamata (es. Activity ricreata), anche in parallelo, non fa nulla.
    suspend fun restore() = restoreMutex.withLock {
        if (_state.value != SessionState.Loading) return@withLock
        if (readTokens() == null) {
            _state.value = SessionState.Anonymous
            return@withLock
        }
        _state.value = SessionState.LoggedIn(null)
        loadProfile()
    }

    // Con la verifica in due passaggi attiva il server risponde 401 mfa-required senza creare nulla: la UI chiede il
    // codice e ripete la stessa richiesta con mfaCode (TOTP o codice di recupero, il server li distingue)
    suspend fun login(email: String, password: String, mfaCode: String? = null): Result<Unit> =
        apiCall { authApi.login(LoginRequest(email.trim(), password, mfaCode?.trim())) }.andThen { startSession(it) }

    // La soglia di età la controlla il server (422 age-requirement-not-met)
    suspend fun register(form: RegisterForm): Result<Unit> =
        apiCall {
            authApi.register(RegisterRequest(form.birthDate, form.displayName.trim(), form.email.trim(), form.password))
        }.andThen { startSession(it) }

    // ID token dal SDK Google della piattaforma. Primo accesso senza data di nascita: 422 birth-date-required, la UI
    // la chiede e ripete con lo stesso token (vale circa un'ora). Un account esistente collegato a Google perde la
    // password e le altre sessioni (lo fa il server).
    // Anche qui 401 mfa-required se l'account ha la verifica in due passaggi.
    suspend fun loginWithGoogle(idToken: String, birthDate: LocalDate? = null, mfaCode: String? = null): Result<Unit> =
        apiCall { authApi.loginWithGoogle(GoogleLoginRequest(idToken, birthDate, mfaCode?.trim())) }.andThen { startSession(it) }

    // Sempre 204, anche per un'email non registrata: la risposta non dice se l'account esiste
    suspend fun requestPasswordReset(email: String): Result<Unit> =
        apiCall { authApi.requestPasswordReset(PasswordResetRequest(email.trim())) }

    // Il server chiude tutte le sessioni dell'utente: se è quello di questo telefono (password impostata dal Profilo),
    // si chiude anche qui, come una sessione scaduta (l'outbox resta: rientrando, un SOS in coda riparte)
    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): Result<Unit> =
        apiCall { authApi.confirmPasswordReset(PasswordResetConfirmRequest(code.trim(), email.trim(), newPassword)) }.map {
            val current = _state.value
            if (current is SessionState.LoggedIn && current.profile?.email.equals(email.trim(), ignoreCase = true)) {
                endSession(SessionState.Expired, SessionEnd.EXPIRED)
            }
        }

    // Nell'ordine del contratto: unregisterDevice, logout, pulizia locale. La pulizia locale avviene comunque,
    // anche senza rete o con la coroutine annullata.
    suspend fun logout() {
        try {
            pushToken()?.let { token -> apiCall { devicesApi.unregisterDevice(token) } }
            // Letto adesso, non prima: unregisterDevice può aver rinnovato i token. Se è /auth/logout a prendere 401,
            // il plugin rinnova e reinvia lo stesso corpo con il token vecchio: allora si revoca anche quello nuovo.
            val sent = readTokens()?.refreshToken
            if (sent != null) {
                apiCall { authApi.logout(RefreshRequest(sent)) }
                readTokens()?.refreshToken?.takeIf { it != sent }?.let { renewed ->
                    apiCall { authApi.logout(RefreshRequest(renewed)) }
                }
            }
        } finally {
            endSession(SessionState.Anonymous, SessionEnd.LOGOUT)
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
    // Non annullabile: se l'utente lascia la schermata a metà, i token ruotati vanno salvati lo stesso.
    // Il mutex non può avvolgere la richiesta (il plugin vi chiama refreshTokens: deadlock). Un refresh partito con il
    // token vecchio mentre la richiesta è in volo prende 401 perché il server li ha appena revocati: finché il cambio è
    // in corso quel 401 non chiude la sessione (vedi refreshTokens), e i token nuovi arrivano con la risposta.
    // mfaCode: TOTP o codice di recupero, obbligatorio se la verifica in due passaggi è attiva (ADR-0026 del backend:
    // la sessione nuova non nasce senza il secondo fattore); senza o sbagliato -> 422 invalid-mfa-code
    suspend fun changePassword(currentPassword: String, newPassword: String, mfaCode: String? = null): Result<Unit> = withContext(NonCancellable) {
        refreshMutex.withLock { passwordChanges++ }
        try {
            apiCall { usersApi.changeMyPassword(ChangePasswordRequest(currentPassword, newPassword, mfaCode?.trim()?.ifEmpty { null })) }.andThen { response ->
                refreshMutex.withLock {
                    when {
                        _state.value !is SessionState.LoggedIn -> Result.failure(SessionEndedException())
                        !saveTokens(response.toAuthTokens()) -> Result.failure(TokenStorageException())
                        else -> Result.success(clearClientTokens())
                    }
                }
            }
        } finally {
            refreshMutex.withLock { passwordChanges-- }
        }
    }

    // Solo per HIKER: il personale riceve 409 account-managed e la sessione resta com'è.
    // Il server annulla prenotazioni future e SOS attivo, toglie i dispositivi e revoca tutti i token:
    // qui basta chiudere la sessione locale, anche se l'utente lascia la schermata a metà.
    suspend fun deleteAccount(): Result<Unit> = withContext(NonCancellable) {
        apiCall { usersApi.deleteMyAccount() }.map { endSession(SessionState.Anonymous, SessionEnd.ACCOUNT_DELETED) }
    }

    // Dopo un cambio che il server tiene nel profilo (verifica in due passaggi, foto): /users/me di nuovo
    suspend fun reloadProfile() = loadProfile()

    override suspend fun getTokens(): AuthTokens? = readTokens()

    /**
     * L'id dell'utente loggato, anche prima di aver letto il profilo (avvio senza rete): allora dal `sub` del token,
     * che il backend riempie con lo stesso id. Null da ospite o con la sessione scaduta. Serve all'SOS (M6.1), che
     * con poca rete non può aspettare `GET /users/me`.
     */
    suspend fun currentUserId(): String? = when (val current = _state.value) {
        is SessionState.LoggedIn -> current.profile?.id ?: readTokens()?.accessToken?.let(::jwtSubject)
        else -> null
    }

    // Tiene il mutex per tutta la richiesta: login, logout, cambio password e cancellazione scrivono lo store prima o
    // dopo, mai a metà di un refresh (niente sessione riscritta dopo un logout, né una nuova chiusa da un 401 vecchio).
    override suspend fun refreshTokens(refreshToken: String): AuthTokens? = refreshMutex.withLock {
        val stored = readTokens() ?: return@withLock null // logout nel frattempo
        // già rinnovati da un'altra chiamata (o da un login): niente richiesta, il token vecchio non vale più
        if (stored.refreshToken != refreshToken) return@withLock stored

        // Con i retry su 429/503 la richiesta può durare minuti, e intanto logout e login aspettano il mutex: oltre
        // RefreshTimeout si lascia perdere come per un errore di rete (se il server aveva già ruotato, vale ADR-0019)
        val result = withTimeoutOrNull(RefreshTimeout) { apiCall { authApi.refreshTokens(RefreshRequest(refreshToken)) } }
            ?: return@withLock null
        result.fold(
            // non salvati (disco pieno, Keychain): la richiesta fallisce, la sessione resta quella di prima
            onSuccess = { response -> response.toAuthTokens().takeIf { saveTokens(it) } },
            onFailure = { failure ->
                // Solo un 401 chiude la sessione. Rete, 429 o 5xx: i token restano e il plugin riproverà
                // (il backend riaccetta il refresh token per 60 s se la risposta si è persa, ADR-0019).
                // Il client non si tocca qui: siamo dentro il suo refresh, che con null non usa più i token.
                // Durante un cambio password il 401 viene dalla revoca appena fatta dal server: la sessione la tiene in
                // piedi la risposta del cambio. Se il cambio fallisce, il refresh successivo riprende 401 e la chiude.
                if ((failure as? ApiException)?.error is ApiError.Unauthorized && passwordChanges == 0) {
                    endSessionLocked(SessionState.Expired, clearClient = false, reason = SessionEnd.EXPIRED)
                }
                null
            },
        )
    }

    private suspend fun startSession(response: TokenResponse): Result<Unit> {
        val saved = withContext(NonCancellable) {
            refreshMutex.withLock {
                saveTokens(response.toAuthTokens()).also { saved ->
                    if (saved) {
                        // Il plugin Auth tiene in memoria i token: senza questo partirebbero quelli della sessione precedente
                        clearClientTokens()
                        _state.value = SessionState.LoggedIn(null)
                    }
                }
            }
        }
        if (!saved) return Result.failure(TokenStorageException())
        loadProfile()
        return Result.success(Unit)
    }

    // Il ruolo per la UI arriva da /users/me, non dal JWT. Se fallisce (es. rete) resta LoggedIn(null).
    private suspend fun loadProfile() {
        apiCall { usersApi.getMyProfile() }.onSuccess { profile ->
            if (_state.value is SessionState.LoggedIn) _state.value = SessionState.LoggedIn(profile.toUserProfile())
        }
    }

    private suspend fun endSession(next: SessionState, reason: SessionEnd) = withContext(NonCancellable) {
        refreshMutex.withLock { endSessionLocked(next, clearClient = true, reason = reason) }
    }

    // Da chiamare con refreshMutex già preso
    private suspend fun endSessionLocked(next: SessionState, clearClient: Boolean, reason: SessionEnd) = withContext(NonCancellable) {
        val endedUserId = currentUserId()
        // Un secondo tentativo per un errore momentaneo. Se fallisce ancora la sessione si chiude lo stesso in memoria,
        // ma i token restano su disco e al prossimo avvio si riaprirebbe: ponytail: raro (disco o Keychain guasti),
        // un flag "sessione chiusa" letto da restore() se mai succede davvero.
        for (attempt in 1..2) {
            try {
                store.clear()
                break
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        if (clearClient) clearClientTokens()
        _state.value = next
        try {
            onSessionEnded(endedUserId, reason)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // la pulizia locale non deve impedire di chiudere la sessione
        }
    }

    // Lo store può fallire (file danneggiato, disco pieno, Keychain non disponibile): un errore, mai un crash
    private suspend fun readTokens(): AuthTokens? = try {
        store.read()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun saveTokens(tokens: AuthTokens): Boolean = try {
        store.write(tokens)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private fun clearClientTokens() {
        client.authProvider<BearerAuthProvider>()?.clearToken()
    }
}

// Oltre questo tempo un refresh si considera fallito per rete: i token restano e si riproverà
private val RefreshTimeout = 20.seconds

// I token non si sono potuti salvare: per la UI è un errore generico
private class TokenStorageException : Exception("Sessione non salvata")

// Un refresh rifiutato ha chiuso la sessione mentre la richiesta era in corso
private class SessionEndedException : Exception("Sessione chiusa")

private inline fun <T> Result<T>.andThen(next: (T) -> Result<Unit>): Result<Unit> =
    fold(onSuccess = next, onFailure = { Result.failure(it) })

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
    hasPassword = hasPassword,
    mfaEnabled = mfaEnabled,
    avatarMediaId = avatarMediaId,
)
