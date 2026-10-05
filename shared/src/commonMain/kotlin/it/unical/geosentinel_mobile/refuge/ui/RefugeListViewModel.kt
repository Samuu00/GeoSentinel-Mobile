package it.unical.geosentinel_mobile.refuge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.api.model.RefugeResponse
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.refuge.data.RefugeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** La lista della scheda Rifugi. */
sealed interface RefugeList {
    data object Loading : RefugeList
    // from: il punto da cui si misurano le distanze; nearMe: è la posizione dell'utente (non l'area di partenza)
    data class Loaded(val refuges: List<RefugeResponse>, val from: GeoPoint, val nearMe: Boolean) : RefugeList {
        override fun toString() = "Loaded(refuges=${refuges.size}, nearMe=$nearMe)"
    }
    data class Failed(val message: UiMessage) : RefugeList
}

/**
 * Scheda Rifugi (M5.1): i rifugi entro 50 km, dal più vicino. Il punto lo sceglie la schermata, che ha il provider della
 * posizione: la posizione dell'utente se c'è già il permesso, altrimenti l'area di partenza della mappa.
 */
class RefugeListViewModel(private val refuges: RefugeRepository) : ViewModel() {

    private val _state = MutableStateFlow<RefugeList>(RefugeList.Loading)
    val state: StateFlow<RefugeList> = _state.asStateFlow()

    private var last: Pair<GeoPoint, Boolean>? = null
    private var job: Job? = null

    fun load(center: GeoPoint, nearMe: Boolean) {
        last = center to nearMe
        // una posizione nuova vince su una richiesta in corso
        job?.cancel()
        _state.value = RefugeList.Loading
        job = viewModelScope.launch {
            _state.value = refuges.nearby(center).fold(
                { RefugeList.Loaded(it, center, nearMe) },
                { RefugeList.Failed(it.toUiMessage()) },
            )
        }
    }

    fun retry() {
        val (center, nearMe) = last ?: return
        load(center, nearMe)
    }
}
