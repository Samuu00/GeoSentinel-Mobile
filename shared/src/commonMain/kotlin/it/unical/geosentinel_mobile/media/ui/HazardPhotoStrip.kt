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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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

private val ThumbShape = RoundedCornerShape(14.dp)
// il primo URL più due rinnovi, come nel dettaglio del sentiero
private const val MaxUrlRequests = 3

/**
 * Le foto di una segnalazione (M4.2 bis): una striscia di miniature che un tocco apre a schermo intero. Si caricano
 * quando la striscia compare; senza foto (o se la lista non arriva) non occupa spazio: le foto sono un di più, il testo
 * della segnalazione resta. Gli URL sono firmati e durano 5 minuti: quando un download non riesce se ne chiede un altro.
 */
@Composable
fun HazardPhotoStrip(hazardId: String, modifier: Modifier = Modifier) {
    val media = koinInject<MediaRepository>()
    var photos by remember(hazardId) { mutableStateOf<List<MediaResponse>>(emptyList()) }
    // la lista è in cache nel repository per poco (una riga che rientra scorrendo non la richiede di nuovo)
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

// L'URL firmato di una foto: chiesto quando serve, rinnovato quando il download non riesce (scaduto), al massimo 3 volte
internal class SignedUrl(private val media: MediaRepository, private val mediaId: String) {
    var url by mutableStateOf<String?>(null)
    var failed by mutableStateOf(false)
    private var requests = 0

    suspend fun load() {
        requests++
        media.downloadUrl(mediaId).fold(onSuccess = { url = it }, onFailure = { failed = true })
    }

    suspend fun onDownloadFailed() {
        url = null
        if (requests >= MaxUrlRequests) failed = true else load()
    }

    suspend fun retry() {
        requests = 0
        failed = false
        load()
    }
}

@Composable
internal fun rememberSignedUrl(media: MediaRepository, mediaId: String): SignedUrl {
    val signed = remember(mediaId) { SignedUrl(media, mediaId) }
    LaunchedEffect(mediaId) { if (signed.url == null && !signed.failed) signed.load() }
    return signed
}

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

// La foto a schermo intero, intera (non tagliata), su fondo scuro; il tondo in alto a destra la chiude, come Indietro
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
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.photo_viewer_close))
            }
        }
    }
}

// La chiave della cache è l'id, non l'URL: l'URL cambia a ogni richiesta, la foto no (come nel dettaglio del sentiero)
@Composable
internal fun photoRequest(url: String, mediaId: String): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(url) { ImageRequest.Builder(context).data(url).memoryCacheKey(mediaId).diskCacheKey(mediaId).build() }
}
