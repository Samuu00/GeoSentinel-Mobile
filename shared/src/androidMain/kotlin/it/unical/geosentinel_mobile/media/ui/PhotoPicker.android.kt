package it.unical.geosentinel_mobile.media.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    // Salvato: con poca memoria Android può chiudere l'Activity mentre la fotocamera è aperta
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pending?.toUri()
        pending = null
        if (saved && uri != null) callback(PickedImage(uri, context.contentResolver))
    }
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return null
    return {
        // Una foto alla volta: quella di prima è già stata ricodificata e caricata
        val dir = File(context.cacheDir, "camera").apply { deleteRecursively(); mkdirs() }
        val uri = FileProvider.getUriForFile(context, photosAuthority(context), File(dir, "photo-${System.currentTimeMillis()}.jpg"))
        pending = uri.toString()
        try {
            launcher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pending = null // nessuna app fotocamera
        }
    }
}
