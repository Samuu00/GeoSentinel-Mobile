// Funzioni di supporto condivise dalle schermate del personale.
package it.unical.geosentinel_mobile.staff.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import org.jetbrains.compose.resources.StringResource

/**
 * Messaggio per l'utente a partire da un errore. Se è indicato, [onRuleViolation] sostituisce il generico "Operazione
 * non consentita" del 422 `business-rule` con una frase che spiega cosa è successo in quella schermata; la regola
 * vera e propria resta solo sul server.
 */
internal fun Throwable.toUiMessage(onRuleViolation: StringResource? = null): UiMessage {
    val error = (this as? ApiException)?.error ?: return UiMessage(Res.string.error_generic)
    if (onRuleViolation != null && error is ApiError.BusinessRule && error.slug == "business-rule") return UiMessage(onRuleViolation)
    return error.uiMessage()
}
