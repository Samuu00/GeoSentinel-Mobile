// Gestione della sessione dell'utente: login, registrazione, logout, rinnovo dei token e operazioni sull'account.
// È il punto più delicato del client per la concorrenza: il refresh token vale una sola volta e il server revoca
// la sessione se ne riceve uno già usato, quindi ogni scrittura dei token passa da un unico Mutex. Allo stesso tempo
// quel Mutex non deve mai avvolgere una richiesta autenticata, altrimenti il plugin Auth, che dentro la richiesta
// chiama il refresh, resterebbe bloccato per sempre (deadlock).
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
 * Il motivo per cui una sessione è terminata. Decide cosa si pulisce sul telefono (vedi `clearAfterSession`).
 */
enum class SessionEnd {
    /** L'utente è uscito: lascia il telefono, quindi i suoi dati in coda vengono eliminati. */
    LOGOUT,
    /** La sessione è scaduta o è stata revocata: l'utente non l'ha scelto e, rientrando, un SOS in coda deve ripartire. */
    EXPIRED,
    /** L'account è stato cancellato: si elimina tutto ciò che lo riguarda. */
    ACCOUNT_DELETED }

/** Lo stato della sessione, osservato dalla UI per decidere cosa mostrare. */
sealed interface SessionState {
    /** All'avvio, finché non si è letto l'archivio dei token. */
    data object Loading : SessionState
    /** Utente ospite, non autenticato. */
    data object Anonymous : SessionState
    /**
     * Utente autenticato. [profile] è null se la sessione è salvata ma il profilo non è ancora stato letto
     * (per esempio a un avvio senza rete).
     */
    data class LoggedIn(val profile: UserProfile?) : SessionState
    /** Il server ha rifiutato il refresh (sessione oltre la durata massima o revocata): si chiede di accedere di nuovo. */
    data object Expired : SessionState
}

/**
 * Il ruolo da usare per l'interfaccia. Serve solo a decidere cosa mostrare. Se l'utente è autenticato ma il profilo
 * non è ancora noto (per esempio offline) si mostra la vista da escursionista; se non è autenticato, quella da ospite.
 */
val SessionState.role: UserRole
    get() = if (this is SessionState.LoggedIn) profile?.role ?: UserRole.HIKER else UserRole.GUEST

/**
 * Il profilo dell'utente autenticato, letto da `GET /users/me`.
 *
 * @property hasPassword false per gli account aperti con Google, che non hanno una password: si può impostare
 *   con la procedura di reset tramite codice via email.
 * @property mfaEnabled vero se è attiva la verifica in due passaggi.
 * @property avatarMediaId l'id della foto profilo; null se l'utente non ne ha una.
 */
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val hasPassword: Boolean = true,
    val mfaEnabled: Boolean = false,
    val avatarMediaId: String? = null,
)

/** I dati del modulo di registrazione. */
class RegisterForm(val email: String, val password: String, val displayName: String, val birthDate: LocalDate) {
    // Il modulo contiene password ed email: il toString non mostra nessun campo, così non finiscono nei log.
    override fun toString() = "RegisterForm(…)"
}

/**
 * L'unico punto dell'app che possiede la sessione (singleton in Koin): espone lo stato, apre e chiude la sessione
 * e implementa [SessionRepository] per il plugin `Auth` del client HTTP.
 *
 * @param store l'archivio sicuro dei token.
 * @param httpClient fornisce il client condiviso; è una funzione perché il client, a sua volta, dipende da questa classe.
 * @param apiBaseUrl indirizzo del backend.
 */
class SessionManager(
    private val store: SecureTokenStore,
    httpClient: () -> HttpClient,
    apiBaseUrl: String,
    // Il token push registrato, da rimuovere dal server al logout. Lo fornisce PushManager; nei test non c'è.
    private val pushToken: suspend () -> String? = { null },
    // Chiamata a ogni fine sessione con l'id dell'utente uscito e il motivo, per la pulizia locale (token push,
    // outbox, cache). Viene eseguita con il mutex del refresh già preso: al suo interno non si possono fare
    // richieste autenticate.
    private val onSessionEnded: suspend (userId: String?, reason: SessionEnd) -> Unit = { _, _ -> },
) : SessionRepository {

    /** Client condiviso, risolto al primo uso per rompere la dipendenza circolare con Koin. */
    private val client by lazy(httpClient)
    private val authApi by lazy { AuthenticationApi(apiBaseUrl, client) }
    private val usersApi by lazy { UserProfileApi(apiBaseUrl, client) }
    private val devicesApi by lazy { DevicesApi(apiBaseUrl, client) }

    // Il refresh token vale una volta sola: questo Mutex garantisce un solo refresh alla volta in tutta l'app, non
    // solo dentro il client, e protegge ogni scrittura e cancellazione dei token salvati.
    private val refreshMutex = Mutex()
    // Rende sicuro chiamare restore() più volte, anche in parallelo.
    private val restoreMutex = Mutex()
    // Numero di cambi password in corso; si legge e si scrive solo con refreshMutex preso.
    private var passwordChanges = 0

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    /** Lo stato corrente della sessione. */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /**
     * Ripristina la sessione salvata all'avvio dell'app: se ci sono token l'utente risulta autenticato e si prova
     * a leggere il profilo, altrimenti è ospite. Una seconda chiamata (per esempio con l'Activity ricreata), anche
     * in parallelo, non fa nulla.
     */
    suspend fun restore() = restoreMutex.withLock {
        if (_state.value != SessionState.Loading) return@withLock
        if (readTokens() == null) {
            _state.value = SessionState.Anonymous
            return@withLock
        }
        _state.value = SessionState.LoggedIn(null)
        loadProfile()
    }

    /**
     * Accesso con email e password. Se l'account ha la verifica in due passaggi il server risponde 401
     * `mfa-required` senza creare la sessione: la UI chiede il codice e ripete la richiesta con [mfaCode], che può
     * essere un codice TOTP o un codice di recupero (li distingue il server).
     */
    suspend fun login(email: String, password: String, mfaCode: String? = null): Result<Unit> =
        apiCall { authApi.login(LoginRequest(email.trim(), password, mfaCode?.trim())) }.andThen { startSession(it) }

    /**
     * Registrazione di un nuovo account, seguita dall'apertura della sessione. L'età minima la controlla il server,
     * che risponde 422 `age-requirement-not-met`.
     */
    suspend fun register(form: RegisterForm): Result<Unit> =
        apiCall {
            authApi.register(RegisterRequest(form.birthDate, form.displayName.trim(), form.email.trim(), form.password))
        }.andThen { startSession(it) }

    /**
     * Accesso con Google, a partire dall'ID token ottenuto dall'SDK Google della piattaforma.
     *
     * Al primo accesso senza data di nascita il server risponde 422 `birth-date-required`: la UI la chiede e ripete
     * la richiesta con lo stesso token, che vale circa un'ora. Se l'email appartiene a un account esistente, il server
     * lo collega a Google, rimuove la password e chiude le altre sessioni. Anche qui si riceve 401 `mfa-required`
     * se l'account ha la verifica in due passaggi.
     */
    suspend fun loginWithGoogle(idToken: String, birthDate: LocalDate? = null, mfaCode: String? = null): Result<Unit> =
        apiCall { authApi.loginWithGoogle(GoogleLoginRequest(idToken, birthDate, mfaCode?.trim())) }.andThen { startSession(it) }

    /**
     * Chiede l'invio del codice per reimpostare la password. Il server risponde sempre 204, anche per un'email non
     * registrata, così la risposta non rivela se l'account esiste.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit> =
        apiCall { authApi.requestPasswordReset(PasswordResetRequest(email.trim())) }

    /**
     * Imposta la nuova password con il codice ricevuto via email. Il server chiude tutte le sessioni dell'utente: se
     * l'utente è quello autenticato su questo telefono (password impostata dal Profilo), la sessione si chiude anche
     * qui, come se fosse scaduta. L'outbox resta: rientrando, un SOS in coda riparte.
     */
    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): Result<Unit> =
        apiCall { authApi.confirmPasswordReset(PasswordResetConfirmRequest(code.trim(), email.trim(), newPassword)) }.map {
            val current = _state.value
            if (current is SessionState.LoggedIn && current.profile?.email.equals(email.trim(), ignoreCase = true)) {
                endSession(SessionState.Expired, SessionEnd.EXPIRED)
            }
        }

    /**
     * Esce dall'account, nell'ordine previsto dal contratto: rimozione del dispositivo dalle push, revoca del refresh
     * token sul server, pulizia locale. La pulizia locale avviene in ogni caso, anche senza rete o con la coroutine
     * annullata, grazie al blocco `finally`.
     */
    suspend fun logout() {
        try {
            pushToken()?.let { token -> apiCall { devicesApi.unregisterDevice(token) } }
            // Il refresh token si legge adesso e non prima, perché unregisterDevice può aver rinnovato i token. Se è
            // /auth/logout a ricevere 401, il plugin rinnova i token e reinvia lo stesso corpo con il token vecchio:
            // in quel caso si revoca anche quello nuovo, che altrimenti resterebbe valido sul server.
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

    // Operazioni su profilo e account: stanno qui perché modificano la sessione.

    /**
     * Cambia il nome visualizzato. Il profilo restituito dal server sostituisce quello in sessione, così il
     * Profilo mostra subito il nome nuovo.
     */
    suspend fun updateDisplayName(displayName: String): Result<Unit> =
        apiCall { usersApi.updateMyProfile(UpdateProfileRequest(displayName.trim())) }.map { profile ->
            if (_state.value is SessionState.LoggedIn) _state.value = SessionState.LoggedIn(profile.toUserProfile())
        }

    /**
     * Cambia la password. Il server ruota i token e invalida quelli degli altri dispositivi, che al primo 401
     * tornano al login. Qui si salvano i token nuovi e si svuota la cache del plugin Auth: se il plugin usasse ancora
     * il refresh token vecchio, già sostituito, il server vedrebbe un riuso e revocherebbe anche questa sessione.
     * Con la password attuale errata il server risponde 422 `business-rule`.
     *
     * L'operazione non è annullabile (`NonCancellable`): se l'utente lascia la schermata a metà, i token ruotati
     * vanno salvati comunque. Il mutex non può avvolgere la richiesta, perché il plugin, durante la richiesta, chiama
     * refreshTokens e si avrebbe un deadlock. Un refresh partito con il token vecchio mentre la richiesta è in corso
     * riceve 401, perché il server ha appena revocato quei token: finché il cambio è in corso quel 401 non chiude la
     * sessione (vedi [refreshTokens]) e i token nuovi arrivano con la risposta.
     *
     * @param mfaCode codice TOTP o di recupero, obbligatorio se la verifica in due passaggi è attiva: il server non
     *   apre la nuova sessione senza il secondo fattore. Se manca o è sbagliato la risposta è 422 `invalid-mfa-code`.
     */
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

    /**
     * Cancella l'account. È consentito solo agli escursionisti: il personale riceve 409 `account-managed` e la
     * sessione resta com'è. Il server annulla le prenotazioni future e l'eventuale SOS attivo, rimuove i dispositivi
     * e revoca tutti i token: all'app basta chiudere la sessione locale, anche se l'utente lascia la schermata a metà.
     */
    suspend fun deleteAccount(): Result<Unit> = withContext(NonCancellable) {
        apiCall { usersApi.deleteMyAccount() }.map { endSession(SessionState.Anonymous, SessionEnd.ACCOUNT_DELETED) }
    }

    /** Rilegge il profilo da `/users/me` dopo una modifica salvata dal server (verifica in due passaggi, foto). */
    suspend fun reloadProfile() = loadProfile()

    /** I token salvati, per il plugin Auth; null se mancano o non sono leggibili. */
    override suspend fun getTokens(): AuthTokens? = readTokens()

    /**
     * L'id dell'utente autenticato, disponibile anche prima di aver letto il profilo (avvio senza rete): in quel caso
     * si ricava dal claim `sub` dell'access token, che il backend valorizza con lo stesso id. È null da ospite o con
     * la sessione scaduta. Serve soprattutto all'SOS, che con poca rete non può aspettare `GET /users/me`.
     */
    suspend fun currentUserId(): String? = when (val current = _state.value) {
        is SessionState.LoggedIn -> current.profile?.id ?: readTokens()?.accessToken?.let(::jwtSubject)
        else -> null
    }

    /**
     * Rinnova i token (vedi [SessionRepository.refreshTokens]). Il mutex resta preso per tutta la richiesta: login,
     * logout, cambio password e cancellazione scrivono l'archivio prima o dopo, mai a metà di un refresh. Così una
     * sessione non viene riscritta dopo un logout e una sessione nuova non viene chiusa da un 401 arrivato in ritardo.
     */
    override suspend fun refreshTokens(refreshToken: String): AuthTokens? = refreshMutex.withLock {
        // Nessun token salvato: nel frattempo c'è stato un logout.
        val stored = readTokens() ?: return@withLock null
        // Token già rinnovati da un'altra chiamata (o da un login): non si fa la richiesta, perché il token vecchio
        // non vale più.
        if (stored.refreshToken != refreshToken) return@withLock stored

        // Con i tentativi automatici su 429 e 503 la richiesta potrebbe durare minuti, mentre logout e login aspettano
        // il mutex: oltre RefreshTimeout si rinuncia come per un errore di rete. Se il server aveva già ruotato i
        // token, il periodo di grazia sul refresh token permette di riprovare con lo stesso token.
        val result = withTimeoutOrNull(RefreshTimeout) { apiCall { authApi.refreshTokens(RefreshRequest(refreshToken)) } }
            ?: return@withLock null
        result.fold(
            // Se i token nuovi non si riescono a salvare (disco pieno, Keychain non disponibile) il refresh fallisce
            // e la sessione resta quella di prima.
            onSuccess = { response -> response.toAuthTokens().takeIf { saveTokens(it) } },
            onFailure = { failure ->
                // Solo un 401 chiude la sessione. Con errori di rete, 429 o 5xx i token restano e il plugin riproverà
                // (il backend accetta di nuovo lo stesso refresh token per 60 secondi se la risposta è andata persa).
                // La cache del client qui non si svuota: siamo dentro il suo refresh, che ricevendo null smette già
                // di usare i token. Durante un cambio password il 401 è dovuto alla revoca appena fatta dal server:
                // a mantenere la sessione ci pensa la risposta del cambio. Se il cambio fallisce, il refresh
                // successivo riceve di nuovo 401 e chiude la sessione.
                if ((failure as? ApiException)?.error is ApiError.Unauthorized && passwordChanges == 0) {
                    endSessionLocked(SessionState.Expired, clearClient = false, reason = SessionEnd.EXPIRED)
                }
                null
            },
        )
    }

    /**
     * Apre la sessione con i token appena ricevuti: li salva, passa allo stato autenticato e legge il profilo.
     * Il salvataggio non è annullabile. Restituisce un fallimento se i token non si possono salvare.
     */
    private suspend fun startSession(response: TokenResponse): Result<Unit> {
        val saved = withContext(NonCancellable) {
            refreshMutex.withLock {
                saveTokens(response.toAuthTokens()).also { saved ->
                    if (saved) {
                        // Il plugin Auth tiene i token in memoria: senza svuotarla, le richieste userebbero ancora
                        // quelli della sessione precedente.
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

    /**
     * Legge il profilo da `/users/me`, da cui arriva anche il ruolo per l'interfaccia (non dal JWT). Se la lettura
     * fallisce (per esempio per la rete) lo stato resta `LoggedIn(null)`.
     */
    private suspend fun loadProfile() {
        apiCall { usersApi.getMyProfile() }.onSuccess { profile ->
            if (_state.value is SessionState.LoggedIn) _state.value = SessionState.LoggedIn(profile.toUserProfile())
        }
    }

    /** Chiude la sessione prendendo il mutex; non annullabile. */
    private suspend fun endSession(next: SessionState, reason: SessionEnd) = withContext(NonCancellable) {
        refreshMutex.withLock { endSessionLocked(next, clearClient = true, reason = reason) }
    }

    /**
     * Chiude la sessione: cancella i token salvati, svuota se richiesto la cache del plugin Auth, passa allo stato
     * [next] e avvia la pulizia locale. Va chiamata con refreshMutex già preso.
     */
    private suspend fun endSessionLocked(next: SessionState, clearClient: Boolean, reason: SessionEnd) = withContext(NonCancellable) {
        val endedUserId = currentUserId()
        // Si fa un secondo tentativo in caso di errore momentaneo. Se fallisce di nuovo la sessione si chiude lo
        // stesso in memoria, ma i token restano su disco e al prossimo avvio la sessione si riaprirebbe. Limite noto:
        // il caso è raro (disco o Keychain guasti); se si presentasse davvero, la soluzione sarebbe un indicatore
        // "sessione chiusa" letto da restore().
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
            // Un errore nella pulizia locale non deve impedire di chiudere la sessione.
        }
    }

    /**
     * Legge i token dall'archivio. L'archivio può fallire (file danneggiato, disco pieno, Keychain non disponibile):
     * in quel caso si restituisce null, mai un'eccezione che farebbe chiudere l'app.
     */
    private suspend fun readTokens(): AuthTokens? = try {
        store.read()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Salva i token; restituisce false se l'archivio fallisce, senza lanciare eccezioni. */
    private suspend fun saveTokens(tokens: AuthTokens): Boolean = try {
        store.write(tokens)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** Svuota la cache dei token del plugin Auth, che alla richiesta successiva li rilegge dall'archivio. */
    private fun clearClientTokens() {
        client.authProvider<BearerAuthProvider>()?.clearToken()
    }
}

// Tempo massimo di un refresh: oltre questo limite si considera fallito come per un errore di rete, i token
// restano e si riproverà.
private val RefreshTimeout = 20.seconds

/** I token non si sono potuti salvare; per la UI è un errore generico. */
private class TokenStorageException : Exception("Sessione non salvata")

/** Un refresh rifiutato ha chiuso la sessione mentre la richiesta era in corso. */
private class SessionEndedException : Exception("Sessione chiusa")

/** Se il risultato è un successo esegue [next] sul valore, altrimenti propaga il fallimento. */
private inline fun <T> Result<T>.andThen(next: (T) -> Result<Unit>): Result<Unit> =
    fold(onSuccess = next, onFailure = { Result.failure(it) })

/** Converte la risposta del server nei token della sessione. */
private fun TokenResponse.toAuthTokens() = AuthTokens(accessToken, refreshToken, accessExpiresAt)

/** Converte il profilo restituito dal server nel profilo dell'app. */
private fun UserProfileResponse.toUserProfile() = UserProfile(
    id = id,
    email = email,
    displayName = displayName,
    role = when (role) {
        UserProfileResponse.Role.HIKER -> UserRole.HIKER
        UserProfileResponse.Role.PARK_MANAGER -> UserRole.PARK_MANAGER
        UserProfileResponse.Role.RESCUE_OPERATOR -> UserRole.RESCUE_OPERATOR
        UserProfileResponse.Role.ADMIN -> UserRole.ADMIN
        // Ruolo introdotto da una versione più recente del server: si usa la vista meno privilegiata tra quelle
        // di un utente autenticato.
        UserProfileResponse.Role.unknown_default_open_api -> UserRole.HIKER
    },
    hasPassword = hasPassword,
    mfaEnabled = mfaEnabled,
    avatarMediaId = avatarMediaId,
)
