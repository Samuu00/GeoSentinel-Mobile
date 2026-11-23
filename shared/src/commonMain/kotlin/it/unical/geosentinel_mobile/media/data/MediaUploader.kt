// Caricamento di una foto sul server, seguendo il protocollo in tre passi del backend: richiesta di upload all'API,
// invio diretto del file allo storage (compatibile S3) e conferma all'API. Usato dal form di segnalazione dei pericoli
// e dalla foto profilo.
package it.unical.geosentinel_mobile.media.data

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import it.unical.geosentinel_mobile.api.MediaApi
import it.unical.geosentinel_mobile.api.model.InitUploadRequest
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.api.model.UploadSessionResponse
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.apiCall
import kotlinx.coroutines.delay
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Tipo MIME di tutte le foto caricate: ImageEncoder le converte sempre in JPEG. */
private const val JPEG = "image/jpeg"

/** Nome di file dichiarato al server; il nome originale della foto non viene inviato. */
private const val FILE_NAME = "photo.jpg"

/** Dimensione massima di una foto accettata dal backend: 10 MiB (documentata nella guida delle API, "Upload delle foto"). */
internal const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024

/** Numero massimo di tentativi della conferma finale in caso di errore di rete. */
private const val CONFIRM_ATTEMPTS = 3

/**
 * Esegue l'upload di una foto nei tre passi previsti dalla guida delle API:
 * 1. richiesta di upload all'API, che restituisce l'URL dello storage e i campi firmati del modulo;
 * 2. POST multipart diretto allo storage con [storageClient], un client senza il plugin `Auth` perché il token
 *    dell'utente non deve mai arrivare allo storage;
 * 3. conferma all'API, che controlla il file caricato.
 *
 * Il risultato finale può essere `READY` oppure `QUARANTINED` (il contenuto non era un'immagine): per il client sono
 * entrambi un successo. [clock] è iniettabile per i test sulla scadenza della sessione di upload.
 */
class MediaUploader(
    httpClient: () -> HttpClient,
    storageClient: () -> HttpClient,
    apiBaseUrl: String,
    private val clock: Clock = Clock.System,
) {
    private val client by lazy(httpClient)
    private val storage by lazy(storageClient)
    private val mediaApi by lazy { MediaApi(apiBaseUrl, client) }
    // L'invio in chiaro (http) allo storage è ammesso solo se anche il backend è in chiaro, cioè nello sviluppo locale.
    // Negli altri casi un uploadUrl http:// (server compromesso o configurato male) farebbe viaggiare la firma del
    // modulo e la foto senza TLS, quindi viene rifiutato.
    private val cleartextAllowed = apiBaseUrl.startsWith("http://")

    /**
     * Carica [jpeg] e lo allega a [target]. Restituisce la foto confermata, oppure un fallimento con un ApiException:
     * `media-payload-too-large` se supera il limite, errori dell'API o dello storage, errore di rete.
     */
    suspend fun upload(jpeg: ByteArray, target: MediaTarget): Result<MediaResponse> {
        // Controllo locale della dimensione: lo storage rifiuterebbe comunque il file (400 EntityTooLarge), ma solo
        // dopo aver consumato una richiesta di upload sul server.
        if (jpeg.size > MAX_UPLOAD_BYTES) return Result.failure(ApiException(ApiError.BusinessRule("media-payload-too-large", null)))

        var session = requestUpload(jpeg, target).getOrElse { return Result.failure(it) }
        var stored = send(session, jpeg)
        // Dopo expiresAt (10 minuti dalla richiesta) lo storage rifiuta il modulo firmato. Se l'invio è fallito per
        // questo motivo si riparte dal primo passo, una volta sola.
        if (stored.isFailure && clock.now() >= session.expiresAt) {
            session = requestUpload(jpeg, target).getOrElse { return Result.failure(it) }
            stored = send(session, jpeg)
        }
        return stored.fold(onSuccess = { confirm(session.mediaId) }, onFailure = { Result.failure(it) })
    }

    /** Primo passo: chiede all'API una sessione di upload per il file, dichiarandone tipo, nome e dimensione. */
    private suspend fun requestUpload(jpeg: ByteArray, target: MediaTarget) = apiCall {
        mediaApi.createUploadRequest(
            InitUploadRequest(
                contentType = JPEG,
                originalFilename = FILE_NAME,
                sizeBytes = jpeg.size.toLong(),
                entityId = target.entityId,
                entityType = target.type,
            ),
        )
    }

    /**
     * Secondo passo: invia la foto allo storage con un POST multipart. L'ordine delle parti conta: prima tutti i
     * formFields firmati così come sono, poi `file` per ultimo con lo stesso Content-Type dichiarato. Passando un
     * ByteArray, Ktor conosce la lunghezza di ogni parte e invia Content-Length, perché lo storage non accetta il
     * trasferimento chunked. Restituisce un fallimento se l'URL non è https quando dovrebbe, se lo storage risponde
     * con un errore o se la rete cade.
     */
    private suspend fun send(session: UploadSessionResponse, jpeg: ByteArray): Result<Unit> = if (!cleartextAllowed && !session.uploadUrl.startsWith("https://")) {
        Result.failure(ApiException(ApiError.Server(status = 0, traceId = null)))
    } else try {
        val response = storage.submitFormWithBinaryData(
            url = session.uploadUrl,
            formData = formData {
                session.formFields.forEach { (name, value) -> append(name, value) }
                append(
                    "file",
                    jpeg,
                    Headers.build {
                        append(HttpHeaders.ContentType, JPEG)
                        append(HttpHeaders.ContentDisposition, "filename=\"$FILE_NAME\"")
                    },
                )
            },
        )
        when {
            response.status.isSuccess() -> Result.success(Unit)
            // Un 5xx indica lo storage non disponibile; un 4xx un modulo rifiutato (scaduto, campo alterato, file
            // troppo grande).
            response.status.value >= 500 -> Result.failure(ApiException(ApiError.Server(response.status.value, null)))
            else -> Result.failure(ApiException(ApiError.BusinessRule("storage-rejected", null)))
        }
    } catch (e: CancellationException) {
        // La cancellazione della coroutine va sempre propagata, non trasformata in un errore.
        throw e
    } catch (_: IOException) {
        Result.failure(ApiException(ApiError.Network))
    }

    /**
     * Terzo passo: conferma l'upload all'API. La conferma è idempotente (ripeterla dà lo stesso esito), quindi su un
     * errore di rete si riprova qui, fino a CONFIRM_ATTEMPTS volte a un secondo di distanza: ricominciare dal primo
     * passo caricherebbe la stessa foto una seconda volta. Gli altri errori si restituiscono subito.
     */
    private suspend fun confirm(mediaId: String): Result<MediaResponse> {
        var result = apiCall { mediaApi.confirmUpload(mediaId) }
        repeat(CONFIRM_ATTEMPTS - 1) {
            if ((result.exceptionOrNull() as? ApiException)?.error != ApiError.Network) return result
            delay(1.seconds)
            result = apiCall { mediaApi.confirmUpload(mediaId) }
        }
        return result
    }
}
