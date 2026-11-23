// Messaggi per l'utente prodotti dai ViewModel.
// I ViewModel non risolvono le stringhe localizzate: espongono un riferimento alla risorsa e la UI lo
// trasforma in testo. In questo modo i ViewModel restano testabili sulla JVM senza le risorse di Compose.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_rate_limited_seconds
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.messageResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Un messaggio da mostrare all'utente, espresso come risorsa di testo.
 *
 * @property text la stringa in `strings.xml`.
 * @property arg un eventuale argomento da inserire nel testo (per esempio un numero di secondi).
 */
data class UiMessage(val text: StringResource, val arg: Any? = null)

/** Risolve il messaggio nella lingua corrente; va chiamata dalla UI. */
@Composable
fun UiMessage.resolve(): String = if (arg == null) stringResource(text) else stringResource(text, arg)

/**
 * Trasforma un errore delle API nel messaggio per l'utente. Per il limite di richieste superato, se il server
 * ha indicato quanto aspettare, il messaggio riporta i secondi; negli altri casi si usa il testo generico
 * associato all'errore.
 */
fun ApiError.uiMessage(): UiMessage =
    if (this is ApiError.RateLimited && retryAfterSeconds != null) {
        UiMessage(Res.string.error_rate_limited_seconds, retryAfterSeconds)
    } else {
        UiMessage(messageResource())
    }
