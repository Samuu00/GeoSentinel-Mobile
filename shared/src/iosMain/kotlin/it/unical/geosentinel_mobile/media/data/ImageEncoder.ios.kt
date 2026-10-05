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
    actual suspend fun toJpeg(source: PickedImage, maxSidePx: Int, quality: Int): ByteArray = withContext(Dispatchers.Default) {
        val original = UIImage.imageWithData(source.data) ?: error("foto non leggibile")
        val (width, height) = original.size.useContents { width to height }
        if (width <= 0.0 || height <= 0.0) error("foto non leggibile")
        val ratio = min(1.0, maxSidePx / max(width, height))

        // Si ridisegna sempre: applica l'orientamento ai pixel e lascia fuori tutti i metadati dell'originale
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width * ratio, height * ratio), true, 1.0)
        val redrawn = try {
            original.drawInRect(CGRectMake(0.0, 0.0, width * ratio, height * ratio))
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
