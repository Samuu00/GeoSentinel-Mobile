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

private const val JPEG = "image/jpeg"
private const val FILE_NAME = "photo.jpg"
// Limite del backend (api-guide, "Upload delle foto")
internal const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
private const val CONFIRM_ATTEMPTS = 3

/**
 * Upload di una foto (M4.2), nei tre passi della guida: richiesta di upload all'API, POST multipart diretto allo
 * storage con [storageClient] (senza `Auth`: il bearer non deve arrivare lì), conferma all'API.
 * Il risultato è `READY` oppure `QUARANTINED` (contenuto diverso da un'immagine): tutti e due sono un successo.
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

    suspend fun upload(jpeg: ByteArray, target: MediaTarget): Result<MediaResponse> {
        // lo storage lo rifiuterebbe comunque (400 EntityTooLarge), dopo aver occupato una richiesta di upload
        if (jpeg.size > MAX_UPLOAD_BYTES) return Result.failure(ApiException(ApiError.BusinessRule("media-payload-too-large", null)))

        var session = requestUpload(jpeg, target).getOrElse { return Result.failure(it) }
        var stored = send(session, jpeg)
        // Scaduto expiresAt (10 minuti) lo storage rifiuta il modulo: si riparte dal passo 1, una volta sola
        if (stored.isFailure && clock.now() >= session.expiresAt) {
            session = requestUpload(jpeg, target).getOrElse { return Result.failure(it) }
            stored = send(session, jpeg)
        }
        return stored.fold(onSuccess = { confirm(session.mediaId) }, onFailure = { Result.failure(it) })
    }

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

    // Prima tutti i formFields così come sono, poi `file` per ultimo con lo stesso Content-Type. Con un ByteArray Ktor
    // conosce la lunghezza di ogni parte e manda Content-Length (lo storage non accetta il chunked).
    private suspend fun send(session: UploadSessionResponse, jpeg: ByteArray): Result<Unit> = try {
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
            // 5xx: storage giù. 4xx: modulo rifiutato (scaduto, campo cambiato, file troppo grande)
            response.status.value >= 500 -> Result.failure(ApiException(ApiError.Server(response.status.value, null)))
            else -> Result.failure(ApiException(ApiError.BusinessRule("storage-rejected", null)))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        Result.failure(ApiException(ApiError.Network))
    }

    // La conferma si può ripetere (stesso esito): su un errore di rete si riprova qui, perché ricominciare
    // dall'inizio caricherebbe la stessa foto una seconda volta
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
