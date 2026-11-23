// Dichiarazioni comuni per scegliere una foto dalla galleria o scattarla con la fotocamera. Le implementazioni usano i
// componenti di sistema di Android e iOS, così l'app non deve chiedere permessi sulla galleria o sulla fotocamera.
package it.unical.geosentinel_mobile.media.ui

import androidx.compose.runtime.Composable
import it.unical.geosentinel_mobile.media.data.PickedImage

/**
 * Prepara il selettore di foto di sistema: il Photo Picker su Android, `PHPickerViewController` su iOS. Nessuno dei due
 * richiede il permesso di accesso alla galleria. Restituisce la funzione da chiamare per aprirlo; [onPicked] viene
 * invocata con la foto scelta e non viene chiamata se l'utente annulla.
 */
@Composable
expect fun rememberPhotoPicker(onPicked: (PickedImage) -> Unit): () -> Unit

/**
 * Prepara lo scatto di una foto con la fotocamera di sistema: l'intent `ACTION_IMAGE_CAPTURE` su Android (che non
 * richiede il permesso perché l'app non dichiara `CAMERA`) e `UIImagePickerController` su iOS. Restituisce null se il
 * dispositivo non ha una fotocamera (per esempio il simulatore iPhone): in quel caso la UI nasconde il pulsante.
 */
@Composable
expect fun rememberCameraCapture(onCaptured: (PickedImage) -> Unit): (() -> Unit)?
