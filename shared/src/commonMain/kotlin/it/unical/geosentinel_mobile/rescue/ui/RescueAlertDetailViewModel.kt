// ViewModel del dettaglio di un'allerta SOS per l'operatore del soccorso: dati dell'allerta, squadre libere vicine,
// invio di una squadra e chiusura. Gestisce le corse tra riletture e azioni con un contatore di versione.
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

/** Stato del dettaglio dell'allerta. */
data class RescueAlertDetailUiState(
    val alert: SosAlertResponse? = null,
    /** Primo caricamento in corso. Se [alert] è null e c'è [loadError], la schermata mostra solo "Riprova". */
    val loading: Boolean = false,
    val loadError: UiMessage? = null,
    /** Squadre libere vicine, caricate solo per un'allerta ancora `ACTIVE`. */
    val teams: List<RescueTeamResponse> = emptyList(),
    val teamsLoading: Boolean = false,
    val teamsError: UiMessage? = null,
    val selectedTeamId: String? = null,
    /** Invio della squadra o chiusura in corso: i pulsanti si disattivano. */
    val busy: Boolean = false,
    /** Motivo dell'ultima azione non riuscita, per esempio "un altro operatore l'ha già presa". */
    val actionError: UiMessage? = null,
    /** Allerta chiusa da questo operatore: la schermata torna alla lista. */
    val resolved: Boolean = false,
) {
    // toString scritto a mano: posizione e note mediche non devono finire nei log.
    override fun toString() =
        "RescueAlertDetailUiState(status=${alert?.status}, loading=$loading, loadError=$loadError, teams=${teams.size}, " +
            "busy=$busy, actionError=$actionError, resolved=$resolved)"
}

/**
 * ViewModel del dettaglio dell'allerta [alertId]: posizione e sua età, note mediche, squadre libere vicine, invio
 * della squadra e chiusura. Decide il server: un 409 `alert-already-taken` o un 422 si mostrano all'operatore e il
 * dettaglio si rilegge, così l'operatore vede la squadra inviata dal collega o il nuovo stato.
 */
class RescueAlertDetailViewModel(private val alertId: String, private val rescue: RescueRepository) : ViewModel() {

    private val _state = MutableStateFlow(RescueAlertDetailUiState(loading = true))
    val state: StateFlow<RescueAlertDetailUiState> = _state.asStateFlow()

    // Cresce a ogni risposta di un'azione: una rilettura partita prima dell'azione, se arriva dopo, viene scartata
    // e non sovrascrive lo stato più recente.
    private var version = 0

    init {
        reload(withTeams = true)
    }

    /**
     * Rilettura silenziosa, chiamata periodicamente (ogni 30 secondi) mentre la schermata è aperta: la posizione
     * dell'escursionista si aggiorna ogni 60 secondi e anche le squadre libere cambiano. Con l'allerta ancora in
     * attesa si rileggono pure le squadre, così compaiono anche se il primo caricamento era fallito. Non fa nulla
     * durante un'azione.
     */
    fun refresh() {
        if (_state.value.busy) return
        reload(withTeams = true)
    }

    /** Riprova il caricamento del dettaglio dopo un errore. */
    fun retry() {
        _state.update { it.copy(loading = it.alert == null, loadError = null) }
        reload(withTeams = true)
    }

    /** Riprova il caricamento delle squadre libere. */
    fun retryTeams() {
        val alert = _state.value.alert ?: return
        loadTeams(alert)
    }

    /** Seleziona la squadra da inviare. */
    fun selectTeam(teamId: String) = _state.update { it.copy(selectedTeamId = teamId) }

    /** Invia la squadra selezionata; se il server rifiuta, mostra il motivo e rilegge tutto. */
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
                    // Allerta già presa da un collega (409), squadra non più libera o allerta chiusa (422): si rilegge
                    // tutto per mostrare lo stato reale.
                    _state.update { it.copy(busy = false, actionError = failure.toUiMessage(), selectedTeamId = null) }
                    reload(withTeams = true)
                },
            )
        }
    }

    /** Chiude l'allerta; se il server rifiuta, mostra il motivo e rilegge il dettaglio. */
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

    /**
     * Rilegge l'allerta e, se è ancora `ACTIVE` e [withTeams] è vero, anche le squadre libere. Una risposta arrivata
     * dopo un'azione (versione cambiata) si scarta.
     */
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
                // Se il dettaglio è già a schermo, un errore della rilettura non lo toglie.
                onFailure = { failure ->
                    _state.update { if (it.alert == null) it.copy(loading = false, loadError = failure.toUiMessage()) else it.copy(loading = false) }
                },
            )
        }
    }

    /** Carica le squadre libere attorno alla posizione dell'allerta. */
    private fun loadTeams(alert: SosAlertResponse) {
        val started = version
        _state.update { it.copy(teamsLoading = true, teamsError = null) }
        viewModelScope.launch {
            val result = rescue.availableTeams(alert.location.toGeoPoint())
            // Un invio riuscito nel frattempo ha già tolto le squadre: la risposta vecchia non deve rimetterle.
            if (version != started) {
                _state.update { it.copy(teamsLoading = false) }
                return@launch
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { teams ->
                        // La squadra scelta resta selezionata solo se è ancora libera.
                        val selected = state.selectedTeamId?.takeIf { id -> teams.any { it.id == id } }
                        state.copy(teams = teams, teamsLoading = false, selectedTeamId = selected)
                    },
                    // Se le squadre sono già a schermo, un errore della rilettura non le toglie: si riprova alla
                    // rilettura successiva.
                    onFailure = { failure ->
                        if (state.teams.isNotEmpty()) state.copy(teamsLoading = false) else state.copy(teamsLoading = false, teamsError = failure.toUiMessage())
                    },
                )
            }
        }
    }
}
