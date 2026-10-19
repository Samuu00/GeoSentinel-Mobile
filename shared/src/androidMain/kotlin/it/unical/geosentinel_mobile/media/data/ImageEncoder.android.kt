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

// Il ContentResolver è quello dell'applicazione (non tiene in vita l'Activity).
// tempFile: la foto scattata con la fotocamera (originale con gli EXIF, anche il GPS), da cancellare dopo la ricodifica
actual class PickedImage(val uri: Uri, val resolver: ContentResolver, val tempFile: File? = null)

actual class ImageEncoder actual constructor() {

    actual suspend fun toJpeg(source: PickedImage, maxSidePx: Int, quality: Int, squareCrop: Boolean): ByteArray = withContext(Dispatchers.IO) {
        try {
            encode(source, maxSidePx, quality, squareCrop)
        } finally {
            // l'originale della fotocamera non resta in cache: il JPEG ricodificato (senza EXIF) è l'unica copia
            source.tempFile?.delete()
        }
    }

    private fun encode(source: PickedImage, maxSidePx: Int, quality: Int, squareCrop: Boolean): ByteArray {
        fun <T> read(block: (InputStream) -> T): T =
            (source.resolver.openInputStream(source.uri) ?: throw IOException("foto non leggibile")).use(block)

        // Solo le dimensioni: con inJustDecodeBounds decodeStream restituisce sempre null, si guarda outWidth
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("formato non supportato")

        // android.media.ExifInterface legge da uno stream da API 24 (minSdk): niente dipendenza in più
        val orientation = read { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }

        // Campionamento a potenze di 2 finché il lato che conta (il lungo, o il corto se si ritaglia il quadrato) resta
        // almeno maxSidePx: meno memoria, poi la scala fine
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
        // Il quadrato al centro: con la rotazione resta lo stesso. Con la matrice identità e senza ritaglio
        // createBitmap restituisce lo stesso bitmap
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
