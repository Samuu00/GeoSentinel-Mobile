// Stato e logica della schermata della verifica in due passaggi nel Profilo: attivazione con chiave e codici,
// visualizzazione dei codici di recupero, disattivazione e rigenerazione dei codici. Nulla viene scritto su disco.
package it.unical.geosentinel_mobile.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.error_mfa_code_wrong
import geosentinel_mobile.shared.generated.resources.error_mfa_setup_codes_wrong
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.safely
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.profile.data.MfaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Passi della schermata. OVERVIEW: stato attuale e azioni disponibili. SETUP: chiave da inserire nell'app, codice
 * arrivato per email e primo codice. CODES: i codici di recupero, mostrati una volta sola. DISABLE e REGENERATE:
 * richiedono un codice per confermare l'operazione.
 */
enum class MfaStep { OVERVIEW, SETUP, CODES, DISABLE, REGENERATE }

/** Stato immutabile della schermata della verifica in due passaggi. */
data class MfaUiState(
    val enabled: Boolean,
    val step: MfaStep = MfaStep.OVERVIEW,
    val secret: String? = null,
    val otpauthUri: String? = null,
    val code: String = "",
    val emailCode: String = "",
    val recoveryCodes: List<String> = emptyList(),
    val savedConfirmed: Boolean = false,
    val busy: Boolean = false,
    val codeError: UiMessage? = null,
    val error: UiMessage? = null,
) {
    /**
     * Vero se il codice inserito si può inviare. DISABLE accetta anche un codice di recupero (testo libero); gli
     * altri passi solo il codice a 6 cifre. SETUP vuole anche il codice dell'email, che conta 16 lettere o cifre
     * esclusi trattini e spazi.
     */
    val canSubmitCode: Boolean get() = !busy && when (step) {
        MfaStep.DISABLE -> code.isNotBlank()
        MfaStep.SETUP -> code.length == 6 && emailCode.count(Char::isLetterOrDigit) == EMAIL_CODE_LENGTH
        else -> code.length == 6
    }

    // toString scritto a mano: chiave, URI, codici e codici di recupero non devono finire nei log.
    override fun toString() = "MfaUiState(enabled=$enabled, step=$step, busy=$busy, error=$error, codeError=$codeError)"
}

/** Numero di lettere e cifre del codice inviato per email durante l'attivazione. */
private const val EMAIL_CODE_LENGTH = 16

/**
 * ViewModel della verifica in due passaggi. Lo stato iniziale (attiva o no) viene dal profilo della sessione; dopo
 * ogni attivazione o disattivazione il profilo si ricarica, così il resto dell'app vede il valore aggiornato.
 */
class MfaViewModel(private val mfa: MfaRepository, private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(
        MfaUiState(enabled = (session.state.value as? SessionState.LoggedIn)?.profile?.mfaEnabled == true),
    )
    val state: StateFlow<MfaUiState> = _state.asStateFlow()

    /** Aggiorna il codice: testo libero fino a 32 caratteri in DISABLE, altrimenti solo 6 cifre. */
    fun onCodeChange(value: String) = _state.update {
        val code = if (it.step == MfaStep.DISABLE) value.take(32) else value.filter(Char::isDigit).take(6)
        it.copy(code = code, codeError = null, error = null)
    }

    /**
     * Aggiorna il codice ricevuto per email. Si tengono lettere, cifre, trattini e spazi (che il server ignora), in
     * maiuscolo e al massimo 24 caratteri, così l'utente può copiarlo com'è scritto nell'email.
     */
    fun onEmailCodeChange(value: String) = _state.update {
        it.copy(emailCode = value.filter { c -> c.isLetterOrDigit() || c == '-' || c == ' ' }.uppercase().take(24), codeError = null, error = null)
    }

    /** Segna se l'utente ha confermato di aver salvato i codici di recupero. */
    fun onSavedChange(saved: Boolean) = _state.update { it.copy(savedConfirmed = saved) }

    /** Chiede al server una nuova chiave e passa al passo SETUP. */
    fun startSetup() = request({ mfa.startSetup() }) { setup ->
        _state.update { it.copy(step = MfaStep.SETUP, secret = setup.secret, otpauthUri = setup.otpauthUri, code = "", emailCode = "") }
    }

    /** Apre il passo di conferma della disattivazione. */
    fun openDisable() = _state.update { it.copy(step = MfaStep.DISABLE, code = "", codeError = null, error = null) }

    /** Apre il passo di conferma della rigenerazione dei codici di recupero. */
    fun openRegenerate() = _state.update { it.copy(step = MfaStep.REGENERATE, code = "", codeError = null, error = null) }

    /**
     * Invia il codice del passo corrente: in SETUP attiva la verifica e mostra i codici di recupero, in REGENERATE
     * mostra i nuovi codici, in DISABLE la disattiva. Negli altri passi non fa nulla.
     */
    fun submitCode() {
        val current = _state.value
        if (!current.canSubmitCode) return
        when (current.step) {
            MfaStep.SETUP -> request({ mfa.enable(current.code, current.emailCode) }) { codes ->
                _state.update {
                    it.copy(enabled = true, step = MfaStep.CODES, recoveryCodes = codes, secret = null, otpauthUri = null, code = "", emailCode = "")
                }
                session.reloadProfile()
            }
            MfaStep.REGENERATE -> request({ mfa.regenerateRecoveryCodes(current.code) }) { codes ->
                _state.update { it.copy(step = MfaStep.CODES, recoveryCodes = codes, code = "") }
            }
            MfaStep.DISABLE -> request({ mfa.disable(current.code) }) {
                _state.update { it.copy(enabled = false, step = MfaStep.OVERVIEW, code = "") }
                session.reloadProfile()
            }
            MfaStep.OVERVIEW, MfaStep.CODES -> Unit
        }
    }

    /**
     * Torna alla panoramica e dimentica chiave, codici e codici di recupero. Dal passo CODES la schermata chiede prima
     * una conferma, perché i codici non si potranno più rivedere.
     */
    fun back() = _state.update {
        it.copy(
            step = MfaStep.OVERVIEW, secret = null, otpauthUri = null, code = "", emailCode = "", recoveryCodes = emptyList(),
            savedConfirmed = false, codeError = null, error = null,
        )
    }

    /** Nessuna app sul telefono apre l'URI `otpauth`: si mostra [message] e l'utente inserisce la chiave a mano. */
    fun onOpenAppFailed(message: UiMessage) = _state.update { it.copy(error = message) }

    /**
     * Esegue [call] se non c'è già una richiesta in corso, segnando lo stato come occupato. In caso di successo chiama
     * [onSuccess], altrimenti [onFailure], che mette l'errore sul campo del codice o nel banner.
     */
    private fun <T> request(call: suspend () -> Result<T>, onSuccess: suspend (T) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, codeError = null) }
        viewModelScope.launch {
            val result = safely { call() }
            _state.update { it.copy(busy = false) }
            result.fold(onSuccess = { onSuccess(it) }, onFailure = { failure -> onFailure(failure) })
        }
    }

    /** Traduce un errore del server nello stato della schermata. */
    private suspend fun onFailure(failure: Throwable) {
        val error = (failure as? ApiException)?.error
        when {
            // Nel passo SETUP il server non dice quale dei due codici è sbagliato: il messaggio li cita entrambi.
            error is ApiError.BusinessRule && error.slug == "invalid-mfa-code" -> _state.update {
                it.copy(codeError = UiMessage(if (it.step == MfaStep.SETUP) Res.string.error_mfa_setup_codes_wrong else Res.string.error_mfa_code_wrong))
            }
            // La verifica è già attiva (per esempio attivata da un altro dispositivo): si mostra lo stato reale.
            error is ApiError.Conflict && error.slug == "mfa-already-enabled" -> {
                _state.update { it.copy(enabled = true, step = MfaStep.OVERVIEW) }
                session.reloadProfile()
            }
            else -> _state.update { it.copy(error = error?.uiMessage() ?: UiMessage(Res.string.error_generic)) }
        }
    }
}
