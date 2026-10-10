package it.unical.geosentinel_mobile.hazard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.report_photo_failed
import geosentinel_mobile.shared.generated.resources.report_photo_pick
import geosentinel_mobile.shared.generated.resources.report_photo_ready
import geosentinel_mobile.shared.generated.resources.report_photo_remove
import geosentinel_mobile.shared.generated.resources.report_photo_remove_body
import geosentinel_mobile.shared.generated.resources.report_photo_remove_cancel
import geosentinel_mobile.shared.generated.resources.report_photo_remove_confirm
import geosentinel_mobile.shared.generated.resources.report_photo_remove_title
import geosentinel_mobile.shared.generated.resources.report_photo_removing
import geosentinel_mobile.shared.generated.resources.report_photo_rejected
import geosentinel_mobile.shared.generated.resources.report_photo_retry
import geosentinel_mobile.shared.generated.resources.report_photo_take
import geosentinel_mobile.shared.generated.resources.report_photo_uploading
import org.jetbrains.compose.resources.stringResource

// Un riquadro da 88 dp: abbastanza per riconoscere la foto, ben sopra i 48 dp di bersaglio
private val TileSize = 88.dp
private val TileCorner = 16.dp
private val TileShape = RoundedCornerShape(TileCorner)
// Come i campi dei form (§2): card a 20 dp
private val BarShape = RoundedCornerShape(20.dp)

/**
 * Le foto della segnalazione come miniature, ognuna con un badge nell'angolo (anello in invio, spunta verde caricata,
 * ✕ non accettata, freccia per riprovare), sotto una barra per aggiungerne: fotocamera (se c'è) e galleria. Lo stato è anche detto a parole agli screen reader.
 */
@Composable
internal fun HazardPhotos(
    photos: List<PhotoUpload>,
    onTakePhoto: (() -> Unit)?,
    onPickPhoto: () -> Unit,
    onRetry: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    // la foto da togliere, in attesa della conferma (una caricata si cancella anche sul server)
    var confirmRemove by rememberSaveable { mutableStateOf<Int?>(null) }
    confirmRemove?.let { photoId ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(Res.string.report_photo_remove_title)) },
            text = { Text(stringResource(Res.string.report_photo_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    onRemove(photoId)
                }) { Text(stringResource(Res.string.report_photo_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(Res.string.report_photo_remove_cancel)) } },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Prima i comandi, poi le foto: la barra resta ferma mentre le miniature si aggiungono sotto
        if (photos.size < MAX_HAZARD_PHOTOS) AddBar(onTakePhoto, onPickPhoto)
        // Va a capo invece di scorrere: niente miniature tagliate sul bordo
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            photos.forEachIndexed { index, photo ->
                PhotoTile(
                    index + 1,
                    photo,
                    onRetry = { onRetry(photo.id) },
                    // una foto non partita sparisce e basta; una caricata o non accettata è sul server: si chiede prima
                    onRemove = { if (photo.mediaId == null) onRemove(photo.id) else confirmRemove = photo.id },
                )
            }
        }
    }
}

// Una sola barra come i campi dei form (card, filo di bordo), divisa a metà: fotocamera | galleria.
// Senza fotocamera (simulatore) resta solo la galleria, a tutta larghezza.
@Composable
private fun AddBar(onTakePhoto: (() -> Unit)?, onPickPhoto: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(BarShape)
            .background(colors.surfaceContainerLowest)
            .border(1.dp, colors.outlineVariant, BarShape),
    ) {
        if (onTakePhoto != null) {
            AddAction(CameraIcon, stringResource(Res.string.report_photo_take), onTakePhoto)
            VerticalDivider(modifier = Modifier.height(24.dp), color = colors.outlineVariant)
        }
        AddAction(GalleryIcon, stringResource(Res.string.report_photo_pick), onPickPhoto)
    }
}

@Composable
private fun RowScope.AddAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PhotoTile(number: Int, photo: PhotoUpload, onRetry: () -> Unit, onRemove: () -> Unit) {
    val description = stringResource(
        when (photo.status) {
            PhotoStatus.UPLOADING -> Res.string.report_photo_uploading
            PhotoStatus.READY -> Res.string.report_photo_ready
            PhotoStatus.REJECTED -> Res.string.report_photo_rejected
            PhotoStatus.FAILED -> Res.string.report_photo_failed
            PhotoStatus.REMOVING -> Res.string.report_photo_removing
        },
        number,
    )
    val removeLabel = stringResource(Res.string.report_photo_remove, number)
    // La foto si "accende" quando arriva: attenuata mentre parte, spenta se non è passata
    val alpha by animateFloatAsState(
        targetValue = when (photo.status) {
            PhotoStatus.READY -> 1f
            PhotoStatus.UPLOADING -> 0.6f
            PhotoStatus.REJECTED, PhotoStatus.FAILED, PhotoStatus.REMOVING -> 0.4f
        },
        animationSpec = tween(300),
    )
    val retryLabel = stringResource(Res.string.report_photo_retry)
    Box(
        modifier = Modifier
            .size(TileSize)
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                // tutta la miniatura è il pulsante "Riprova": più facile da centrare con i guanti
                if (photo.status == PhotoStatus.FAILED) Modifier.clickable(onClickLabel = retryLabel, role = Role.Button, onClick = onRetry) else Modifier,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        if (photo.jpeg != null) {
            AsyncImage(
                model = photo.jpeg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
            )
        }
        StatusBadge(photo.status, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        // "Togli" nell'angolo opposto al badge: bersaglio da 48 dp, cerchio visibile da 26 (una in invio non si toglie)
        if (photo.status != PhotoStatus.UPLOADING && photo.status != PhotoStatus.REMOVING) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .clickable(onClickLabel = removeLabel, role = Role.Button, onClick = onRemove)
                    .semantics { contentDescription = removeLabel },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// Il badge cambia con una piccola molla: la spunta "scatta" dentro quando la foto è arrivata
@Composable
private fun StatusBadge(status: PhotoStatus, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) + fadeIn())
                .togetherWith(fadeOut(tween(90)))
        },
        modifier = modifier,
    ) { current ->
        val (background, content) = when (current) {
            PhotoStatus.UPLOADING -> colors.surfaceContainerLowest to colors.primary
            PhotoStatus.READY -> colors.primary to colors.onPrimary
            PhotoStatus.REJECTED, PhotoStatus.FAILED -> colors.error to colors.onError
            PhotoStatus.REMOVING -> colors.surfaceContainerLowest to colors.onSurfaceVariant
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                // anello del colore della card: stacca il badge da qualsiasi foto
                .border(2.dp, colors.surfaceContainerLowest, CircleShape)
                .padding(2.dp)
                .background(background, CircleShape),
        ) {
            when (current) {
                PhotoStatus.UPLOADING, PhotoStatus.REMOVING -> CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = content)
                PhotoStatus.READY -> Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
                PhotoStatus.REJECTED -> Icon(Icons.Filled.Close, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                PhotoStatus.FAILED -> Icon(Icons.Filled.Refresh, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// Tracciati di Material Symbols (Apache 2.0): material-icons-core non ha fotocamera né immagine.
// Il nero è solo il riempimento di base: Icon lo sostituisce con il tint
private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
    .build()

private val CameraIcon = icon(
    "Camera",
    "M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4zM9 2 7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z",
)

private val GalleryIcon = icon(
    "Gallery",
    "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z",
)
