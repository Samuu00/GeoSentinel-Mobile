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
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !submitting

    // niente email e password nei log
    override fun toString() = "LoginUiState(submitting=$submitting, error=$error, loggedIn=$loggedIn, askBirthDate=$askBirthDate)"
}

class LoginViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // L'ID token di un primo accesso in attesa della data di nascita. Fuori dallo stato della UI: non si salva né si stampa.
    private var pendingGoogleToken: String? = null

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = session.login(current.email, current.password)
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
            pendingGoogleToken = null
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(submitting = false, password = "", loggedIn = true) },
                    onFailure = { state.copy(submitting = false, error = it.toGoogleMessage()) },
                )
            }
        }
    }
}

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
