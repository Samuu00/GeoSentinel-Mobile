package it.unical.geosentinel_mobile.hazard.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.my_hazards_status_rejected
import geosentinel_mobile.shared.generated.resources.my_hazards_status_resolved
import geosentinel_mobile.shared.generated.resources.my_hazards_status_submitted
import geosentinel_mobile.shared.generated.resources.my_hazards_status_unknown
import geosentinel_mobile.shared.generated.resources.my_hazards_status_verified
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.core.ui.UiMessage
import org.jetbrains.compose.resources.StringResource

/** "Le mie segnalazioni" (M4.3): la lista si allunga una pagina alla volta, in fondo. */
data class MyHazardsUiState(
    val items: List<HazardResponse> = emptyList(),
    // prima pagina (all'apertura e con "Riprova" dallo stato d'errore)
    val loading: Boolean = false,
    // pagina successiva, in fondo alla lista
    val loadingMore: Boolean = false,
    // trascinando in basso
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: UiMessage? = null,
) {
    // niente descrizioni né posizioni nei log
    override fun toString() =
        "MyHazardsUiState(items=${items.size}, loading=$loading, loadingMore=$loadingMore, refreshing=$refreshing, endReached=$endReached, error=$error)"
}

// Lo stato in parole, non solo con il colore (§2); un valore nuovo del server non fa crashare l'app
fun HazardResponse.Status.label(): StringResource = when (this) {
    HazardResponse.Status.SUBMITTED -> Res.string.my_hazards_status_submitted
    HazardResponse.Status.VERIFIED -> Res.string.my_hazards_status_verified
    HazardResponse.Status.REJECTED -> Res.string.my_hazards_status_rejected
    HazardResponse.Status.RESOLVED -> Res.string.my_hazards_status_resolved
    HazardResponse.Status.unknown_default_open_api -> Res.string.my_hazards_status_unknown
}
