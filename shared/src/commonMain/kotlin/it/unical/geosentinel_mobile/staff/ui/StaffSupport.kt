package it.unical.geosentinel_mobile.staff.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_generic
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource

// Una risposta fuori contratto (ContractViolationException) non deve far cadere l'app: diventa un errore come gli altri
internal suspend fun <T> safely(block: suspend () -> Result<T>): Result<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Il messaggio per l'utente. [onRuleViolation] sostituisce il generico "Operazione non consentita" del 422
 * `business-rule` con una frase che dice cosa è successo in questa schermata: la regola resta solo sul server.
 */
internal fun Throwable.toUiMessage(onRuleViolation: StringResource? = null): UiMessage {
    val error = (this as? ApiException)?.error ?: return UiMessage(Res.string.error_generic)
    if (onRuleViolation != null && error is ApiError.BusinessRule && error.slug == "business-rule") return UiMessage(onRuleViolation)
    return error.uiMessage()
}
