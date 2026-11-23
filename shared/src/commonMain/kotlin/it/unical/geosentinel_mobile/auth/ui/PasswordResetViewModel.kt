// Stato e logica della password dimenticata: l'utente chiede un codice via email, poi lo inserisce insieme alla
// nuova password. Le chiamate al server passano da SessionManager; qui si validano i campi e si traducono gli errori.
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

/**
 * Passi del flusso di reset: EMAIL (richiesta del codice), CODE (codice ricevuto e nuova password), DONE (password
 * cambiata, si torna al login).
 */
enum class ResetStep { EMAIL, CODE, DONE }

/** Campi del form di reset a cui si può attaccare un errore. */
enum class ResetField { EMAIL, CODE, PASSWORD }

// Il codice ha 16 caratteri (nel formato XXXXXXXX-XXXXXXXX), ma il server accetta fino a 32 caratteri contando
// trattini e spazi: il campo si tronca allo stesso limite.
private const val RESET_CODE_MAX_CHARS = 32

/** Stato immutabile del flusso di reset, con il passo corrente e gli errori per campo. */
data class PasswordResetUiState(
    val step: ResetStep = ResetStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val submitting: Boolean = false,
    val fieldErrors: Map<ResetField, UiMessage> = emptyMap(),
    val error: UiMessage? = null,
) {
    // toString scritto a mano: email, codice e password non devono finire nei log.
    override fun toString() = "PasswordResetUiState(step=$step, submitting=$submitting, fieldErrors=${fieldErrors.keys}, error=$error)"
}

/**
 * ViewModel della password dimenticata. [email] arriva dalla schermata di login, così il primo passo parte compilato.
 */
class PasswordResetViewModel(private val session: SessionManager, email: String) : ViewModel() {

    private val _state = MutableStateFlow(PasswordResetUiState(email = email))
    val state: StateFlow<PasswordResetUiState> = _state.asStateFlow()

    /** Aggiorna l'email. */
    fun onEmailChange(value: String) = edit(ResetField.EMAIL) { copy(email = value) }
    /** Aggiorna il codice, troncato a [RESET_CODE_MAX_CHARS]. */
    fun onCodeChange(value: String) = edit(ResetField.CODE) { copy(code = value.take(RESET_CODE_MAX_CHARS)) }
    /** Aggiorna la nuova password. */
    fun onPasswordChange(value: String) = edit(ResetField.PASSWORD) { copy(newPassword = value) }

    /** Applica [change] allo stato e toglie l'errore del campo modificato e quello generale. */
    private fun edit(field: ResetField, change: PasswordResetUiState.() -> PasswordResetUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null) }

    /**
     * Chiede al server di inviare un codice all'email e passa al passo del codice. Serve anche per "Invia di nuovo il
     * codice": un codice nuovo rende non valido il precedente. Il server limita le richieste per email (poche
     * all'ora), quindi un 429 arriva come errore generale con il tempo d'attesa.
     */
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

    /** Torna al primo passo per correggere l'email, scartando codice e password già scritti. */
    fun changeEmail() = _state.update { it.copy(step = ResetStep.EMAIL, code = "", newPassword = "", fieldErrors = emptyMap(), error = null) }

    /**
     * Controlla localmente codice e nuova password e, se vanno bene, invia la conferma del reset. A buon fine passa
     * al passo finale; altrimenti mostra l'errore sul campo giusto con [withConfirmError].
     */
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
                    // Codice e password si cancellano dallo stato appena il reset è riuscito.
                    onSuccess = { state.copy(submitting = false, step = ResetStep.DONE, code = "", newPassword = "") },
                    onFailure = { state.withConfirmError(it).copy(submitting = false) },
                )
            }
        }
    }
}

/** Messaggio comune per un errore del server, o quello generico se l'errore non viene dal server. */
private fun Throwable.toMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)

/**
 * Restituisce lo stato con l'errore della conferma attaccato al campo giusto: il codice se non è valido, la password
 * se il server la rifiuta (scegliendo il messaggio tra troppo corta e troppo lunga), un errore generale negli altri
 * casi.
 */
private fun PasswordResetUiState.withConfirmError(failure: Throwable): PasswordResetUiState =
    when (val error = (failure as? ApiException)?.error) {
        // Codice sbagliato, scaduto, già usato o con i tentativi finiti: il server non dice quale dei casi, di
        // proposito, e il messaggio resta unico.
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
