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

@Composable
actual fun rememberPhotoPicker(onPicked: (PickedImage) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    // Il picker tiene il delegate come riferimento debole: lo tiene in vita la composizione
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

private class PickerDelegate(private val onPicked: (PickedImage) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        // "public.image" copre anche HEIC: la conversione in JPEG la fa ImageEncoder
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data: NSData?, _ ->
            if (data != null) dispatch_async(dispatch_get_main_queue()) { onPicked(PickedImage(data)) }
        }
    }
}

@Composable
actual fun rememberCameraCapture(onCaptured: (PickedImage) -> Unit): (() -> Unit)? {
    val callback by rememberUpdatedState(onCaptured)
    val delegate = remember { CameraDelegate { callback(it) } }
    // Sul simulatore non c'è la fotocamera: niente pulsante
    if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) return null
    return {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class CameraDelegate(private val onCaptured: (PickedImage) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        // JPEG alla massima qualità: ridimensionamento e qualità finale li decide ImageEncoder
        val data = UIImageJPEGRepresentation(image, 1.0) ?: return
        onCaptured(PickedImage(data))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

// La schermata in cima: Compose vive nel root view controller, che può avere qualcosa sopra
private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
