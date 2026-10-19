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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: UiMessage? = null,
    val loggedIn: Boolean = false,
    // primo accesso con Google: il server vuole la data di nascita prima di creare l'account
    val askBirthDate: Boolean = false,
    // verifica in due passaggi (M10.1): password (o account Google) giusta, il server vuole il codice
    val mfaStep: Boolean = false,
    val mfaCode: String = "",
    // codice di recupero al posto del TOTP: stesso campo, testo libero
    val recoveryCode: Boolean = false,
    val mfaCodeError: UiMessage? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !submitting
    val canSubmitCode: Boolean get() = (if (recoveryCode) mfaCode.isNotBlank() else mfaCode.length == 6) && !submitting

    // niente email, password e codice nei log
    override fun toString() =
        "LoginUiState(submitting=$submitting, error=$error, loggedIn=$loggedIn, askBirthDate=$askBirthDate, mfaStep=$mfaStep)"
}

class LoginViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // L'ID token di un primo accesso in attesa della data di nascita, o di un accesso in attesa del codice. Fuori dallo
    // stato della UI: non si salva né si stampa. Lo stesso per email e password in attesa del codice.
    private var pendingGoogleToken: String? = null
    private var pendingCredentials: Pair<String, String>? = null

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

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
                    // la password non resta in memoria più del necessario
                    onSuccess = { state.copy(submitting = false, password = "", loggedIn = true) },
                    onFailure = { state.copy(submitting = false, error = it.toLoginMessage()) },
                )
            }
        }
    }

    // Il selettore di account lo apre la schermata (serve l'Activity): qui arriva il risultato
    fun onGoogleStarted() = _state.update { it.copy(submitting = true, error = null) }

    fun onGoogleResult(result: GoogleSignInResult) {
        when (result) {
            is GoogleSignInResult.Token -> loginWithGoogle(result.idToken, birthDate = null)
            GoogleSignInResult.Cancelled -> _state.update { it.copy(submitting = false) }
            GoogleSignInResult.Unavailable -> _state.update { it.copy(submitting = false, error = UiMessage(Res.string.error_google_unavailable)) }
        }
    }

    // Data scelta: stessa richiesta con lo stesso token, che vale circa un'ora
    fun onGoogleBirthDate(date: LocalDate) {
        val token = pendingGoogleToken ?: return
        _state.update { it.copy(askBirthDate = false) }
        loginWithGoogle(token, date)
    }

    fun onBirthDateDismissed() {
        pendingGoogleToken = null
        _state.update { it.copy(askBirthDate = false, submitting = false) }
    }

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

    // ---- codice della verifica in due passaggi ----

    // TOTP: solo 6 cifre. Recupero: il formato lo riconosce il server (max 32 caratteri, come LoginRequest)
    fun onMfaCodeChange(value: String) = _state.update {
        it.copy(mfaCode = if (it.recoveryCode) value.take(32) else value.filter(Char::isDigit).take(6), mfaCodeError = null, error = null)
    }

    fun onToggleRecoveryCode() = _state.update { it.copy(recoveryCode = !it.recoveryCode, mfaCode = "", mfaCodeError = null, error = null) }

    // Indietro: si torna alla password (o al pulsante di Google), e quella tenuta in memoria si dimentica
    fun onMfaCancelled() {
        pendingCredentials = null
        pendingGoogleToken = null
        _state.update { it.copy(mfaStep = false, mfaCode = "", recoveryCode = false, mfaCodeError = null, error = null, submitting = false) }
    }

    // Stessa richiesta di prima con il codice in più. Codice sbagliato: errore sul campo. 401 senza slug: password
    // cambiata nel frattempo o ID token di Google scaduto (dura circa un'ora), si riparte dall'inizio.
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
                // 429: i 5 codici sbagliati all'ora del backend, con l'attesa. Il numero di tentativi rimasti non si dice
                else -> _state.update { it.copy(submitting = false, error = error?.uiMessage() ?: UiMessage(Res.string.error_generic)) }
            }
        }
    }
}

private fun Result<Unit>.isMfaRequired(): Boolean =
    ((exceptionOrNull() as? ApiException)?.error as? ApiError.Unauthorized)?.slug == "mfa-required"

// 401 sul login = credenziali sbagliate (non "sessione scaduta"); 429 con i secondi d'attesa
private fun Throwable.toLoginMessage(): UiMessage = when (val error = (this as? ApiException)?.error) {
    is ApiError.Unauthorized -> UiMessage(Res.string.error_login_failed)
    null -> UiMessage(Res.string.error_generic)
    else -> error.uiMessage()
}

// 401 token rifiutato; 409 personale; 422 business-rule: email Google non verificata o account già collegato a un
// altro Google (il server non distingue). Età sotto la soglia e il resto con i messaggi comuni.
private fun Throwable.toGoogleMessage(): UiMessage = when (val error = (this as? ApiException)?.error) {
    is ApiError.Unauthorized -> UiMessage(Res.string.error_google_failed)
    is ApiError.Conflict if error.slug == "account-managed" -> UiMessage(Res.string.error_google_staff)
    is ApiError.BusinessRule if error.slug == "business-rule" -> UiMessage(Res.string.error_google_rejected)
    null -> UiMessage(Res.string.error_generic)
    else -> error.uiMessage()
}
