package it.unical.geosentinel_mobile.media.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min

// I byte originali dal picker (anche HEIC)
actual class PickedImage(val data: NSData)

actual class ImageEncoder actual constructor() {

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun toJpeg(source: PickedImage, maxSidePx: Int, quality: Int, squareCrop: Boolean): ByteArray = withContext(Dispatchers.Default) {
        val original = UIImage.imageWithData(source.data) ?: error("foto non leggibile")
        val (width, height) = original.size.useContents { width to height }
        if (width <= 0.0 || height <= 0.0) error("foto non leggibile")
        val side = if (squareCrop) min(width, height) else max(width, height)
        val ratio = min(1.0, maxSidePx / side)
        // quadrato: la tela è il lato corto e l'immagine intera ci si disegna centrata, il resto resta fuori
        val canvasW = if (squareCrop) side * ratio else width * ratio
        val canvasH = if (squareCrop) side * ratio else height * ratio

        // Si ridisegna sempre: applica l'orientamento ai pixel e lascia fuori tutti i metadati dell'originale
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(canvasW, canvasH), true, 1.0)
        val redrawn = try {
            original.drawInRect(CGRectMake((canvasW - width * ratio) / 2, (canvasH - height * ratio) / 2, width * ratio, height * ratio))
            UIGraphicsGetImageFromCurrentImageContext()
        } finally {
            UIGraphicsEndImageContext()
        } ?: error("ridimensionamento non riuscito")

        val jpeg: NSData = UIImageJPEGRepresentation(redrawn, quality.coerceIn(0, 100) / 100.0)
            ?: error("conversione in JPEG non riuscita")
        val bytes = ByteArray(jpeg.length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
        bytes
    }
}
