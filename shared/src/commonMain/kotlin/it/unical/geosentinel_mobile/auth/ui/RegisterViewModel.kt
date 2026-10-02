package it.unical.geosentinel_mobile.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_field_birth_date
import geosentinel_mobile.shared.generated.resources.error_field_display_name
import geosentinel_mobile.shared.generated.resources.error_field_email
import geosentinel_mobile.shared.generated.resources.error_field_password_long
import geosentinel_mobile.shared.generated.resources.error_field_password_short
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.error_register_failed
import geosentinel_mobile.shared.generated.resources.error_validation
import it.unical.geosentinel_mobile.auth.data.PasswordProblem
import it.unical.geosentinel_mobile.auth.data.passwordProblem
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.session.RegisterForm
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class RegisterField { EMAIL, PASSWORD, DISPLAY_NAME, BIRTH_DATE }

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val birthDate: LocalDate? = null,
    val submitting: Boolean = false,
    val fieldErrors: Map<RegisterField, UiMessage> = emptyMap(),
    val error: UiMessage? = null,
    val registered: Boolean = false,
) {
    // niente dati personali nei log
    override fun toString() = "RegisterUiState(submitting=$submitting, fieldErrors=${fieldErrors.keys}, error=$error)"
}

class RegisterViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = edit(RegisterField.EMAIL) { copy(email = value) }
    fun onPasswordChange(value: String) = edit(RegisterField.PASSWORD) { copy(password = value) }
    fun onDisplayNameChange(value: String) = edit(RegisterField.DISPLAY_NAME) { copy(displayName = value) }
    fun onBirthDateChange(value: LocalDate) = edit(RegisterField.BIRTH_DATE) { copy(birthDate = value) }

    // Modificare un campo toglie il suo errore
    private fun edit(field: RegisterField, change: RegisterUiState.() -> RegisterUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        // Prima i controlli locali (gli stessi del server): niente richiesta se il form è già sbagliato
        val localErrors = localErrors(current)
        val birthDate = current.birthDate
        if (localErrors.isNotEmpty() || birthDate == null) {
            _state.update { it.copy(fieldErrors = localErrors) }
            return
        }
        val form = RegisterForm(current.email, current.password, current.displayName, birthDate)
        _state.update { it.copy(submitting = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val result = session.register(form)
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(submitting = false, password = "", registered = true) },
                    onFailure = { state.withServerError(it).copy(submitting = false) },
                )
            }
        }
    }

    private fun localErrors(state: RegisterUiState): Map<RegisterField, UiMessage> = buildMap {
        if (state.email.isBlank() || '@' !in state.email) put(RegisterField.EMAIL, UiMessage(Res.string.error_field_email))
        when (passwordProblem(state.password)) {
            PasswordProblem.TOO_SHORT -> put(RegisterField.PASSWORD, UiMessage(Res.string.error_field_password_short))
            PasswordProblem.TOO_LONG -> put(RegisterField.PASSWORD, UiMessage(Res.string.error_field_password_long))
            null -> Unit
        }
        if (state.displayName.trim().length !in 2..100) {
            put(RegisterField.DISPLAY_NAME, UiMessage(Res.string.error_field_display_name))
        }
        if (state.birthDate == null) put(RegisterField.BIRTH_DATE, UiMessage(Res.string.error_field_birth_date))
    }
}

// I campi segnalati dal server (errors[].field) arrivano al form con un messaggio italiano nostro: il testo del
// server è in inglese e non si mostra. Un campo che il form non conosce diventa un errore generico di validazione.
private fun RegisterUiState.withServerError(failure: Throwable): RegisterUiState =
    when (val error = (failure as? ApiException)?.error) {
        is ApiError.Validation -> {
            val fields = error.fieldErrors.keys.mapNotNull { serverFields[it] }.associateWith { fieldMessages.getValue(it) }
            val unknownField = fields.isEmpty() || fields.size < error.fieldErrors.size
            copy(fieldErrors = fields, error = if (unknownField) UiMessage(Res.string.error_validation) else null)
        }
        // business-rule in registrazione: di solito l'email è già registrata
        is ApiError.BusinessRule if error.slug == "business-rule" -> copy(error = UiMessage(Res.string.error_register_failed))
        null -> copy(error = UiMessage(Res.string.error_generic))
        else -> copy(error = error.uiMessage())
    }

private val serverFields = mapOf(
    "email" to RegisterField.EMAIL,
    "password" to RegisterField.PASSWORD,
    "displayName" to RegisterField.DISPLAY_NAME,
    "birthDate" to RegisterField.BIRTH_DATE,
)

private val fieldMessages = mapOf(
    RegisterField.EMAIL to UiMessage(Res.string.error_field_email),
    RegisterField.PASSWORD to UiMessage(Res.string.error_field_password_short),
    RegisterField.DISPLAY_NAME to UiMessage(Res.string.error_field_display_name),
    RegisterField.BIRTH_DATE to UiMessage(Res.string.error_field_birth_date),
)
