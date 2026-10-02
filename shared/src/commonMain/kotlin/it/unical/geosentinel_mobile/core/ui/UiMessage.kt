package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_rate_limited_seconds
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.messageResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Un messaggio per l'utente come risorsa: il ViewModel non risolve le stringhe (così si testa sulla JVM),
// lo fa la UI con resolve()
data class UiMessage(val text: StringResource, val arg: Any? = null)

@Composable
fun UiMessage.resolve(): String = if (arg == null) stringResource(text) else stringResource(text, arg)

fun ApiError.uiMessage(): UiMessage =
    if (this is ApiError.RateLimited && retryAfterSeconds != null) {
        UiMessage(Res.string.error_rate_limited_seconds, retryAfterSeconds)
    } else {
        UiMessage(messageResource())
    }
