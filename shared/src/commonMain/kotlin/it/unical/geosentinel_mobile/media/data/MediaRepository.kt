// Accesso alle foto allegate a sentieri, segnalazioni e rifugi tramite le API del backend. Legge le liste delle foto,
// chiede gli URL firmati per scaricarle e cancella le foto; il caricamento invece sta in MediaUploader.
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
 * Repository delle foto allegate. Tutte le rotte richiedono il login. I byte delle immagini non passano dall'API:
 * `getMediaDownloadUrl` restituisce un URL firmato dello storage, valido 5 minuti, che si scarica senza il token
 * (Coil usa un proprio client HTTP senza il plugin `Auth`, così il bearer non esce verso lo storage).
 *
 * Il client HTTP arriva come funzione e viene creato solo al primo uso.
 */
class MediaRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {

    private val client by lazy(httpClient)
    private val mediaApi by lazy { MediaApi(apiBaseUrl, client) }

    /** Restituisce le foto pronte (`READY`) di un sentiero; quelle in quarantena o non ancora caricate si scartano. */
    suspend fun photosOfTrail(trailId: String): Result<List<MediaResponse>> =
        apiCall { mediaApi.findAttachedMedia(MediaApi.EntityTypeFindAttachedMedia.TRAIL, trailId) }
            .map { media -> media.filter { it.status == MediaResponse.Status.READY } }

    // Cache in memoria delle liste di foto delle segnalazioni, usate in liste che scorrono (coda del gestore, "Le mie
    // segnalazioni", card della mappa): una riga che esce e rientra nello schermo non rifà la richiesta. Si conservano
    // solo gli id e i metadati, mai gli URL firmati (che scadono). La durata è breve (HazardPhotosFresh) perché nel
    // frattempo una foto può essere aggiunta con MediaUploader o cancellata. La mappa è protetta da un Mutex perché
    // più righe la leggono e scrivono in parallelo.
    private val hazardPhotos = mutableMapOf<String, Pair<TimeMark, List<MediaResponse>>>()
    private val hazardPhotosLock = Mutex()

    /**
     * Restituisce le foto pronte di una segnalazione, dalla cache se ancora fresca. Può vederle chi può vedere la
     * segnalazione, secondo le regole del server. La chiamata è avvolta in `safely`: una risposta fuori contratto
     * diventa un errore e non un crash, perché le foto sono un elemento accessorio e non devono far cadere la schermata.
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

    /** Restituisce le foto pronte di un rifugio (caricate dal suo gestore), mostrate in testata nel dettaglio. */
    suspend fun photosOfRefuge(refugeId: String): Result<List<MediaResponse>> =
        apiCall { mediaApi.findAttachedMedia(MediaApi.EntityTypeFindAttachedMedia.REFUGE, refugeId) }
            .map { media -> media.filter { it.status == MediaResponse.Status.READY } }

    /**
     * Chiede al server un URL firmato per scaricare la foto. Ogni chiamata ne produce uno nuovo: si chiede quando la
     * foto viene mostrata e di nuovo quando il precedente è scaduto.
     */
    suspend fun downloadUrl(mediaId: String): Result<String> =
        safely { apiCall { mediaApi.getMediaDownloadUrl(mediaId) } }.map { it.downloadUrl }

    /**
     * Cancella una foto; il server lo permette solo a chi l'ha caricata o a un admin e la rimuove anche dallo storage.
     * In caso di successo svuota la cache delle foto delle segnalazioni, che altrimenti la mostrerebbe ancora.
     */
    suspend fun delete(mediaId: String): Result<Unit> =
        apiCall { mediaApi.deleteMedia(mediaId) }.onSuccess { hazardPhotosLock.withLock { hazardPhotos.clear() } }
}

/** Per quanto tempo una lista di foto di una segnalazione resta valida nella cache. */
private val HazardPhotosFresh = 2.minutes
