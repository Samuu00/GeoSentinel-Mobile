// ViewModel del form con cui un amministratore crea una nuova squadra di soccorso.
package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import it.unical.geosentinel_mobile.core.network.safely
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

/** Stato del form della nuova squadra. */
data class RescueTeamCreateUiState(
    val name: String = "",
    val callSign: String = "",
    val contactNumber: String = "",
    /** Posizione della base, cioè il centro della mappa scelto dall'amministratore; finché manca il form non si invia. */
    val location: GeoPoint? = null,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    val created: Boolean = false,
) {
    /**
     * Vero se i campi rispettano le lunghezze del server, così un form valido non riceve mai un 400. Diventa falso dopo
     * la creazione: la schermata si chiude, ma un secondo tocco prima della chiusura creerebbe un doppione.
     */
    val canSave: Boolean
        get() = name.trim().length in 2..100 && callSign.trim().length in 2..32 && contactNumber.trim().length in 5..30 &&
            location != null && !busy && !created

    // toString scritto a mano: nome e telefono non devono finire nei log.
    override fun toString() = "RescueTeamCreateUiState(busy=$busy, hasLocation=${location != null}, error=$error, created=$created)"
}

/** ViewModel della nuova squadra di soccorso, solo per `ADMIN`. Un nome già usato produce un 422 `business-rule` del server. */
class RescueTeamCreateViewModel(private val staff: StaffRepository) : ViewModel() {

    private val _state = MutableStateFlow(RescueTeamCreateUiState())
    val state: StateFlow<RescueTeamCreateUiState> = _state.asStateFlow()

    /** Aggiorna il nome, al massimo 100 caratteri. */
    fun onNameChange(name: String) = _state.update { it.copy(name = name.take(100), error = null) }
    /** Aggiorna il nominativo radio, al massimo 32 caratteri. */
    fun onCallSignChange(callSign: String) = _state.update { it.copy(callSign = callSign.take(32), error = null) }
    /** Aggiorna il numero di contatto, al massimo 30 caratteri. */
    fun onContactChange(contactNumber: String) = _state.update { it.copy(contactNumber = contactNumber.take(30), error = null) }
    /** Imposta la posizione della base scelta sulla mappa. */
    fun onLocationPicked(location: GeoPoint) = _state.update { it.copy(location = location) }

    /** Invia la nuova squadra con i campi ripuliti dagli spazi ai bordi. */
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
