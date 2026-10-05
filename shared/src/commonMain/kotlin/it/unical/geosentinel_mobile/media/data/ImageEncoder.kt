package it.unical.geosentinel_mobile.media.data

/** La foto scelta con il picker di sistema: l'Uri (con il ContentResolver) su Android, i byte originali su iOS. */
expect class PickedImage

/**
 * Ricodifica in JPEG (A13): lato lungo al massimo [maxSidePx], orientamento applicato ai pixel. Il JPEG nuovo non
 * porta gli EXIF dell'originale, quindi nemmeno la posizione GPS; l'HEIC dell'iPhone diventa JPEG.
 * Lancia un'eccezione se l'immagine non si legge.
 */
expect class ImageEncoder() {
    suspend fun toJpeg(source: PickedImage, maxSidePx: Int = 2560, quality: Int = 85): ByteArray
}
