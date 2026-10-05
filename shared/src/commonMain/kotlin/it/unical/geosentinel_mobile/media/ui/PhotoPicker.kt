package it.unical.geosentinel_mobile.media.ui

import androidx.compose.runtime.Composable
import it.unical.geosentinel_mobile.media.data.PickedImage

/**
 * Picker di sistema per una foto (A13): Photo Picker su Android, `PHPickerViewController` su iOS. Nessuno dei due
 * chiede il permesso sulla galleria. Restituisce la funzione che lo apre; [onPicked] non arriva se l'utente annulla.
 */
@Composable
expect fun rememberPhotoPicker(onPicked: (PickedImage) -> Unit): () -> Unit

/**
 * Foto scattata al momento con la fotocamera di sistema (intent `ACTION_IMAGE_CAPTURE` su Android, che non chiede il
 * permesso perché l'app non dichiara `CAMERA`; `UIImagePickerController` su iOS). null se il dispositivo non ha una
 * fotocamera (es. il simulatore iPhone): la UI non mostra il pulsante.
 */
@Composable
expect fun rememberCameraCapture(onCaptured: (PickedImage) -> Unit): (() -> Unit)?
