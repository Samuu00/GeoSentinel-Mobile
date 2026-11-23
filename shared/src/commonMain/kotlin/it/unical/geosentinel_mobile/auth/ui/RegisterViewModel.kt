// Stato e logica della registrazione di un nuovo escursionista. I campi si controllano prima con le stesse regole
// del server, poi la richiesta passa da SessionManager, che a registrazione riuscita apre anche la sessione.
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

/** Campi del form di registrazione a cui si può attaccare un errore. */
enum class RegisterField { EMAIL, PASSWORD, DISPLAY_NAME, BIRTH_DATE }

/** Stato immutabile del form di registrazione; [registered] dice alla schermata di chiudersi. */
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
    // toString scritto a mano: email, password, nome e data di nascita non devono finire nei log.
    override fun toString() = "RegisterUiState(submitting=$submitting, fieldErrors=${fieldErrors.keys}, error=$error)"
}

/** ViewModel della registrazione: tiene lo stato del form, valida i campi e invia la richiesta. */
class RegisterViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    /** Aggiorna l'email. */
    fun onEmailChange(value: String) = edit(RegisterField.EMAIL) { copy(email = value) }
    /** Aggiorna la password. */
    fun onPasswordChange(value: String) = edit(RegisterField.PASSWORD) { copy(password = value) }
    /** Aggiorna il nome visualizzato. */
    fun onDisplayNameChange(value: String) = edit(RegisterField.DISPLAY_NAME) { copy(displayName = value) }
    /** Imposta la data di nascita scelta dal calendario. */
    fun onBirthDateChange(value: LocalDate) = edit(RegisterField.BIRTH_DATE) { copy(birthDate = value) }

    /** Applica [change] allo stato: modificare un campo toglie il suo errore e quello generale. */
    private fun edit(field: RegisterField, change: RegisterUiState.() -> RegisterUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null) }

    /** Valida il form e, se è corretto, invia la registrazione. */
    fun submit() {
        val current = _state.value
        if (current.submitting) return
        // Prima i controlli locali, gli stessi del server: se il form è già sbagliato non si fa la richiesta.
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

    /**
     * Controlli locali del form: email con la chiocciola, password secondo [passwordProblem], nome tra 2 e 100
     * caratteri (spazi esclusi ai bordi), data di nascita presente.
     *
     * @return gli errori per campo, vuoto se il form è valido.
     */
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

/**
 * Restituisce lo stato con l'errore del server applicato al form.
 *
 * Per un errore di validazione, ogni campo segnalato dal server riceve il messaggio italiano dell'app: il testo del
 * server è in inglese e non si mostra. Se il server segnala un campo che il form non conosce, si aggiunge anche un
 * errore generale di validazione.
 */
private fun RegisterUiState.withServerError(failure: Throwable): RegisterUiState =
    when (val error = (failure as? ApiException)?.error) {
        is ApiError.Validation -> {
            val fields = error.fieldErrors.keys.mapNotNull { serverFields[it] }.associateWith { fieldMessages.getValue(it) }
            val unknownField = fields.isEmpty() || fields.size < error.fieldErrors.size
            copy(fieldErrors = fields, error = if (unknownField) UiMessage(Res.string.error_validation) else null)
        }
        // In registrazione un errore business-rule vuol dire di solito che l'email è già registrata.
        is ApiError.BusinessRule if error.slug == "business-rule" -> copy(error = UiMessage(Res.string.error_register_failed))
        null -> copy(error = UiMessage(Res.string.error_generic))
        else -> copy(error = error.uiMessage())
    }

/** Corrispondenza tra i nomi dei campi nella risposta del server e i campi del form. */
private val serverFields = mapOf(
    "email" to RegisterField.EMAIL,
    "password" to RegisterField.PASSWORD,
    "displayName" to RegisterField.DISPLAY_NAME,
    "birthDate" to RegisterField.BIRTH_DATE,
)

/** Messaggio da mostrare per ogni campo rifiutato dal server. */
private val fieldMessages = mapOf(
    RegisterField.EMAIL to UiMessage(Res.string.error_field_email),
    RegisterField.PASSWORD to UiMessage(Res.string.error_field_password_short),
    RegisterField.DISPLAY_NAME to UiMessage(Res.string.error_field_display_name),
    RegisterField.BIRTH_DATE to UiMessage(Res.string.error_field_birth_date),
)
