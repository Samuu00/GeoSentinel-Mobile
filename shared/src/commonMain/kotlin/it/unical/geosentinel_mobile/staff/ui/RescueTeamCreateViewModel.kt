package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.staff_team_name_taken
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RescueTeamCreateUiState(
    val name: String = "",
    val callSign: String = "",
    val contactNumber: String = "",
    // la base è il centro della mappa scelto dall'admin: senza, il form non si invia
    val location: GeoPoint? = null,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    val created: Boolean = false,
) {
    // le lunghezze del backend (CreateRescueTeamRequest): un 400 non arriva mai da un form valido
    val canSave: Boolean
        get() = name.trim().length in 2..100 && callSign.trim().length in 2..32 && contactNumber.trim().length in 5..30 &&
            location != null && !busy

    // il nome e il telefono non vanno nei log
    override fun toString() = "RescueTeamCreateUiState(busy=$busy, hasLocation=${location != null}, error=$error, created=$created)"
}

/** Nuova squadra di soccorso, solo `ADMIN`. Un nome già usato è un 422 `business-rule` del server. */
class RescueTeamCreateViewModel(private val staff: StaffRepository) : ViewModel() {

    private val _state = MutableStateFlow(RescueTeamCreateUiState())
    val state: StateFlow<RescueTeamCreateUiState> = _state.asStateFlow()

    fun onNameChange(name: String) = _state.update { it.copy(name = name.take(100), error = null) }
    fun onCallSignChange(callSign: String) = _state.update { it.copy(callSign = callSign.take(32), error = null) }
    fun onContactChange(contactNumber: String) = _state.update { it.copy(contactNumber = contactNumber.take(30), error = null) }
    fun onLocationPicked(location: GeoPoint) = _state.update { it.copy(location = location) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val location = current.location ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = safely {
                staff.createRescueTeam(
                    name = current.name.trim(),
                    callSign = current.callSign.trim(),
                    contactNumber = current.contactNumber.trim(),
                    baseLocation = location,
                )
            }
            _state.update {
                it.copy(
                    busy = false,
                    created = result.isSuccess,
                    error = result.exceptionOrNull()?.toUiMessage(onRuleViolation = Res.string.staff_team_name_taken),
                )
            }
        }
    }
}
