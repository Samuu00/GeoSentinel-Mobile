package it.unical.geosentinel_mobile.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.error_mfa_code_wrong
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

// OVERVIEW: stato e azioni. SETUP: chiave e primo codice. CODES: i codici di recupero, una volta sola.
// DISABLE e REGENERATE: un codice per confermare.
enum class MfaStep { OVERVIEW, SETUP, CODES, DISABLE, REGENERATE }

data class MfaUiState(
    val enabled: Boolean,
    val step: MfaStep = MfaStep.OVERVIEW,
    val secret: String? = null,
    val otpauthUri: String? = null,
    val code: String = "",
    val recoveryCodes: List<String> = emptyList(),
    val savedConfirmed: Boolean = false,
    val busy: Boolean = false,
    val codeError: UiMessage? = null,
    val error: UiMessage? = null,
) {
    // DISABLE accetta anche un codice di recupero (testo libero); gli altri passi solo un TOTP
    val canSubmitCode: Boolean get() = !busy && (if (step == MfaStep.DISABLE) code.isNotBlank() else code.length == 6)

    // chiave, URI, codice e codici di recupero non finiscono nei log
    override fun toString() = "MfaUiState(enabled=$enabled, step=$step, busy=$busy, error=$error, codeError=$codeError)"
}

/** Attivazione e disattivazione della verifica in due passaggi dal Profilo (M10.2). Niente su disco. */
class MfaViewModel(private val mfa: MfaRepository, private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(
        MfaUiState(enabled = (session.state.value as? SessionState.LoggedIn)?.profile?.mfaEnabled == true),
    )
    val state: StateFlow<MfaUiState> = _state.asStateFlow()

    fun onCodeChange(value: String) = _state.update {
        val code = if (it.step == MfaStep.DISABLE) value.take(32) else value.filter(Char::isDigit).take(6)
        it.copy(code = code, codeError = null, error = null)
    }

    fun onSavedChange(saved: Boolean) = _state.update { it.copy(savedConfirmed = saved) }

    fun startSetup() = request({ mfa.startSetup() }) { setup ->
        _state.update { it.copy(step = MfaStep.SETUP, secret = setup.secret, otpauthUri = setup.otpauthUri, code = "") }
    }

    fun openDisable() = _state.update { it.copy(step = MfaStep.DISABLE, code = "", codeError = null, error = null) }

    fun openRegenerate() = _state.update { it.copy(step = MfaStep.REGENERATE, code = "", codeError = null, error = null) }

    fun submitCode() {
        val current = _state.value
        if (!current.canSubmitCode) return
        when (current.step) {
            MfaStep.SETUP -> request({ mfa.enable(current.code) }) { codes ->
                _state.update { it.copy(enabled = true, step = MfaStep.CODES, recoveryCodes = codes, secret = null, otpauthUri = null, code = "") }
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

    // Indietro da un passo: si torna allo stato, e chiave, codice e codici di recupero si dimenticano. Dal passo dei
    // codici la schermata chiede prima conferma (non si rivedranno più).
    fun back() = _state.update {
        it.copy(
            step = MfaStep.OVERVIEW, secret = null, otpauthUri = null, code = "", recoveryCodes = emptyList(),
            savedConfirmed = false, codeError = null, error = null,
        )
    }

    // L'URI otpauth:// non l'apre nessuna app: si usa la chiave a mano
    fun onOpenAppFailed(message: UiMessage) = _state.update { it.copy(error = message) }

    // Una richiesta alla volta; codice sbagliato sul campo, il resto nel banner
    private fun <T> request(call: suspend () -> Result<T>, onSuccess: suspend (T) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, codeError = null) }
        viewModelScope.launch {
            val result = safely { call() }
            _state.update { it.copy(busy = false) }
            result.fold(onSuccess = { onSuccess(it) }, onFailure = { failure -> onFailure(failure) })
        }
    }

    private suspend fun onFailure(failure: Throwable) {
        val error = (failure as? ApiException)?.error
        when {
            error is ApiError.BusinessRule && error.slug == "invalid-mfa-code" ->
                _state.update { it.copy(codeError = UiMessage(Res.string.error_mfa_code_wrong)) }
            // già attiva (da un altro dispositivo): si mostra com'è davvero
            error is ApiError.Conflict && error.slug == "mfa-already-enabled" -> {
                _state.update { it.copy(enabled = true, step = MfaStep.OVERVIEW) }
                session.reloadProfile()
            }
            else -> _state.update { it.copy(error = error?.uiMessage() ?: UiMessage(Res.string.error_generic)) }
        }
    }
}
