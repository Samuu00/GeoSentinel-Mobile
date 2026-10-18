package it.unical.geosentinel_mobile.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_field_email
import geosentinel_mobile.shared.generated.resources.error_field_password_long
import geosentinel_mobile.shared.generated.resources.error_field_password_short
import geosentinel_mobile.shared.generated.resources.error_field_reset_code
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.error_invalid_reset_code
import geosentinel_mobile.shared.generated.resources.error_validation
import it.unical.geosentinel_mobile.auth.data.PasswordProblem
import it.unical.geosentinel_mobile.auth.data.passwordProblem
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

// Password dimenticata (M7.2.2 del backend): email -> codice ricevuto e password nuova -> fatto, si torna al login
enum class ResetStep { EMAIL, CODE, DONE }

enum class ResetField { EMAIL, CODE, PASSWORD }

// Il codice è di 16 caratteri (XXXXXXXX-XXXXXXXX); il server ne accetta al massimo 32 con trattini e spazi
private const val RESET_CODE_MAX_CHARS = 32

data class PasswordResetUiState(
    val step: ResetStep = ResetStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val submitting: Boolean = false,
    val fieldErrors: Map<ResetField, UiMessage> = emptyMap(),
    val error: UiMessage? = null,
) {
    // niente email, codice e password nei log
    override fun toString() = "PasswordResetUiState(step=$step, submitting=$submitting, fieldErrors=${fieldErrors.keys}, error=$error)"
}

class PasswordResetViewModel(private val session: SessionManager, email: String) : ViewModel() {

    private val _state = MutableStateFlow(PasswordResetUiState(email = email))
    val state: StateFlow<PasswordResetUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = edit(ResetField.EMAIL) { copy(email = value) }
    fun onCodeChange(value: String) = edit(ResetField.CODE) { copy(code = value.take(RESET_CODE_MAX_CHARS)) }
    fun onPasswordChange(value: String) = edit(ResetField.PASSWORD) { copy(newPassword = value) }

    private fun edit(field: ResetField, change: PasswordResetUiState.() -> PasswordResetUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null) }

    // Anche "Invia di nuovo il codice": un codice nuovo invalida il precedente (3 richieste all'ora per email)
    fun requestCode() {
        val current = _state.value
        if (current.submitting) return
        if (current.email.isBlank() || '@' !in current.email) {
            _state.update { it.copy(fieldErrors = mapOf(ResetField.EMAIL to UiMessage(Res.string.error_field_email))) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = session.requestPasswordReset(current.email)
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(submitting = false, step = ResetStep.CODE, code = "") },
                    onFailure = { failure ->
                        if ((failure as? ApiException)?.error is ApiError.Validation) {
                            state.copy(submitting = false, step = ResetStep.EMAIL, fieldErrors = mapOf(ResetField.EMAIL to UiMessage(Res.string.error_field_email)))
                        } else {
                            state.copy(submitting = false, error = failure.toMessage())
                        }
                    },
                )
            }
        }
    }

    fun changeEmail() = _state.update { it.copy(step = ResetStep.EMAIL, code = "", newPassword = "", fieldErrors = emptyMap(), error = null) }

    fun confirm() {
        val current = _state.value
        if (current.submitting) return
        val localErrors = buildMap {
            if (current.code.isBlank()) put(ResetField.CODE, UiMessage(Res.string.error_field_reset_code))
            when (passwordProblem(current.newPassword)) {
                PasswordProblem.TOO_SHORT -> put(ResetField.PASSWORD, UiMessage(Res.string.error_field_password_short))
                PasswordProblem.TOO_LONG -> put(ResetField.PASSWORD, UiMessage(Res.string.error_field_password_long))
                null -> Unit
            }
        }
        if (localErrors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = localErrors) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = session.confirmPasswordReset(current.email, current.code, current.newPassword)
            _state.update { state ->
                result.fold(
                    // codice e password non restano in memoria
                    onSuccess = { state.copy(submitting = false, step = ResetStep.DONE, code = "", newPassword = "") },
                    onFailure = { state.withConfirmError(it).copy(submitting = false) },
                )
            }
        }
    }
}

private fun Throwable.toMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)

private fun PasswordResetUiState.withConfirmError(failure: Throwable): PasswordResetUiState =
    when (val error = (failure as? ApiException)?.error) {
        // sbagliato, scaduto, già usato o tentativi finiti: il server non dice quale
        is ApiError.BusinessRule if error.slug == "invalid-reset-code" ->
            copy(fieldErrors = mapOf(ResetField.CODE to UiMessage(Res.string.error_invalid_reset_code)))
        is ApiError.Validation -> {
            val fields = buildMap {
                if ("code" in error.fieldErrors) put(ResetField.CODE, UiMessage(Res.string.error_field_reset_code))
                if ("newPassword" in error.fieldErrors) {
                    val tooLong = passwordProblem(newPassword) == PasswordProblem.TOO_LONG
                    put(ResetField.PASSWORD, UiMessage(if (tooLong) Res.string.error_field_password_long else Res.string.error_field_password_short))
                }
            }
            copy(fieldErrors = fields, error = if (fields.isEmpty()) UiMessage(Res.string.error_validation) else null)
        }
        else -> copy(error = failure.toMessage())
    }
