package it.unical.geosentinel_mobile.hazard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.error_validation
import geosentinel_mobile.shared.generated.resources.report_error_description
import geosentinel_mobile.shared.generated.resources.report_error_location
import geosentinel_mobile.shared.generated.resources.report_error_severity
import geosentinel_mobile.shared.generated.resources.report_error_type
import it.unical.geosentinel_mobile.api.model.CreateHazardRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Come il backend (CreateHazardRequest.description, maxLength)
const val HAZARD_DESCRIPTION_MAX = 1000

enum class HazardField { TYPE, SEVERITY, LOCATION, DESCRIPTION }

data class ReportHazardUiState(
    // Tipo e gravità senza un valore di partenza: una segnalazione con il tipo sbagliato è peggio di nessuna
    val type: CreateHazardRequest.Type? = null,
    val severity: CreateHazardRequest.Severity? = null,
    val description: String = "",
    // Il centro della mappa del form: la UI lo aggiorna quando la mappa si ferma
    val location: GeoPoint? = null,
    val submitting: Boolean = false,
    val fieldErrors: Map<HazardField, UiMessage> = emptyMap(),
    val error: UiMessage? = null,
    val sent: Boolean = false,
) {
    // niente posizione né descrizione nei log
    override fun toString() =
        "ReportHazardUiState(type=$type, severity=$severity, submitting=$submitting, sent=$sent, fieldErrors=${fieldErrors.keys}, error=$error)"
}

/** Form della segnalazione di un pericolo (M4.1). [trailId] c'è se si parte dal dettaglio di un sentiero. */
class ReportHazardViewModel(private val hazards: HazardRepository, private val trailId: String?) : ViewModel() {

    private val _state = MutableStateFlow(ReportHazardUiState())
    val state: StateFlow<ReportHazardUiState> = _state.asStateFlow()

    fun onTypeChange(value: CreateHazardRequest.Type) = edit(HazardField.TYPE) { copy(type = value) }
    fun onSeverityChange(value: CreateHazardRequest.Severity) = edit(HazardField.SEVERITY) { copy(severity = value) }
    fun onDescriptionChange(value: String) = edit(HazardField.DESCRIPTION) { copy(description = value) }
    fun onLocationChange(value: GeoPoint) = edit(HazardField.LOCATION) { copy(location = value) }

    // Modificare un campo toglie il suo errore
    private fun edit(field: HazardField, change: ReportHazardUiState.() -> ReportHazardUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null) }

    fun submit() {
        val current = _state.value
        if (current.submitting || current.sent) return
        val type = current.type
        val severity = current.severity
        val location = current.location
        val localErrors = localErrors(current)
        if (localErrors.isNotEmpty() || type == null || severity == null || location == null) {
            _state.update { it.copy(fieldErrors = localErrors) }
            return
        }
        _state.update { it.copy(submitting = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val result = hazards.report(type, severity, location, current.description, trailId)
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(submitting = false, sent = true) },
                    onFailure = { state.withServerError(it).copy(submitting = false) },
                )
            }
        }
    }

    private fun localErrors(state: ReportHazardUiState): Map<HazardField, UiMessage> = buildMap {
        if (state.type == null) put(HazardField.TYPE, fieldMessages.getValue(HazardField.TYPE))
        if (state.severity == null) put(HazardField.SEVERITY, fieldMessages.getValue(HazardField.SEVERITY))
        if (state.location == null) put(HazardField.LOCATION, fieldMessages.getValue(HazardField.LOCATION))
        if (state.description.trim().length > HAZARD_DESCRIPTION_MAX) put(HazardField.DESCRIPTION, fieldMessages.getValue(HazardField.DESCRIPTION))
    }
}

// Come in registrazione: i campi del server diventano messaggi nostri (il testo del server è in inglese e non si
// mostra), un campo che il form non ha (es. trailId) un errore generico di validazione
private fun ReportHazardUiState.withServerError(failure: Throwable): ReportHazardUiState =
    when (val error = (failure as? ApiException)?.error) {
        is ApiError.Validation -> {
            val fields = error.fieldErrors.keys.mapNotNull(::serverField).toSet().associateWith { fieldMessages.getValue(it) }
            val unknownField = error.fieldErrors.keys.any { serverField(it) == null } || fields.isEmpty()
            copy(fieldErrors = fields, error = if (unknownField) UiMessage(Res.string.error_validation) else null)
        }
        null -> copy(error = UiMessage(Res.string.error_generic))
        else -> copy(error = error.uiMessage())
    }

// "location", "location.lat" e "location.lon" sono tutti la posizione
private fun serverField(name: String): HazardField? = when (name.substringBefore('.')) {
    "type" -> HazardField.TYPE
    "severity" -> HazardField.SEVERITY
    "location" -> HazardField.LOCATION
    "description" -> HazardField.DESCRIPTION
    else -> null
}

private val fieldMessages = mapOf(
    HazardField.TYPE to UiMessage(Res.string.report_error_type),
    HazardField.SEVERITY to UiMessage(Res.string.report_error_severity),
    HazardField.LOCATION to UiMessage(Res.string.report_error_location),
    HazardField.DESCRIPTION to UiMessage(Res.string.report_error_description, HAZARD_DESCRIPTION_MAX),
)
