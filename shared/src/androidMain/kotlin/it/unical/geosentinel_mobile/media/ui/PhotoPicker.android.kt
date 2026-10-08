package it.unical.geosentinel_mobile.media.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import it.unical.geosentinel_mobile.media.data.PickedImage

@Composable
actual fun rememberPhotoPicker(onPicked: (PickedImage) -> Unit): () -> Unit {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val callback by rememberUpdatedState(onPicked)
    // Sotto Android 13 senza il Photo Picker aggiornato ripiega su ACTION_OPEN_DOCUMENT, sempre senza permessi
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) callback(PickedImage(uri, resolver))
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

// Autorità del FileProvider dichiarato in androidApp (res/xml/photo_paths.xml): la fotocamera scrive lì la foto
private fun photosAuthority(context: Context) = "${context.packageName}.photos"

@Composable
actual fun rememberCameraCapture(onCaptured: (PickedImage) -> Unit): (() -> Unit)? {
    val context = LocalContext.current.applicationContext
    val callback by rememberUpdatedState(onCaptured)
    val dir = remember(context) { File(context.cacheDir, "camera") }
    // Avanzi di un processo chiuso tra lo scatto e la ricodifica: originali con gli EXIF (anche il GPS) da non tenere
    LaunchedEffect(dir) {
        withContext(Dispatchers.IO) {
            val old = System.currentTimeMillis() - StaleAfterMillis
            dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
        }
    }
    // Salvato (il percorso del file): con poca memoria Android può chiudere l'Activity mentre la fotocamera è aperta
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pending?.let(::File)
        pending = null
        if (file == null) return@rememberLauncherForActivityResult
        // l'encoder cancella il file dopo la ricodifica; annullato o non salvato si cancella qui
        if (saved) callback(PickedImage(uriFor(context, file), context.contentResolver, tempFile = file)) else file.delete()
    }
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return null
    return {
        // un file per scatto: niente svuotamento della cartella, che cancellerebbe una foto ancora in ricodifica
        dir.mkdirs()
        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        pending = file.path
        try {
            launcher.launch(uriFor(context, file))
        } catch (_: ActivityNotFoundException) {
            pending = null // nessuna app fotocamera
        }
    }
}

private fun uriFor(context: Context, file: File) = FileProvider.getUriForFile(context, photosAuthority(context), file)

// un'ora: più di qualunque scatto in corso
private const val StaleAfterMillis = 60 * 60 * 1000L
