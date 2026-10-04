package it.unical.geosentinel_mobile.media.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.MediaApi
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.core.network.apiCall

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

    /** Un URL nuovo a ogni chiamata: si chiede quando la foto si mostra, e di nuovo quando quello vecchio è scaduto. */
    suspend fun downloadUrl(mediaId: String): Result<String> = apiCall { mediaApi.getMediaDownloadUrl(mediaId) }.map { it.downloadUrl }
}
