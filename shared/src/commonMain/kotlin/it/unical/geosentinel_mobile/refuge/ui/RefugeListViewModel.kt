// ViewModel della scheda Rifugi: carica i rifugi vicini a un punto e, riga per riga, la foto di copertina da
// mostrare come miniatura.
package it.unical.geosentinel_mobile.refuge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.media.data.MediaRepository
import kotlinx.coroutines.flow.update
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.refuge.data.RefugeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Stato della lista della scheda Rifugi: in caricamento, caricata o fallita con un messaggio. */
sealed interface RefugeList {
    /** Caricamento in corso. */
    data object Loading : RefugeList
    /**
     * Lista caricata. [from] è il punto da cui si misurano le distanze; [nearMe] è vero se quel punto è la posizione
     * dell'utente e non l'area di partenza della mappa.
     */
    data class Loaded(val refuges: List<RefugeResponse>, val from: GeoPoint, val nearMe: Boolean) : RefugeList {
        override fun toString() = "Loaded(refuges=${refuges.size}, nearMe=$nearMe)"
    }
    /** Caricamento fallito, con il messaggio da mostrare. */
    data class Failed(val message: UiMessage) : RefugeList
}

/**
 * ViewModel della scheda Rifugi: i rifugi entro 50 km da un punto, dal più vicino. Il punto lo sceglie la schermata,
 * che ha accesso alla posizione: quella dell'utente se il permesso è già concesso, altrimenti l'area di partenza
 * della mappa. [media] è facoltativo (null nei test) e serve solo per le copertine.
 */
class RefugeListViewModel(private val refuges: RefugeRepository, private val media: MediaRepository? = null) : ViewModel() {

    private val _state = MutableStateFlow<RefugeList>(RefugeList.Loading)
    val state: StateFlow<RefugeList> = _state.asStateFlow()

    // Prima foto di ogni rifugio, usata come miniatura della riga (valore null: rifugio senza foto). La lista del
    // server non la include, quindi si chiede a parte solo per le righe che entrano in vista, una volta per rifugio.
    private val _covers = MutableStateFlow<Map<String, MediaResponse?>>(emptyMap())
    val covers: StateFlow<Map<String, MediaResponse?>> = _covers.asStateFlow()
    private val coversRequested = mutableSetOf<String>()

    /** Chiede la copertina del rifugio [refugeId] se non è già stata chiesta. */
    fun loadCover(refugeId: String) {
        val media = media ?: return
        if (!coversRequested.add(refugeId)) return
        viewModelScope.launch {
            media.photosOfRefuge(refugeId)
                .onSuccess { list -> _covers.update { it + (refugeId to list.firstOrNull()) } }
                // Un errore lascia la riga senza foto; il rifugio si toglie dai richiesti, così si riprova quando la
                // riga torna in vista.
                .onFailure { coversRequested.remove(refugeId) }
        }
    }

    // Ultima richiesta fatta, per poterla ripetere con retry(), e il job in corso.
    private var last: Pair<GeoPoint, Boolean>? = null
    private var job: Job? = null

    /** Carica i rifugi attorno a [center]; [nearMe] dice se il punto è la posizione dell'utente. */
    fun load(center: GeoPoint, nearMe: Boolean) {
        last = center to nearMe
        // Una posizione nuova annulla la richiesta ancora in corso.
        job?.cancel()
        _state.value = RefugeList.Loading
        job = viewModelScope.launch {
            _state.value = refuges.nearby(center).fold(
                { RefugeList.Loaded(it, center, nearMe) },
                { RefugeList.Failed(it.toUiMessage()) },
            )
        }
    }

    /** Ripete l'ultimo caricamento, dopo un errore. */
    fun retry() {
        val (center, nearMe) = last ?: return
        load(center, nearMe)
    }
}
