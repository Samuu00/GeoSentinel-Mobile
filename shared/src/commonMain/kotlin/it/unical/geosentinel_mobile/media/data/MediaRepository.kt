package it.unical.geosentinel_mobile.media.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.MediaApi
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.core.network.apiCall
import it.unical.geosentinel_mobile.core.network.safely
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Foto allegate. Tutte le rotte richiedono il login. Le immagini non passano dall'API: `getMediaDownloadUrl` dà un
 * URL firmato dello storage, valido 5 minuti, da scaricare senza bearer (Coil usa un client suo, senza `Auth`).
 */
class MediaRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val mediaApi by lazy { MediaApi(apiBaseUrl, client) }

    /** Le foto pronte di un sentiero: quelle in quarantena o non ancora caricate non si mostrano. */
    suspend fun photosOfTrail(trailId: String): Result<List<MediaResponse>> =
        apiCall { mediaApi.findAttachedMedia(MediaApi.EntityTypeFindAttachedMedia.TRAIL, trailId) }
            .map { media -> media.filter { it.status == MediaResponse.Status.READY } }

    // Le foto delle segnalazioni nelle liste (coda del gestore, le mie segnalazioni, card della mappa): una riga che
    // esce e rientra scorrendo non richiede di nuovo la lista. Solo la lista (id e metadati), mai gli URL firmati;
    // dura poco perché una foto si può aggiungere (MediaUploader) o togliere.
    private val hazardPhotos = mutableMapOf<String, Pair<TimeMark, List<MediaResponse>>>()
    private val hazardPhotosLock = Mutex()

    /**
     * Le foto pronte di una segnalazione: le vede chi può vedere la segnalazione (le regole sono del server). Una
     * risposta fuori contratto diventa un errore: le foto sono un di più, non fanno cadere la schermata.
     */
    suspend fun photosOfHazard(hazardId: String): Result<List<MediaResponse>> {
        hazardPhotosLock.withLock { hazardPhotos[hazardId] }
            ?.takeIf { (at, _) -> at.elapsedNow() < HazardPhotosFresh }
            ?.let { (_, list) -> return Result.success(list) }
        val result = safely { apiCall { mediaApi.findAttachedMedia(MediaApi.EntityTypeFindAttachedMedia.HAZARD_REPORT, hazardId) } }
            .map { media -> media.filter { it.status == MediaResponse.Status.READY } }
        result.onSuccess { list -> hazardPhotosLock.withLock { hazardPhotos[hazardId] = TimeSource.Monotonic.markNow() to list } }
        return result
    }

    /** Le foto pronte di un rifugio (le carica il suo gestore), da mostrare in testata al dettaglio. */
    suspend fun photosOfRefuge(refugeId: String): Result<List<MediaResponse>> =
        apiCall { mediaApi.findAttachedMedia(MediaApi.EntityTypeFindAttachedMedia.REFUGE, refugeId) }
            .map { media -> media.filter { it.status == MediaResponse.Status.READY } }

    /** Un URL nuovo a ogni chiamata: si chiede quando la foto si mostra, e di nuovo quando quello vecchio è scaduto. */
    suspend fun downloadUrl(mediaId: String): Result<String> =
        safely { apiCall { mediaApi.getMediaDownloadUrl(mediaId) } }.map { it.downloadUrl }

    /** Toglie una foto caricata (solo chi l'ha caricata, o un admin): il server la cancella anche dallo storage. */
    suspend fun delete(mediaId: String): Result<Unit> =
        apiCall { mediaApi.deleteMedia(mediaId) }.onSuccess { hazardPhotosLock.withLock { hazardPhotos.clear() } }
}

private val HazardPhotosFresh = 2.minutes
