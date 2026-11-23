// Implementazione Android del selettore di foto e dello scatto con la fotocamera (vedi PhotoPicker.kt in commonMain).
// Usa le Activity Result API: il Photo Picker per la galleria e l'intent della fotocamera di sistema, che scrive la
// foto in un file temporaneo esposto tramite FileProvider.
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

/** Registra il Photo Picker di sistema (solo immagini) e restituisce la funzione che lo apre. */
@Composable
actual fun rememberPhotoPicker(onPicked: (PickedImage) -> Unit): () -> Unit {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val callback by rememberUpdatedState(onPicked)
    // Sui dispositivi sotto Android 13 che non hanno il Photo Picker aggiornato, il contratto ripiega da solo su
    // ACTION_OPEN_DOCUMENT, che anch'esso non richiede permessi.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) callback(PickedImage(uri, resolver))
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/**
 * Autorità del FileProvider dichiarato nel modulo androidApp (res/xml/photo_paths.xml). L'app della fotocamera scrive la
 * foto nel file che questo provider espone.
 */
private fun photosAuthority(context: Context) = "${context.packageName}.photos"

/**
 * Registra lo scatto con la fotocamera di sistema. Ogni scatto usa un file nuovo nella cache dell'app; restituisce null
 * se il dispositivo non ha una fotocamera.
 */
@Composable
actual fun rememberCameraCapture(onCaptured: (PickedImage) -> Unit): (() -> Unit)? {
    val context = LocalContext.current.applicationContext
    val callback by rememberUpdatedState(onCaptured)
    val dir = remember(context) { File(context.cacheDir, "camera") }
    // Pulizia dei file rimasti da un processo chiuso tra lo scatto e la ricodifica: sono originali con gli EXIF (GPS
    // compreso) e non vanno conservati. Si cancellano solo quelli più vecchi di StaleAfterMillis.
    LaunchedEffect(dir) {
        withContext(Dispatchers.IO) {
            val old = System.currentTimeMillis() - StaleAfterMillis
            dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
        }
    }
    // Il percorso del file in attesa è salvato con rememberSaveable perché, con poca memoria, Android può distruggere
    // l'Activity mentre la fotocamera è aperta: al ritorno il risultato deve ritrovare il file giusto.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pending?.let(::File)
        pending = null
        if (file == null) return@rememberLauncherForActivityResult
        // Se la foto è stata salvata il file lo cancella ImageEncoder dopo la ricodifica; se lo scatto è stato
        // annullato si cancella subito qui.
        if (saved) callback(PickedImage(uriFor(context, file), context.contentResolver, tempFile = file)) else file.delete()
    }
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return null
    return {
        // Un file nuovo per ogni scatto. La cartella non si svuota qui, perché si rischierebbe di cancellare una foto
        // precedente che è ancora in ricodifica.
        dir.mkdirs()
        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        pending = file.path
        try {
            launcher.launch(uriFor(context, file))
        } catch (_: ActivityNotFoundException) {
            // Nessuna app di fotocamera installata: lo scatto semplicemente non parte.
            pending = null
        }
    }
}

/** Uri content:// del file, leggibile dall'app della fotocamera tramite il FileProvider. */
private fun uriFor(context: Context, file: File) = FileProvider.getUriForFile(context, photosAuthority(context), file)

/** Età oltre la quale un file della fotocamera è considerato abbandonato: un'ora, più di qualunque scatto in corso. */
private const val StaleAfterMillis = 60 * 60 * 1000L
