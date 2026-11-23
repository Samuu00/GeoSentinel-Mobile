// Sezione "Foto" del form di segnalazione di un pericolo: la barra per scattare o scegliere una foto e le miniature
// delle foto aggiunte, ognuna con lo stato del proprio upload.
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
import it.unical.geosentinel_mobile.core.ui.AppIcons

/** Lato di una miniatura: abbastanza per riconoscere la foto e ben sopra i 48 dp minimi di un bersaglio toccabile. */
private val TileSize = 88.dp

/** Raggio degli angoli delle miniature. */
private val TileCorner = 16.dp

/** Forma delle miniature. */
private val TileShape = RoundedCornerShape(TileCorner)

/** Forma della barra "Scatta | Galleria", con gli stessi angoli da 20 dp dei campi dei form. */
private val BarShape = RoundedCornerShape(20.dp)

/**
 * Mostra le foto della segnalazione come miniature, ognuna con un badge nell'angolo che ne indica lo stato: anello
 * durante l'invio, spunta quando è caricata, croce se il server non l'ha accettata, freccia per riprovare dopo un
 * errore. Sopra c'è la barra per aggiungerne altre, con fotocamera (se presente) e galleria, nascosta quando si è
 * raggiunto MAX_HAZARD_PHOTOS. Lo stato di ogni foto è comunicato a parole anche ai lettori di schermo.
 *
 * [onTakePhoto] è null se non c'è la fotocamera; [onRetry] e [onRemove] ricevono l'id locale della foto.
 */
@Composable
internal fun HazardPhotos(
    photos: List<PhotoUpload>,
    onTakePhoto: (() -> Unit)?,
    onPickPhoto: () -> Unit,
    onRetry: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    // Id della foto da rimuovere, in attesa di conferma nel dialogo: una foto già caricata viene cancellata anche sul
    // server, quindi si chiede prima all'utente.
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
        // Prima i comandi e poi le foto: così la barra resta ferma mentre le miniature si aggiungono sotto.
        if (photos.size < MAX_HAZARD_PHOTOS) AddBar(onTakePhoto, onPickPhoto)
        // FlowRow va a capo invece di scorrere in orizzontale, così nessuna miniatura resta tagliata sul bordo.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            photos.forEachIndexed { index, photo ->
                PhotoTile(
                    index + 1,
                    photo,
                    onRetry = { onRetry(photo.id) },
                    // Una foto mai arrivata al server (mediaId null) si toglie subito; una caricata o non accettata
                    // esiste sul server, quindi prima si chiede conferma.
                    onRemove = { if (photo.mediaId == null) onRemove(photo.id) else confirmRemove = photo.id },
                )
            }
        }
    }
}

/**
 * Barra con lo stesso aspetto dei campi dei form (card con un bordo sottile), divisa a metà tra fotocamera e
 * galleria. Senza fotocamera (per esempio sul simulatore) resta solo la galleria, a tutta larghezza.
 */
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

/** Metà della barra: icona e testo, tutta toccabile. L'icona è decorativa perché il testo dice già l'azione. */
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

/**
 * Miniatura di una foto, numerata con [number] per i lettori di schermo. Mostra l'anteprima locale del JPEG, il badge
 * di stato e, quando la foto non è in invio o in rimozione, il pulsante per toglierla. Se l'invio è fallito tutta la
 * miniatura diventa il pulsante "Riprova".
 */
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
    // L'opacità segue lo stato: attenuata durante l'invio, piena quando è caricata, molto attenuata se non è passata.
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
                // Tutta la miniatura fa da pulsante "Riprova": è più facile da centrare, anche con i guanti.
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
        // Pulsante "Togli" nell'angolo opposto al badge: l'area toccabile è di 48 dp, il cerchio visibile di 26 dp.
        // Una foto in invio o in rimozione non si può togliere.
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
                    Icon(AppIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * Badge circolare con lo stato della foto. Il passaggio da uno stato all'altro usa una piccola animazione a molla, così
 * la spunta "scatta" dentro quando la foto è arrivata. Le icone sono decorative: lo stato è già nella descrizione
 * della miniatura.
 */
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
                // Anello del colore della card, che stacca il badge da qualsiasi foto sottostante.
                .border(2.dp, colors.surfaceContainerLowest, CircleShape)
                .padding(2.dp)
                .background(background, CircleShape),
        ) {
            when (current) {
                PhotoStatus.UPLOADING, PhotoStatus.REMOVING -> CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = content)
                PhotoStatus.READY -> Icon(AppIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
                PhotoStatus.REJECTED -> Icon(AppIcons.Close, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                PhotoStatus.FAILED -> Icon(AppIcons.Refresh, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/**
 * Crea un'icona vettoriale 24x24 da un tracciato SVG. I tracciati vengono da Material Symbols (licenza Apache 2.0)
 * perché material-icons-core non include né la fotocamera né l'immagine. Il nero è solo il riempimento di base: il
 * componente Icon lo sostituisce con il proprio tint.
 */
private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
    .build()

/** Icona della fotocamera. */
private val CameraIcon = icon(
    "Camera",
    "M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4zM9 2 7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z",
)

/** Icona della galleria (immagine). */
private val GalleryIcon = icon(
    "Gallery",
    "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z",
)
