// Contratto comune per preparare una foto prima dell'upload. Le implementazioni (Android con Bitmap ed ExifInterface,
// iOS con UIImage) stanno nei rispettivi source set e vengono usate dal form di segnalazione dei pericoli e dalla
// foto profilo.
package it.unical.geosentinel_mobile.media.data

/**
 * Foto scelta con il picker o scattata con la fotocamera di sistema. Su Android contiene l'Uri da leggere con il
 * ContentResolver, su iOS i byte originali dell'immagine.
 */
expect class PickedImage

/**
 * Ricodifica una foto in JPEG prima di caricarla sul server.
 *
 * Il lato lungo viene ridotto al massimo a [maxSidePx] pixel e l'orientamento indicato dagli EXIF viene applicato
 * direttamente ai pixel. Il JPEG prodotto non porta con sé gli EXIF dell'originale, quindi nemmeno la posizione GPS
 * dello scatto: è una scelta di privacy. Le foto HEIC dell'iPhone diventano JPEG.
 * Con [squareCrop] (usato per la foto profilo) si ritaglia prima il quadrato centrale e [maxSidePx] vale per il suo
 * lato. [quality] è la qualità JPEG da 0 a 100.
 */
expect class ImageEncoder() {
    /** Restituisce i byte del JPEG; lancia un'eccezione se l'immagine non si può leggere o decodificare. */
    suspend fun toJpeg(source: PickedImage, maxSidePx: Int = 2560, quality: Int = 85, squareCrop: Boolean = false): ByteArray
}
