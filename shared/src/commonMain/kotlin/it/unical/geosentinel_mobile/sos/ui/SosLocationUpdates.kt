// Invio periodico della posizione mentre c'è un SOS attivo, così il soccorso segue gli spostamenti
// dell'escursionista.
package it.unical.geosentinel_mobile.sos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import it.unical.geosentinel_mobile.core.location.current
import kotlinx.coroutines.delay
import org.maplibre.compose.location.rememberDefaultLocationProvider
import kotlin.time.Duration.Companion.seconds

/** Intervallo tra due invii della posizione. */
private val LocationEvery = 60.seconds

/**
 * Con un'allerta attiva invia la posizione ogni 60 secondi, solo con l'app in primo piano (la posizione in background
 * non è supportata), e subito a ogni ritorno in primo piano, perché `repeatOnLifecycle` riparte da capo.
 *
 * Si trova in `App` e non nella schermata SOS, così funziona anche mentre l'utente guarda la mappa. Il provider della
 * posizione esiste solo finché questa funzione è nella composizione: in questo modo il GPS lavora solo durante un SOS.
 */
@Composable
fun SosLocationUpdates(controller: SosController) {
    val provider = rememberDefaultLocationProvider()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(provider, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // Senza permesso o senza una posizione disponibile si salta il giro: il soccorso ha comunque l'ultima
                // posizione inviata.
                provider.current()?.let { controller.updateLocation(it) }
                delay(LocationEvery)
            }
        }
    }
}
