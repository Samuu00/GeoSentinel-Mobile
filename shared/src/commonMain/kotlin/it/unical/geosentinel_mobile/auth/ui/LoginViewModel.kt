// Stato e logica del login: con email e password, con Google e, per gli account che l'hanno attivata, con il
// codice della verifica in due passaggi. Lo stesso ViewModel serve la schermata di login e il blocco
// "Continua con Google" del Profilo. La sessione vera e propria la gestisce SessionManager.
package it.unical.geosentinel_mobile.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.error_google_failed
import geosentinel_mobile.shared.generated.resources.error_google_rejected
import geosentinel_mobile.shared.generated.resources.error_google_staff
import geosentinel_mobile.shared.generated.resources.error_google_unavailable
import geosentinel_mobile.shared.generated.resources.error_login_failed
import geosentinel_mobile.shared.generated.resources.error_mfa_code_wrong
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Stato immutabile della schermata di login, osservato dalla UI.
 *
 * Il flag [loggedIn] dice alla schermata di chiudersi; [mfaStep] la fa passare al passo del codice.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: UiMessage? = null,
    val loggedIn: Boolean = false,
    /** Primo accesso con Google: il server vuole la data di nascita prima di creare l'account. */
    val askBirthDate: Boolean = false,
    /** Credenziali (o account Google) accettate, ma l'account ha la verifica in due passaggi: serve il codice. */
    val mfaStep: Boolean = false,
    val mfaCode: String = "",
    /** Vero se l'utente inserisce un codice di recupero invece del codice a 6 cifre (TOTP): stesso campo, testo libero. */
    val recoveryCode: Boolean = false,
    val mfaCodeError: UiMessage? = null,
) {
    /** Il pulsante "Accedi" è attivo solo con email e password compilate e nessun invio in corso. */
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !submitting
    /** Il codice si può inviare se ha 6 cifre (TOTP) o, per un codice di recupero, se non è vuoto. */
    val canSubmitCode: Boolean get() = (if (recoveryCode) mfaCode.isNotBlank() else mfaCode.length == 6) && !submitting

    // toString scritto a mano: email, password e codice non devono finire nei log.
    override fun toString() =
        "LoginUiState(submitting=$submitting, error=$error, loggedIn=$loggedIn, askBirthDate=$askBirthDate, mfaStep=$mfaStep)"
}

/**
 * ViewModel del login. Riceve gli eventi della UI, chiama [SessionManager] e traduce gli errori del server in
 * messaggi per l'utente. Tiene in memoria le credenziali solo per il tempo del passo del codice.
 */
class LoginViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // ID token di Google in attesa della data di nascita o del codice, ed email e password in attesa del codice.
    // Stanno fuori dallo stato della UI di proposito: così non vengono salvati né stampati nei log, e si
    // dimenticano appena il passo successivo finisce o viene annullato.
    private var pendingGoogleToken: String? = null
    private var pendingCredentials: Pair<String, String>? = null

    /** Aggiorna l'email e toglie l'errore mostrato. */
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    /** Aggiorna la password e toglie l'errore mostrato. */
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    /**
     * Invia email e password. Se il server risponde che serve il codice della verifica in due passaggi, le credenziali
     * restano in memoria e si passa al passo del codice; altrimenti si mostra l'esito.
     */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = session.login(current.email, current.password)
            if (result.isMfaRequired()) {
                pendingCredentials = current.email to current.password
                _state.update { it.copy(submitting = false, password = "", mfaStep = true) }
                return@launch
            }
            _state.update { state ->
                result.fold(
                    // La password si cancella dallo stato appena non serve più.
                    onSuccess = { state.copy(submitting = false, password = "", loggedIn = true) },
                    onFailure = { state.copy(submitting = false, error = it.toLoginMessage()) },
                )
            }
        }
    }

    /**
     * Segna l'inizio del login con Google. Il selettore di account lo apre la UI, perché su Android serve
     * l'Activity; il ViewModel riceve poi il risultato in [onGoogleResult].
     */
    fun onGoogleStarted() = _state.update { it.copy(submitting = true, error = null) }

    /** Gestisce l'esito del selettore di account: con un token prosegue col login, altrimenti chiude l'attesa. */
    fun onGoogleResult(result: GoogleSignInResult) {
        when (result) {
            is GoogleSignInResult.Token -> loginWithGoogle(result.idToken, birthDate = null)
            GoogleSignInResult.Cancelled -> _state.update { it.copy(submitting = false) }
            GoogleSignInResult.Unavailable -> _state.update { it.copy(submitting = false, error = UiMessage(Res.string.error_google_unavailable)) }
        }
    }

    /** Data di nascita scelta: ripete la stessa richiesta con lo stesso ID token, che resta valido per circa un'ora. */
    fun onGoogleBirthDate(date: LocalDate) {
        val token = pendingGoogleToken ?: return
        _state.update { it.copy(askBirthDate = false) }
        loginWithGoogle(token, date)
    }

    /** L'utente ha chiuso il calendario senza scegliere: il token in attesa si scarta. */
    fun onBirthDateDismissed() {
        pendingGoogleToken = null
        _state.update { it.copy(askBirthDate = false, submitting = false) }
    }

    /**
     * Invia l'ID token di Google al server, con la data di nascita se già nota. Due risposte non sono errori ma
     * richieste di un passo in più: `birth-date-required` (primo accesso, serve la data) e `mfa-required` (serve il
     * codice). In entrambi i casi il token si tiene in memoria per ripetere la richiesta.
     */
    private fun loginWithGoogle(idToken: String, birthDate: LocalDate?) {
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = session.loginWithGoogle(idToken, birthDate)
            val error = (result.exceptionOrNull() as? ApiException)?.error
            if (error is ApiError.BusinessRule && error.slug == "birth-date-required" && birthDate == null) {
                pendingGoogleToken = idToken
                _state.update { it.copy(submitting = false, askBirthDate = true) }
                return@launch
            }
            if (result.isMfaRequired()) {
                pendingGoogleToken = idToken
                _state.update { it.copy(submitting = false, mfaStep = true) }
                return@launch
            }
            pendingGoogleToken = null
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(submitting = false, password = "", loggedIn = true) },
                    onFailure = { state.copy(submitting = false, error = it.toGoogleMessage()) },
                )
            }
        }
    }

    // Passo del codice della verifica in due passaggi.

    /**
     * Aggiorna il codice. Per il TOTP tiene solo le cifre, al massimo 6. Per un codice di recupero accetta testo
     * libero fino a 32 caratteri (lo stesso limite della richiesta di login): il formato lo controlla il server.
     */
    fun onMfaCodeChange(value: String) = _state.update {
        it.copy(mfaCode = if (it.recoveryCode) value.take(32) else value.filter(Char::isDigit).take(6), mfaCodeError = null, error = null)
    }

    /** Passa dal codice a 6 cifre al codice di recupero e viceversa, svuotando il campo. */
    fun onToggleRecoveryCode() = _state.update { it.copy(recoveryCode = !it.recoveryCode, mfaCode = "", mfaCodeError = null, error = null) }

    /** Esce dal passo del codice: si torna alla password (o al pulsante di Google) e le credenziali in memoria si cancellano. */
    fun onMfaCancelled() {
        pendingCredentials = null
        pendingGoogleToken = null
        _state.update { it.copy(mfaStep = false, mfaCode = "", recoveryCode = false, mfaCodeError = null, error = null, submitting = false) }
    }

    /**
     * Ripete il login di prima (con email e password o con l'ID token di Google) aggiungendo il codice.
     *
     * Un codice sbagliato (`invalid-mfa-code`) diventa un errore sul campo. Un altro 401 vuol dire che le credenziali
     * non valgono più (password cambiata nel frattempo o ID token di Google scaduto): si annulla il passo e si
     * riparte dall'inizio con un messaggio.
     */
    fun submitCode() {
        val current = _state.value
        if (!current.canSubmitCode) return
        val credentials = pendingCredentials
        val googleToken = pendingGoogleToken
        val send: suspend () -> Result<Unit> = when {
            credentials != null -> { -> session.login(credentials.first, credentials.second, current.mfaCode) }
            googleToken != null -> { -> session.loginWithGoogle(googleToken, mfaCode = current.mfaCode) }
            else -> return
        }
        _state.update { it.copy(submitting = true, error = null, mfaCodeError = null) }
        viewModelScope.launch {
            val result = send()
            val error = (result.exceptionOrNull() as? ApiException)?.error
            when {
                result.isSuccess -> {
                    pendingCredentials = null
                    pendingGoogleToken = null
                    _state.update { it.copy(submitting = false, mfaStep = false, mfaCode = "", loggedIn = true) }
                }
                error is ApiError.Unauthorized && error.slug == "invalid-mfa-code" ->
                    _state.update { it.copy(submitting = false, mfaCodeError = UiMessage(Res.string.error_mfa_code_wrong)) }
                error is ApiError.Unauthorized -> {
                    val message = if (credentials != null) UiMessage(Res.string.error_login_failed) else UiMessage(Res.string.error_google_failed)
                    onMfaCancelled()
                    _state.update { it.copy(error = message) }
                }
                // Qui arriva anche il 429 del server dopo troppi codici sbagliati, con il tempo d'attesa. Il numero di
                // tentativi rimasti non si mostra, per non aiutare chi prova a indovinare.
                else -> _state.update { it.copy(submitting = false, error = error?.uiMessage() ?: UiMessage(Res.string.error_generic)) }
            }
        }
    }
}

/** Vero se il server ha risposto 401 con lo slug `mfa-required`, cioè credenziali giuste ma manca il codice. */
private fun Result<Unit>.isMfaRequired(): Boolean =
    ((exceptionOrNull() as? ApiException)?.error as? ApiError.Unauthorized)?.slug == "mfa-required"

/**
 * Messaggio per un errore del login con email. Qui un 401 vuol dire credenziali sbagliate e non sessione scaduta,
 * quindi ha un testo suo; gli altri errori (per esempio il 429 con i secondi d'attesa) usano i messaggi comuni.
 */
private fun Throwable.toLoginMessage(): UiMessage = when (val error = (this as? ApiException)?.error) {
    is ApiError.Unauthorized -> UiMessage(Res.string.error_login_failed)
    null -> UiMessage(Res.string.error_generic)
    else -> error.uiMessage()
}

/**
 * Messaggio per un errore del login con Google: 401 se il token è rifiutato, 409 `account-managed` se l'email
 * appartiene a un account del personale (che non può entrare con Google), 422 `business-rule` se l'email Google non è
 * verificata o l'account è già collegato a un altro profilo Google (il server non distingue i due casi). Il resto,
 * compresa l'età sotto la soglia, usa i messaggi comuni.
 */
private fun Throwable.toGoogleMessage(): UiMessage = when (val error = (this as? ApiException)?.error) {
    is ApiError.Unauthorized -> UiMessage(Res.string.error_google_failed)
    is ApiError.Conflict if error.slug == "account-managed" -> UiMessage(Res.string.error_google_staff)
    is ApiError.BusinessRule if error.slug == "business-rule" -> UiMessage(Res.string.error_google_rejected)
    null -> UiMessage(Res.string.error_generic)
    else -> error.uiMessage()
}
