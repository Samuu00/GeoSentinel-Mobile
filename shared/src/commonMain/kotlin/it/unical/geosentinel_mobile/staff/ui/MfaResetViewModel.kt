package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_not_found
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_self
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.safely
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MfaResetUiState(
    val email: String = "",
    val busy: Boolean = false,
    val error: UiMessage? = null,
    val done: Boolean = false,
) {
    val canSubmit: Boolean get() = '@' in email && !busy && !done

    // l'email non va nei log
    override fun toString() = "MfaResetUiState(busy=$busy, error=$error, done=$done)"
}

/** Azzeramento della verifica in due passaggi di un utente che ha perso telefono e codici (M10.3), solo `ADMIN`. */
class MfaResetViewModel(private val staff: StaffRepository) : ViewModel() {

    private val _state = MutableStateFlow(MfaResetUiState())
    val state: StateFlow<MfaResetUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value.take(254), error = null) }

    // Dopo la conferma della schermata ("hai verificato la sua identità?")
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = safely { staff.resetUserMfa(current.email) }
            val failure = result.exceptionOrNull()
            _state.update {
                it.copy(
                    busy = false,
                    done = result.isSuccess,
                    error = when {
                        failure == null -> null
                        (failure as? ApiException)?.error is ApiError.NotFound -> UiMessage(Res.string.staff_mfa_reset_not_found)
                        else -> failure.toUiMessage(onRuleViolation = Res.string.staff_mfa_reset_self)
                    },
                )
            }
        }
    }
}
