// Implementazione Android della ricodifica delle foto in JPEG (vedi ImageEncoder in commonMain). Legge l'immagine
// dal ContentResolver, applica l'orientamento EXIF, ridimensiona ed eventualmente ritaglia il quadrato centrale, poi
// comprime in JPEG senza copiare i metadati dell'originale.
package it.unical.geosentinel_mobile.media.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Foto scelta o scattata su Android.
 *
 * @property uri indirizzo dell'immagine da leggere.
 * @property resolver il ContentResolver dell'applicazione (non dell'Activity, così non la tiene in vita).
 * @property tempFile per le foto scattate con la fotocamera, il file temporaneo con l'originale (EXIF e GPS compresi),
 * da cancellare dopo la ricodifica; null per le foto prese dalla galleria.
 */
actual class PickedImage(val uri: Uri, val resolver: ContentResolver, val tempFile: File? = null)

/** Ricodificatore JPEG basato su BitmapFactory, Matrix ed ExifInterface. */
actual class ImageEncoder actual constructor() {

    /** Esegue la ricodifica su Dispatchers.IO e cancella sempre l'eventuale file temporaneo della fotocamera. */
    actual suspend fun toJpeg(source: PickedImage, maxSidePx: Int, quality: Int, squareCrop: Boolean): ByteArray = withContext(Dispatchers.IO) {
        try {
            encode(source, maxSidePx, quality, squareCrop)
        } finally {
            // L'originale della fotocamera non deve restare nella cache dell'app, anche se la ricodifica fallisce:
            // l'unica copia che rimane è il JPEG ricodificato, senza EXIF.
            source.tempFile?.delete()
        }
    }

    /**
     * Decodifica, ruota, scala, ritaglia e comprime la foto. Lancia IOException se lo stream non si apre o se il
     * formato non è decodificabile.
     */
    private fun encode(source: PickedImage, maxSidePx: Int, quality: Int, squareCrop: Boolean): ByteArray {
        // Apre un nuovo stream sulla foto e lo chiude dopo [block]; serve più volte perché ogni lettura consuma lo stream.
        // Lancia IOException se la foto non si può aprire.
        fun <T> read(block: (InputStream) -> T): T =
            (source.resolver.openInputStream(source.uri) ?: throw IOException("foto non leggibile")).use(block)

        // Prima lettura per le sole dimensioni: con inJustDecodeBounds decodeStream restituisce sempre null e le
        // misure si leggono da outWidth e outHeight.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("formato non supportato")

        // android.media.ExifInterface sa leggere da uno stream a partire da API 24, che è il minSdk dell'app, quindi
        // non serve la libreria androidx.exifinterface.
        val orientation = read { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }

        // Il decoder riduce l'immagine per potenze di 2 (inSampleSize) finché il lato di riferimento (il lungo, o il
        // corto se si ritaglia il quadrato) resta almeno maxSidePx. Così si occupa meno memoria e la scala esatta si
        // applica dopo con la matrice.
        val side = { w: Int, h: Int -> if (squareCrop) min(w, h) else max(w, h) }
        var sampleSize = 1
        while (side(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSidePx) sampleSize *= 2
        val decoded = read { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize }) }
            ?: throw IOException("foto non decodificabile")

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        val outSide = side(decoded.width, decoded.height)
        if (outSide > maxSidePx) {
            val scale = maxSidePx.toFloat() / outSide
            matrix.postScale(scale, scale)
        }
        // Ritaglio del quadrato centrale: è lo stesso prima e dopo la rotazione, quindi si calcola sul bitmap decodificato.
        // Con la matrice identità e senza ritaglio createBitmap restituisce lo stesso oggetto, per questo nel finally
        // si ricicla output solo se è diverso da decoded.
        val cropW = if (squareCrop) outSide else decoded.width
        val cropH = if (squareCrop) outSide else decoded.height
        val output = Bitmap.createBitmap(decoded, (decoded.width - cropW) / 2, (decoded.height - cropH) / 2, cropW, cropH, matrix, true)
        return try {
            ByteArrayOutputStream().use { stream ->
                output.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(0, 100), stream)
                stream.toByteArray()
            }
        } finally {
            if (output !== decoded) output.recycle()
            decoded.recycle()
        }
    }
}
