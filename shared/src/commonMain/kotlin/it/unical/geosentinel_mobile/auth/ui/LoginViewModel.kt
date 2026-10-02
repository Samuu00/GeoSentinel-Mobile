package it.unical.geosentinel_mobile.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: UiMessage? = null,
    val loggedIn: Boolean = false,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !submitting

    // niente email e password nei log
    override fun toString() = "LoginUiState(submitting=$submitting, error=$error, loggedIn=$loggedIn)"
}

class LoginViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

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
}

// 401 sul login = credenziali sbagliate (non "sessione scaduta"); 429 con i secondi d'attesa
private fun Throwable.toLoginMessage(): UiMessage = when (val error = (this as? ApiException)?.error) {
    is ApiError.Unauthorized -> UiMessage(Res.string.error_login_failed)
    null -> UiMessage(Res.string.error_generic)
    else -> error.uiMessage()
}
