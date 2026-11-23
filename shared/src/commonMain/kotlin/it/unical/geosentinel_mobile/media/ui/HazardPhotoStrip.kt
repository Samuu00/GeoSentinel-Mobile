// Componenti per mostrare le foto delle segnalazioni: la striscia di miniature, il visualizzatore a schermo intero e
// la gestione degli URL firmati (che scadono e vanno richiesti di nuovo). SignedUrl e photoRequest sono riusati anche
// da altre schermate con foto, come la foto profilo.
package it.unical.geosentinel_mobile.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.hazard_photo_description
import geosentinel_mobile.shared.generated.resources.hazard_photo_failed
import geosentinel_mobile.shared.generated.resources.photo_viewer_close
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.media.data.MediaRepository
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import it.unical.geosentinel_mobile.core.ui.AppIcons

/** Forma delle miniature: rettangolo con gli angoli arrotondati. */
private val ThumbShape = RoundedCornerShape(14.dp)

/** Richieste massime di URL firmato per una foto: il primo più due rinnovi, poi la foto si dà per non disponibile. */
private const val MaxUrlRequests = 3

/**
 * Mostra le foto della segnalazione [hazardId] come una striscia orizzontale di miniature; un tocco apre la foto a
 * schermo intero. La lista si carica quando la striscia compare. Se non ci sono foto, o se la richiesta fallisce, il
 * componente non occupa spazio: le foto sono un'aggiunta e il testo della segnalazione resta comunque leggibile.
 * Gli URL sono firmati e valgono 5 minuti: quando un download non riesce se ne chiede uno nuovo.
 */
@Composable
fun HazardPhotoStrip(hazardId: String, modifier: Modifier = Modifier) {
    val media = koinInject<MediaRepository>()
    var photos by remember(hazardId) { mutableStateOf<List<MediaResponse>>(emptyList()) }
    // Il repository tiene la lista in cache per poco tempo, così una riga che rientra nello schermo scorrendo non
    // ripete la richiesta.
    LaunchedEffect(hazardId) { media.photosOfHazard(hazardId).onSuccess { photos = it } }
    if (photos.isEmpty()) return

    var opened by remember(hazardId) { mutableStateOf<MediaResponse?>(null) }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
            Thumbnail(photo, number = index + 1, total = photos.size, media = media, onOpen = { opened = photo })
        }
    }
    opened?.let { photo -> PhotoViewer(photo, media, onClose = { opened = null }) }
}

/**
 * Stato dell'URL firmato di una foto, osservabile da Compose. L'URL si chiede quando la foto deve essere mostrata e
 * si rinnova quando il download non riesce (di solito perché è scaduto), fino a MaxUrlRequests richieste in tutto.
 * [url] è null finché non arriva; [failed] diventa true quando la foto va considerata non disponibile.
 */
internal class SignedUrl(private val media: MediaRepository, private val mediaId: String) {
    var url by mutableStateOf<String?>(null)
    var failed by mutableStateOf(false)
    private var requests = 0

    /** Chiede un nuovo URL al server; in caso di errore segna la foto come non disponibile. */
    suspend fun load() {
        requests++
        media.downloadUrl(mediaId).fold(onSuccess = { url = it }, onFailure = { failed = true })
    }

    /** Da chiamare quando Coil non riesce a scaricare l'immagine: scarta l'URL e ne chiede un altro, se ne restano. */
    suspend fun onDownloadFailed() {
        url = null
        if (requests >= MaxUrlRequests) failed = true else load()
    }

    /** Nuovo tentativo chiesto dall'utente dopo un fallimento: azzera il conteggio delle richieste e ricarica. */
    suspend fun retry() {
        requests = 0
        failed = false
        load()
    }
}

/** Crea e ricorda lo stato dell'URL firmato di [mediaId] e lo carica la prima volta che il componente compare. */
@Composable
internal fun rememberSignedUrl(media: MediaRepository, mediaId: String): SignedUrl {
    val signed = remember(mediaId) { SignedUrl(media, mediaId) }
    LaunchedEffect(mediaId) { if (signed.url == null && !signed.failed) signed.load() }
    return signed
}

/**
 * Miniatura di una foto nella striscia. Mostra un indicatore di caricamento, poi l'immagine ritagliata; se la foto non
 * è disponibile mostra un testo e il tocco riprova, altrimenti il tocco apre la foto. [number] e [total] servono alla
 * descrizione per i lettori di schermo ("Foto 2 di 3").
 */
@Composable
private fun Thumbnail(photo: MediaResponse, number: Int, total: Int, media: MediaRepository, onOpen: () -> Unit) {
    val signed = rememberSignedUrl(media, photo.id)
    val scope = rememberCoroutineScope()
    val description = stringResource(Res.string.hazard_photo_description, number, total)
    val failedLabel = stringResource(Res.string.hazard_photo_failed)
    val url = signed.url
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 112.dp, height = 84.dp)
            .clip(ThumbShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button) { if (signed.failed) scope.launch { signed.retry() } else onOpen() }
            .semantics { contentDescription = if (signed.failed) failedLabel else description },
    ) {
        when {
            signed.failed -> Text(
                failedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(6.dp),
            )
            url != null -> AsyncImage(
                model = photoRequest(url, photo.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { scope.launch { signed.onDownloadFailed() } },
                modifier = Modifier.fillMaxSize(),
            )
            else -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * Mostra la foto a schermo intero in un Dialog, intera (non ritagliata) su fondo scuro. Il pulsante tondo in alto a
 * destra la chiude, come il tasto Indietro. In basso a sinistra c'è l'eventuale credito della foto.
 */
@Composable
internal fun PhotoViewer(photo: MediaResponse, media: MediaRepository, onClose: () -> Unit) {
    val signed = rememberSignedUrl(media, photo.id)
    val scope = rememberCoroutineScope()
    val url = signed.url
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim), contentAlignment = Alignment.Center) {
            when {
                url != null -> AsyncImage(
                    model = photoRequest(url, photo.id),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { scope.launch { signed.onDownloadFailed() } },
                    modifier = Modifier.fillMaxSize(),
                )
                signed.failed -> Text(
                    stringResource(Res.string.hazard_photo_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.clickable(role = Role.Button) { scope.launch { signed.retry() } }.padding(24.dp),
                )
                else -> CircularProgressIndicator()
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp)
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
            ) {
                Icon(AppIcons.Close, contentDescription = stringResource(Res.string.photo_viewer_close))
            }
            PhotoCredit(
                photo.credit,
                Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp),
            )
        }
    }
}

/**
 * Costruisce la richiesta di Coil per una foto. Come chiave della cache (in memoria e su disco) si usa l'id della foto
 * e non l'URL: l'URL firmato cambia a ogni richiesta, mentre la foto resta la stessa e non va riscaricata.
 */
@Composable
internal fun photoRequest(url: String, mediaId: String): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(url) { ImageRequest.Builder(context).data(url).memoryCacheKey(mediaId).diskCacheKey(mediaId).build() }
}
