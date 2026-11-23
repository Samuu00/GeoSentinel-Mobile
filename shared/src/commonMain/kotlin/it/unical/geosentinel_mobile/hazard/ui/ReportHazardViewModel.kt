// Stato e logica del form "Segnala un pericolo": validazione dei campi, invio della segnalazione e, dopo l'invio,
// caricamento e rimozione delle foto allegate.
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
import it.unical.geosentinel_mobile.media.data.MediaRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Lunghezza massima della descrizione, uguale al maxLength di CreateHazardRequest.description nel backend. */
const val HAZARD_DESCRIPTION_MAX = 1000

/** Campi del form che possono avere un errore di validazione. */
enum class HazardField { TYPE, SEVERITY, LOCATION, DESCRIPTION }

/**
 * Stato immutabile del form di segnalazione.
 *
 * @property type tipo del pericolo; non ha un valore iniziale, come [severity], perché una segnalazione con il tipo
 * sbagliato è peggio di nessuna segnalazione.
 * @property severity gravità scelta dall'utente.
 * @property description testo libero facoltativo.
 * @property location centro della mappa del form, aggiornato dalla UI quando la mappa si ferma.
 * @property submitting invio in corso.
 * @property fieldErrors errori da mostrare sotto i singoli campi.
 * @property error errore generale, mostrato sopra il pulsante d'invio.
 * @property sent true dopo l'invio riuscito: la schermata passa a "Segnalazione inviata".
 * @property photos foto allegate dopo l'invio, nell'ordine in cui sono state scelte.
 * @property photoError ultimo errore relativo alle foto.
 */
data class ReportHazardUiState(
    val type: CreateHazardRequest.Type? = null,
    val severity: CreateHazardRequest.Severity? = null,
    val description: String = "",
    val location: GeoPoint? = null,
    val submitting: Boolean = false,
    val fieldErrors: Map<HazardField, UiMessage> = emptyMap(),
    val error: UiMessage? = null,
    val sent: Boolean = false,
    val photos: List<PhotoUpload> = emptyList(),
    val photoError: UiMessage? = null,
) {
    /** Rappresentazione per i log: niente posizione né descrizione, che sono dati personali. */
    override fun toString() =
        "ReportHazardUiState(type=$type, severity=$severity, submitting=$submitting, sent=$sent, fieldErrors=${fieldErrors.keys}, error=$error, photos=$photos)"
}

/**
 * Numero massimo di foto per segnalazione. È un limite dell'app, non del backend (che impone solo una quota di spazio
 * per utente): cinque foto bastano a documentare un pericolo.
 */
const val MAX_HAZARD_PHOTOS = 5

/** Stato dell'upload di una foto. */
enum class PhotoStatus {
    /** Ricodifica o invio in corso. */
    UPLOADING,

    /** Caricata e accettata dal server. */
    READY,

    /** Caricata ma messa in quarantena dal server (non riconosciuta come immagine); riprovare non cambia l'esito. */
    REJECTED,

    /** Invio fallito (rete o errore del server): si può riprovare. */
    FAILED,

    /** Cancellazione sul server in corso. */
    REMOVING
}

/**
 * Una foto della segnalazione nel form. [id] è un identificativo locale, progressivo. [jpeg] è disponibile dopo la
 * ricodifica e serve sia per la miniatura sia per "Riprova". [mediaId] è l'id assegnato dal server, presente quando la
 * foto è stata caricata (anche se in quarantena), e serve per cancellarla.
 */
data class PhotoUpload(val id: Int, val status: PhotoStatus, val jpeg: ByteArray? = null, val mediaId: String? = null) {
    /** Nei log niente byte dell'immagine. */
    override fun toString() = "PhotoUpload(id=$id, status=$status)"
}

/**
 * ViewModel del form di segnalazione di un pericolo e, dopo l'invio, delle foto allegate. Le foto si aggiungono solo
 * dopo l'invio perché il backend accetta un upload su `HAZARD_REPORT` solo da chi ha fatto la segnalazione: servono
 * l'id appena creato e la stessa sessione.
 *
 * @param trailId sentiero a cui legare la segnalazione, presente se il form è stato aperto dal dettaglio di un sentiero.
 * @param uploadScope scope dell'applicazione in cui girano gli upload, così uscire dalla schermata "Segnalazione
 * inviata" non li annulla. Nei test è null e si usa il viewModelScope.
 */
class ReportHazardViewModel(
    private val hazards: HazardRepository,
    private val uploader: MediaUploader,
    private val media: MediaRepository,
    private val trailId: String?,
    private val uploadScope: CoroutineScope? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportHazardUiState())
    val state: StateFlow<ReportHazardUiState> = _state.asStateFlow()

    /** Id della segnalazione creata, disponibile dopo l'invio riuscito. */
    private var hazardId: String? = null

    /** Prossimo id locale da assegnare a una foto. */
    private var nextPhotoId = 0

    /** Aggiorna il tipo scelto. */
    fun onTypeChange(value: CreateHazardRequest.Type) = edit(HazardField.TYPE) { copy(type = value) }
    /** Aggiorna la gravità scelta. */
    fun onSeverityChange(value: CreateHazardRequest.Severity) = edit(HazardField.SEVERITY) { copy(severity = value) }
    /** Aggiorna la descrizione. */
    fun onDescriptionChange(value: String) = edit(HazardField.DESCRIPTION) { copy(description = value) }
    /** Aggiorna la posizione con il centro della mappa. */
    fun onLocationChange(value: GeoPoint) = edit(HazardField.LOCATION) { copy(location = value) }

    /** Applica la modifica [change] allo stato e toglie l'errore del campo modificato e quello generale. */
    private fun edit(field: HazardField, change: ReportHazardUiState.() -> ReportHazardUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null) }

    /**
     * Invia la segnalazione. Prima controlla i campi in locale e, se mancano o non sono validi, mostra gli errori senza
     * chiamare il server. Ignora le chiamate durante un invio o dopo un invio riuscito, così un doppio tocco non crea
     * due segnalazioni.
     */
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

    /**
     * Aggiunge una foto alla segnalazione già inviata e ne avvia l'upload. [encode] esegue la ricodifica in JPEG della
     * foto scelta (ImageEncoder). Non fa nulla prima dell'invio o se si è raggiunto MAX_HAZARD_PHOTOS.
     */
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
                // Foto illeggibile (formato non supportato, file sparito): non c'è nulla da riprovare, quindi la
                // miniatura si toglie e si mostra un messaggio.
                _state.update { state ->
                    state.copy(photos = state.photos.filterNot { it.id == photoId }, photoError = UiMessage(Res.string.report_photo_unreadable))
                }
                return@launch
            }
            // L'anteprima compare subito nella miniatura, mentre l'upload parte.
            _state.update { state -> state.copy(photos = state.photos.map { if (it.id == photoId) it.copy(jpeg = jpeg) else it }) }
            upload(hazardId, photoId, jpeg)
        }
    }

    /** Riprova l'upload di una foto fallita, riusando il JPEG già ricodificato. */
    fun retryPhoto(photoId: Int) {
        val hazardId = hazardId ?: return
        val photo = _state.value.photos.firstOrNull { it.id == photoId }
        // Si riprova solo una foto in stato FAILED: un doppio tocco trova la foto già in UPLOADING e non parte due volte.
        val jpeg = photo?.jpeg?.takeIf { photo.status == PhotoStatus.FAILED } ?: return
        setPhotoStatus(photoId, PhotoStatus.UPLOADING)
        _state.update { it.copy(photoError = null) }
        viewModelScope.launch { upload(hazardId, photoId, jpeg) }
    }

    /**
     * Carica il JPEG come foto della segnalazione e aggiorna lo stato della miniatura. L'upload vero e proprio gira in
     * uploadScope, quindi continua anche se il ViewModel viene distrutto; in quel caso il risultato non viene mostrato.
     */
    private suspend fun upload(hazardId: String, photoId: Int, jpeg: ByteArray) {
        val target = MediaTarget(InitUploadRequest.EntityType.HAZARD_REPORT, hazardId)
        (uploadScope ?: viewModelScope).async { uploader.upload(jpeg, target) }.await().fold(
            onSuccess = { uploaded ->
                _state.update { state -> state.copy(photos = state.photos.map { if (it.id == photoId) it.copy(mediaId = uploaded.id) else it }) }
                if (uploaded.status == MediaResponse.Status.READY) {
                    setPhotoStatus(photoId, PhotoStatus.READY)
                } else {
                    // QUARANTINED: il server non l'ha riconosciuta come immagine, quindi ripetere l'upload non
                    // cambierebbe l'esito.
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

    /**
     * Rimuove una foto. Se non ha un id sul server (upload non riuscito) sparisce subito; altrimenti prima la si
     * cancella sul server e, se non ci si riesce, resta al suo posto con lo stato precedente e un messaggio d'errore.
     * Una foto in invio non si può togliere, perché l'upload non si annulla.
     */
    fun removePhoto(photoId: Int) {
        val photo = _state.value.photos.firstOrNull { it.id == photoId } ?: return
        if (photo.status == PhotoStatus.UPLOADING || photo.status == PhotoStatus.REMOVING) return
        val mediaId = photo.mediaId
        if (mediaId == null) {
            _state.update { state -> state.copy(photos = state.photos.filterNot { it.id == photoId }, photoError = null) }
            return
        }
        val before = photo.status
        setPhotoStatus(photoId, PhotoStatus.REMOVING)
        _state.update { it.copy(photoError = null) }
        viewModelScope.launch {
            val result = media.delete(mediaId)
            // Un 404 significa che la foto è già stata cancellata (doppio tocco o risposta persa): per l'utente il
            // risultato è lo stesso, quindi si considera tolta.
            val gone = result.isSuccess || (result.exceptionOrNull() as? ApiException)?.error is ApiError.NotFound
            _state.update { state ->
                if (gone) {
                    state.copy(photos = state.photos.filterNot { it.id == photoId })
                } else {
                    val failure = result.exceptionOrNull()
                    state.copy(
                        photos = state.photos.map { if (it.id == photoId) it.copy(status = before) else it },
                        photoError = (failure as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic),
                    )
                }
            }
        }
    }

    /** Cambia lo stato della foto con id locale [photoId]. */
    private fun setPhotoStatus(photoId: Int, status: PhotoStatus) = _state.update { state ->
        state.copy(photos = state.photos.map { if (it.id == photoId) it.copy(status = status) else it })
    }

    /** Controlla i campi in locale e restituisce gli errori trovati (mappa vuota se il form è valido). */
    private fun localErrors(state: ReportHazardUiState): Map<HazardField, UiMessage> = buildMap {
        if (state.type == null) put(HazardField.TYPE, fieldMessages.getValue(HazardField.TYPE))
        if (state.severity == null) put(HazardField.SEVERITY, fieldMessages.getValue(HazardField.SEVERITY))
        if (state.location == null) put(HazardField.LOCATION, fieldMessages.getValue(HazardField.LOCATION))
        if (state.description.trim().length > HAZARD_DESCRIPTION_MAX) put(HazardField.DESCRIPTION, fieldMessages.getValue(HazardField.DESCRIPTION))
    }
}

/**
 * Applica allo stato un errore restituito dal server. Come nella registrazione, gli errori di validazione sui campi
 * diventano messaggi dell'app (il testo del server è in inglese e non viene mostrato); un errore su un campo che il form
 * non ha (per esempio trailId) diventa un errore generico di validazione. Gli altri errori diventano il messaggio
 * generale.
 */
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

/**
 * Converte il nome di un campo restituito dal server nel campo del form, oppure null se il form non lo ha. I nomi
 * annidati come "location.lat" e "location.lon" corrispondono tutti alla posizione.
 */
private fun serverField(name: String): HazardField? = when (name.substringBefore('.')) {
    "type" -> HazardField.TYPE
    "severity" -> HazardField.SEVERITY
    "location" -> HazardField.LOCATION
    "description" -> HazardField.DESCRIPTION
    else -> null
}

/** Messaggio d'errore di ciascun campo del form. */
private val fieldMessages = mapOf(
    HazardField.TYPE to UiMessage(Res.string.report_error_type),
    HazardField.SEVERITY to UiMessage(Res.string.report_error_severity),
    HazardField.LOCATION to UiMessage(Res.string.report_error_location),
    HazardField.DESCRIPTION to UiMessage(Res.string.report_error_description, HAZARD_DESCRIPTION_MAX),
)
