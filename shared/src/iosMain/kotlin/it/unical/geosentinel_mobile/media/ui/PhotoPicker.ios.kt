// Implementazione iOS del selettore di foto e dello scatto con la fotocamera (vedi PhotoPicker.kt in commonMain).
// Presenta i controller di sistema (PHPickerViewController e UIImagePickerController) sopra la schermata Compose e
// riceve il risultato tramite delegate Objective-C.
package it.unical.geosentinel_mobile.media.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import it.unical.geosentinel_mobile.media.data.PickedImage
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Crea il delegate del picker e restituisce la funzione che presenta PHPickerViewController (una sola immagine). */
@Composable
actual fun rememberPhotoPicker(onPicked: (PickedImage) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    // Il picker mantiene il delegate con un riferimento debole: se nessuno lo tenesse in vita verrebbe liberato prima
    // del risultato. Per questo il delegate è conservato con remember nella composizione.
    val delegate = remember { PickerDelegate { callback(it) } }
    return {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration)
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

/** Delegate di PHPickerViewController: chiude il picker e carica i byte della foto scelta. */
private class PickerDelegate(private val onPicked: (PickedImage) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    /** Chiamato quando l'utente ha scelto (o annullato): chiude il picker e, se c'è una foto, ne carica i byte. */
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        // Il tipo "public.image" comprende anche HEIC: la conversione in JPEG avviene dopo, in ImageEncoder. Il
        // caricamento è asincrono e termina su un thread qualunque, quindi il risultato torna sul main thread.
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data: NSData?, _ ->
            if (data != null) dispatch_async(dispatch_get_main_queue()) { onPicked(PickedImage(data)) }
        }
    }
}

/** Restituisce la funzione che apre la fotocamera, oppure null se il dispositivo non ne ha una. */
@Composable
actual fun rememberCameraCapture(onCaptured: (PickedImage) -> Unit): (() -> Unit)? {
    val callback by rememberUpdatedState(onCaptured)
    val delegate = remember { CameraDelegate { callback(it) } }
    // Sul simulatore la fotocamera non esiste: si restituisce null e la UI non mostra il pulsante.
    if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) return null
    return {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

/** Delegate di UIImagePickerController: chiude la fotocamera e converte la foto scattata in byte. */
private class CameraDelegate(private val onCaptured: (PickedImage) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    /** Chiamato dopo lo scatto: chiude la fotocamera e consegna la foto come JPEG. */
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        // Conversione in JPEG alla massima qualità solo per avere dei byte: dimensioni e qualità finali le decide
        // ImageEncoder.
        val data = UIImageJPEGRepresentation(image, 1.0) ?: return
        onCaptured(PickedImage(data))
    }

    /** L'utente ha annullato lo scatto: si chiude la fotocamera senza risultato. */
    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

/**
 * Restituisce il view controller in cima alla pila, da cui presentare il picker. Compose vive nel root view controller,
 * che potrebbe avere già un altro controller presentato sopra.
 */
private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
