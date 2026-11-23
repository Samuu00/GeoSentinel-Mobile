// Stato della schermata "Le mie segnalazioni" e testi degli stati di una segnalazione.
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

/**
 * Stato immutabile della schermata "Le mie segnalazioni", prodotto da MyHazardsViewModel. La lista si allunga una
 * pagina alla volta, quando si arriva in fondo.
 *
 * @property items le segnalazioni caricate finora.
 * @property loading caricamento della prima pagina (all'apertura o con "Riprova" dallo stato d'errore).
 * @property loadingMore caricamento della pagina successiva, in fondo alla lista.
 * @property refreshing ricaricamento da capo avviato trascinando la lista verso il basso.
 * @property endReached true quando il server non ha altre pagine.
 * @property error messaggio d'errore da mostrare, oppure null.
 */
data class MyHazardsUiState(
    val items: List<HazardResponse> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: UiMessage? = null,
) {
    /** Rappresentazione per i log: riporta solo il numero di segnalazioni, mai descrizioni o posizioni (dati personali). */
    override fun toString() =
        "MyHazardsUiState(items=${items.size}, loading=$loading, loadingMore=$loadingMore, refreshing=$refreshing, endReached=$endReached, error=$error)"
}

/**
 * Restituisce il testo dello stato di una segnalazione. Lo stato si scrive sempre in parole e non solo con un colore,
 * per l'accessibilità. Un valore sconosciuto inviato dal server arriva come `unknown_default_open_api` e ha un testo
 * proprio, così l'app non va in crash.
 */
fun HazardResponse.Status.label(): StringResource = when (this) {
    HazardResponse.Status.SUBMITTED -> Res.string.my_hazards_status_submitted
    HazardResponse.Status.VERIFIED -> Res.string.my_hazards_status_verified
    HazardResponse.Status.REJECTED -> Res.string.my_hazards_status_rejected
    HazardResponse.Status.RESOLVED -> Res.string.my_hazards_status_resolved
    HazardResponse.Status.unknown_default_open_api -> Res.string.my_hazards_status_unknown
}
