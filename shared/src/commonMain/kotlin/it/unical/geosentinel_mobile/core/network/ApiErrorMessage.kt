package it.unical.geosentinel_mobile.core.network

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_account_managed
import geosentinel_mobile.shared.generated.resources.error_age_requirement_not_met
import geosentinel_mobile.shared.generated.resources.error_alert_already_taken
import geosentinel_mobile.shared.generated.resources.error_business_rule
import geosentinel_mobile.shared.generated.resources.error_capacity_exceeded
import geosentinel_mobile.shared.generated.resources.error_concurrent_modification
import geosentinel_mobile.shared.generated.resources.error_conflict
import geosentinel_mobile.shared.generated.resources.error_forbidden
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.error_idempotency_key_reused
import geosentinel_mobile.shared.generated.resources.error_invalid_reset_code
import geosentinel_mobile.shared.generated.resources.error_media_payload_too_large
import geosentinel_mobile.shared.generated.resources.error_media_quota_exceeded
import geosentinel_mobile.shared.generated.resources.error_media_unsupported_type
import geosentinel_mobile.shared.generated.resources.error_network
import geosentinel_mobile.shared.generated.resources.error_not_found
import geosentinel_mobile.shared.generated.resources.error_precondition_required
import geosentinel_mobile.shared.generated.resources.error_rate_limited
import geosentinel_mobile.shared.generated.resources.error_rate_limited_seconds
import geosentinel_mobile.shared.generated.resources.error_resource_busy
import geosentinel_mobile.shared.generated.resources.error_server
import geosentinel_mobile.shared.generated.resources.error_unauthorized
import geosentinel_mobile.shared.generated.resources.error_validation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Messaggio per l'utente, in italiano. Utilizzabile dal ViewModel (è suspend, non serve un Composable). */
suspend fun ApiError.userMessage(): String =
    if (this is ApiError.RateLimited && retryAfterSeconds != null) {
        getString(Res.string.error_rate_limited_seconds, retryAfterSeconds)
    } else {
        getString(messageResource())
    }

// Slug e status non si mostrano mai all'utente: uno slug sconosciuto ha il messaggio generico del suo ramo
internal fun ApiError.messageResource(): StringResource = when (this) {
    ApiError.Network -> Res.string.error_network
    is ApiError.Unauthorized -> Res.string.error_unauthorized
    is ApiError.Forbidden -> Res.string.error_forbidden
    is ApiError.NotFound -> Res.string.error_not_found
    is ApiError.Validation -> Res.string.error_validation
    is ApiError.RateLimited -> Res.string.error_rate_limited
    is ApiError.Conflict -> when (slug) {
        "capacity-exceeded" -> Res.string.error_capacity_exceeded
        "alert-already-taken" -> Res.string.error_alert_already_taken
        "concurrent-modification" -> Res.string.error_concurrent_modification
        "resource-busy" -> Res.string.error_resource_busy
        "account-managed" -> Res.string.error_account_managed
        else -> Res.string.error_conflict
    }
    is ApiError.BusinessRule -> when (slug) {
        "business-rule" -> Res.string.error_business_rule
        "idempotency-key-reused" -> Res.string.error_idempotency_key_reused
        "age-requirement-not-met" -> Res.string.error_age_requirement_not_met
        "media-quota-exceeded" -> Res.string.error_media_quota_exceeded
        "media-unsupported-type" -> Res.string.error_media_unsupported_type
        "media-payload-too-large" -> Res.string.error_media_payload_too_large
        "precondition-required" -> Res.string.error_precondition_required
        "invalid-reset-code" -> Res.string.error_invalid_reset_code
        else -> Res.string.error_generic
    }
    // internal-error e storage-unavailable: Server non porta lo slug, e un 503 può venire anche da un proxy
    is ApiError.Server -> Res.string.error_server
}
