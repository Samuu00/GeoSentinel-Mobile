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
import geosentinel_mobile.shared.generated.resources.report_photo_rejected_message
import geosentinel_mobile.shared.generated.resources.report_photo_unreadable
import it.unical.geosentinel_mobile.api.model.CreateHazardRequest
import it.unical.geosentinel_mobile.api.model.InitUploadRequest
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.media.data.MediaTarget
import it.unical.geosentinel_mobile.media.data.MediaUploader
import kotlin.coroutines.cancellation.CancellationException
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
    // Foto allegate dopo l'invio (M4.2), nell'ordine in cui sono state scelte
    val photos: List<PhotoUpload> = emptyList(),
    val photoError: UiMessage? = null,
) {
    // niente posizione né descrizione nei log
    override fun toString() =
        "ReportHazardUiState(type=$type, severity=$severity, submitting=$submitting, sent=$sent, fieldErrors=${fieldErrors.keys}, error=$error, photos=$photos)"
}

// Un limite dell'app, non del backend (che ha la quota di 1 GB per utente): bastano a mostrare il pericolo
const val MAX_HAZARD_PHOTOS = 5

enum class PhotoStatus { UPLOADING, READY, REJECTED, FAILED }

/** Una foto della segnalazione. [jpeg] arriva dopo la ricodifica: serve alla miniatura e a "Riprova". */
data class PhotoUpload(val id: Int, val status: PhotoStatus, val jpeg: ByteArray? = null) {
    // niente byte nei log
    override fun toString() = "PhotoUpload(id=$id, status=$status)"
}

/**
 * Form della segnalazione di un pericolo (M4.1) e, dopo l'invio, le foto (M4.2): il backend accetta l'upload su
 * `HAZARD_REPORT` solo da chi ha fatto la segnalazione, quindi servono l'id appena creato e la stessa sessione.
 * [trailId] c'è se si parte dal dettaglio di un sentiero.
 */
class ReportHazardViewModel(
    private val hazards: HazardRepository,
    private val uploader: MediaUploader,
    private val trailId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportHazardUiState())
    val state: StateFlow<ReportHazardUiState> = _state.asStateFlow()

    private var hazardId: String? = null
    private var nextPhotoId = 0

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
                    onSuccess = { hazard ->
                        hazardId = hazard.id
                        state.copy(submitting = false, sent = true)
                    },
                    onFailure = { state.withServerError(it).copy(submitting = false) },
                )
            }
        }
    }

    /** Nuova foto per la segnalazione inviata. [encode] è la ricodifica in JPEG della foto scelta (ImageEncoder). */
    fun addPhoto(encode: suspend () -> ByteArray) {
        val hazardId = hazardId ?: return
        if (_state.value.photos.size >= MAX_HAZARD_PHOTOS) return
        val photoId = nextPhotoId++
        _state.update { it.copy(photos = it.photos + PhotoUpload(photoId, PhotoStatus.UPLOADING), photoError = null) }
        viewModelScope.launch {
            val jpeg = try {
                encode()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // foto illeggibile (formato strano, file sparito): non c'è niente da riprovare
                _state.update { state ->
                    state.copy(photos = state.photos.filterNot { it.id == photoId }, photoError = UiMessage(Res.string.report_photo_unreadable))
                }
                return@launch
            }
            // la miniatura compare subito, mentre l'upload parte
            _state.update { state -> state.copy(photos = state.photos.map { if (it.id == photoId) it.copy(jpeg = jpeg) else it }) }
            upload(hazardId, photoId, jpeg)
        }
    }

    fun retryPhoto(photoId: Int) {
        val hazardId = hazardId ?: return
        val photo = _state.value.photos.firstOrNull { it.id == photoId }
        // solo le foto non partite: un doppio tocco trova già UPLOADING
        val jpeg = photo?.jpeg?.takeIf { photo.status == PhotoStatus.FAILED } ?: return
        setPhotoStatus(photoId, PhotoStatus.UPLOADING)
        _state.update { it.copy(photoError = null) }
        viewModelScope.launch { upload(hazardId, photoId, jpeg) }
    }

    private suspend fun upload(hazardId: String, photoId: Int, jpeg: ByteArray) {
        uploader.upload(jpeg, MediaTarget(InitUploadRequest.EntityType.HAZARD_REPORT, hazardId)).fold(
            onSuccess = { media ->
                if (media.status == MediaResponse.Status.READY) {
                    setPhotoStatus(photoId, PhotoStatus.READY)
                } else {
                    // QUARANTINED: il server non l'ha riconosciuta come immagine, ripeterla non cambia l'esito
                    setPhotoStatus(photoId, PhotoStatus.REJECTED)
                    _state.update { it.copy(photoError = UiMessage(Res.string.report_photo_rejected_message)) }
                }
            },
            onFailure = { failure ->
                setPhotoStatus(photoId, PhotoStatus.FAILED)
                _state.update { it.copy(photoError = (failure as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)) }
            },
        )
    }

    private fun setPhotoStatus(photoId: Int, status: PhotoStatus) = _state.update { state ->
        state.copy(photos = state.photos.map { if (it.id == photoId) it.copy(status = status) else it })
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
