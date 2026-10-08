package it.unical.geosentinel_mobile.rescue.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.api.model.RescueTeamResponse
import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.rescue.data.RescueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RescueAlertDetailUiState(
    val alert: SosAlertResponse? = null,
    // primo caricamento; con alert == null e loadError la schermata mostra solo "Riprova"
    val loading: Boolean = false,
    val loadError: UiMessage? = null,
    // squadre libere vicine, solo per un'allerta ACTIVE
    val teams: List<RescueTeamResponse> = emptyList(),
    val teamsLoading: Boolean = false,
    val teamsError: UiMessage? = null,
    val selectedTeamId: String? = null,
    // invio della squadra o chiusura in corso: i pulsanti si disattivano
    val busy: Boolean = false,
    // motivo dell'ultima azione non riuscita (es. "un altro operatore l'ha già presa")
    val actionError: UiMessage? = null,
    // chiusa da questo operatore: la schermata torna alla lista
    val resolved: Boolean = false,
) {
    // niente posizione né note mediche nei log
    override fun toString() =
        "RescueAlertDetailUiState(status=${alert?.status}, loading=$loading, loadError=$loadError, teams=${teams.size}, " +
            "busy=$busy, actionError=$actionError, resolved=$resolved)"
}

/**
 * Dettaglio di un'allerta per l'operatore (M7.2): posizione, età della posizione, note mediche, squadre libere vicine,
 * invio della squadra e chiusura. Il server decide: un 409 `alert-already-taken` o un 422 si mostrano e il dettaglio si
 * rilegge, così l'operatore vede la squadra del collega o lo stato nuovo.
 */
class RescueAlertDetailViewModel(private val alertId: String, private val rescue: RescueRepository) : ViewModel() {

    private val _state = MutableStateFlow(RescueAlertDetailUiState(loading = true))
    val state: StateFlow<RescueAlertDetailUiState> = _state.asStateFlow()

    // cresce a ogni risposta di un'azione: una rilettura partita prima non sovrascrive lo stato più nuovo
    private var version = 0

    init {
        reload(withTeams = true)
    }

    /**
     * Rilettura in silenzio (ogni 30 s con la schermata aperta): la posizione dell'escursionista cambia ogni 60 s e le
     * squadre libere anche. Con l'allerta in attesa si rileggono anche le squadre: arrivano pure se il primo caricamento
     * era fallito (prima restava "nessuna squadra libera" senza averle mai chieste).
     */
    fun refresh() {
        if (_state.value.busy) return
        reload(withTeams = true)
    }

    fun retry() {
        _state.update { it.copy(loading = it.alert == null, loadError = null) }
        reload(withTeams = true)
    }

    fun retryTeams() {
        val alert = _state.value.alert ?: return
        loadTeams(alert)
    }

    fun selectTeam(teamId: String) = _state.update { it.copy(selectedTeamId = teamId) }

    fun dispatch() {
        val current = _state.value
        val teamId = current.selectedTeamId ?: return
        if (current.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            val result = rescue.dispatch(alertId, teamId)
            version++
            result.fold(
                onSuccess = { alert -> _state.update { it.copy(alert = alert, busy = false, teams = emptyList(), selectedTeamId = null) } },
                onFailure = { failure ->
                    // preso da un collega (409), squadra non più libera o allerta chiusa (422): si rilegge tutto
                    _state.update { it.copy(busy = false, actionError = failure.toUiMessage(), selectedTeamId = null) }
                    reload(withTeams = true)
                },
            )
        }
    }

    fun resolve() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            val result = rescue.resolve(alertId)
            version++
            result.fold(
                onSuccess = { alert -> _state.update { it.copy(alert = alert, busy = false, resolved = true) } },
                onFailure = { failure ->
                    _state.update { it.copy(busy = false, actionError = failure.toUiMessage()) }
                    reload(withTeams = false)
                },
            )
        }
    }

    private fun reload(withTeams: Boolean) {
        val started = version
        viewModelScope.launch {
            val result = rescue.alert(alertId)
            if (version != started) return@launch
            result.fold(
                onSuccess = { alert ->
                    _state.update { it.copy(alert = alert, loading = false, loadError = null) }
                    if (alert.status == SosAlertResponse.Status.ACTIVE) {
                        if (withTeams) loadTeams(alert)
                    } else {
                        _state.update { it.copy(teams = emptyList(), teamsError = null, selectedTeamId = null) }
                    }
                },
                // con il dettaglio già a schermo un errore della rilettura non lo toglie
                onFailure = { failure ->
                    _state.update { if (it.alert == null) it.copy(loading = false, loadError = failure.toUiMessage()) else it.copy(loading = false) }
                },
            )
        }
    }

    private fun loadTeams(alert: SosAlertResponse) {
        val started = version
        _state.update { it.copy(teamsLoading = true, teamsError = null) }
        viewModelScope.launch {
            val result = rescue.availableTeams(alert.location.toGeoPoint())
            // un invio riuscito nel frattempo ha già tolto le squadre: la risposta vecchia non le rimette
            if (version != started) {
                _state.update { it.copy(teamsLoading = false) }
                return@launch
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { teams ->
                        // la squadra scelta resta solo se è ancora libera
                        val selected = state.selectedTeamId?.takeIf { id -> teams.any { it.id == id } }
                        state.copy(teams = teams, teamsLoading = false, selectedTeamId = selected)
                    },
                    // con le squadre già a schermo un errore della rilettura non le toglie: si riprova al giro dopo
                    onFailure = { failure ->
                        if (state.teams.isNotEmpty()) state.copy(teamsLoading = false) else state.copy(teamsLoading = false, teamsError = failure.toUiMessage())
                    },
                )
            }
        }
    }
}
