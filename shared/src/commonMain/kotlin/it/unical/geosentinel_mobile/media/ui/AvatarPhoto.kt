// Componente che mostra la foto profilo dell'utente, scaricata con un URL firmato tramite Coil.
package it.unical.geosentinel_mobile.media.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import it.unical.geosentinel_mobile.media.data.MediaRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Mostra la foto profilo [mediaId] ritagliata in un cerchio di diametro [size]. Il chiamante la disegna sopra
 * l'iniziale dell'utente, che resta visibile finché la foto non arriva o se non arriva affatto. È usata nel Profilo e
 * nel tondo dell'utente sulla mappa. La cache su disco di Coil è disattivata: la foto di una persona resta solo in
 * memoria. Se il download fallisce (per esempio per l'URL scaduto) se ne chiede uno nuovo.
 */
@Composable
internal fun AvatarPhoto(mediaId: String, size: Dp) {
    val signed = rememberSignedUrl(koinInject<MediaRepository>(), mediaId)
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val sidePx = with(LocalDensity.current) { size.roundToPx() }
    val url = signed.url ?: return
    val request = remember(url, sidePx) {
        ImageRequest.Builder(context)
            .data(url)
            .size(sidePx)
            .memoryCacheKey(mediaId)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { scope.launch { signed.onDownloadFailed() } },
        modifier = Modifier.size(size).clip(CircleShape),
    )
}
