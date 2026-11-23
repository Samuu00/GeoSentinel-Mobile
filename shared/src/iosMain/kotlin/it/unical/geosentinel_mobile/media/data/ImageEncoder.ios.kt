// Implementazione iOS della ricodifica delle foto in JPEG (vedi ImageEncoder in commonMain). Usa UIKit per
// ridisegnare l'immagine su una tela nuova, così l'orientamento viene applicato ai pixel e i metadati dell'originale
// (compresa la posizione GPS) non finiscono nel file caricato.
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

/** Foto scelta su iOS: i byte originali restituiti dal picker o dalla fotocamera, anche in formato HEIC. */
actual class PickedImage(val data: NSData)

/** Ricodificatore JPEG basato su UIImage e sul contesto grafico di UIKit. */
actual class ImageEncoder actual constructor() {

    /**
     * Esegue la ricodifica su Dispatchers.Default, fuori dal main thread. Lancia
     * IllegalStateException se la foto non si legge o se il ridisegno o la conversione non riescono.
     */
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun toJpeg(source: PickedImage, maxSidePx: Int, quality: Int, squareCrop: Boolean): ByteArray = withContext(Dispatchers.Default) {
        val original = UIImage.imageWithData(source.data) ?: error("foto non leggibile")
        val (width, height) = original.size.useContents { width to height }
        if (width <= 0.0 || height <= 0.0) error("foto non leggibile")
        val side = if (squareCrop) min(width, height) else max(width, height)
        val ratio = min(1.0, maxSidePx / side)
        // Per il ritaglio quadrato la tela ha come lato il lato corto: l'immagine intera ci viene disegnata centrata
        // e le parti che sporgono restano fuori.
        val canvasW = if (squareCrop) side * ratio else width * ratio
        val canvasH = if (squareCrop) side * ratio else height * ratio

        // Si ridisegna sempre, anche quando non serve ridimensionare: è così che l'orientamento viene applicato ai pixel
        // e che i metadati dell'originale restano fuori dal JPEG.
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(canvasW, canvasH), true, 1.0)
        val redrawn = try {
            original.drawInRect(CGRectMake((canvasW - width * ratio) / 2, (canvasH - height * ratio) / 2, width * ratio, height * ratio))
            UIGraphicsGetImageFromCurrentImageContext()
        } finally {
            UIGraphicsEndImageContext()
        } ?: error("ridimensionamento non riuscito")

        val jpeg: NSData = UIImageJPEGRepresentation(redrawn, quality.coerceIn(0, 100) / 100.0)
            ?: error("conversione in JPEG non riuscita")
        // Copia dei byte da NSData a un ByteArray di Kotlin.
        val bytes = ByteArray(jpeg.length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
        bytes
    }
}
