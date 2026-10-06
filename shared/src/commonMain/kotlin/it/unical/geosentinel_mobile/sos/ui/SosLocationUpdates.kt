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

private val LocationEvery = 60.seconds

/**
 * M6.2: con un'allerta attiva manda la posizione ogni 60 s, solo con l'app in primo piano (la posizione in background
 * non c'è in questa versione, §7), e subito a ogni ritorno in primo piano: `repeatOnLifecycle` riparte da capo.
 * Sta in `App`, non nella schermata SOS, così vale anche mentre l'utente guarda la mappa. Il provider esiste solo
 * finché questa funzione è nella composizione: il GPS lavora solo con un SOS attivo (M9.2).
 */
@Composable
fun SosLocationUpdates(controller: SosController) {
    val provider = rememberDefaultLocationProvider()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(provider, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // senza permesso o senza fix si salta il giro: il soccorso ha comunque l'ultima posizione inviata
                provider.current()?.let { controller.updateLocation(it) }
                delay(LocationEvery)
            }
        }
    }
}
