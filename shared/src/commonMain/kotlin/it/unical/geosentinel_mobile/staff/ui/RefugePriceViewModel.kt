// ViewModel della schermata in cui il gestore di un rifugio (o un amministratore) imposta o toglie il prezzo a
// persona per notte.
package it.unical.geosentinel_mobile.staff.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unical.geosentinel_mobile.core.network.safely
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.centsToEuroInput
import it.unical.geosentinel_mobile.core.ui.parseEuroToCents
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stato del form del prezzo. */
data class RefugePriceUiState(
    /** Ultimo prezzo confermato dal server, in centesimi; null se il rifugio non ha un prezzo. */
    val priceCents: Int?,
    /** Testo del campo, in euro ("35,00"); vuoto vuol dire nessun prezzo. */
    val draft: String,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    val saved: Boolean = false,
) {
    /** Vero se il campo contiene un prezzo valido oppure è vuoto (togli il prezzo). */
    val valid: Boolean get() = draft.isBlank() || parseEuroToCents(draft) != null
    /** Il prezzo da inviare: null per il campo vuoto. Si legge solo se [valid]. */
    val parsed: Int? get() = if (draft.isBlank()) null else parseEuroToCents(draft)
    /** Si può salvare un valore valido, diverso da quello attuale, senza un invio in corso. */
    val canSave: Boolean get() = valid && parsed != priceCents && !busy
}

/**
 * ViewModel del prezzo del rifugio [refugeId]. Il testo si converte in centesimi una sola volta, con
 * [parseEuroToCents]; i permessi li decide il server (403 per chi non è il gestore né un ADMIN).
 *
 * @param initialDraft prezzo attuale già nel formato del campo ("" se il rifugio non ha un prezzo).
 */
class RefugePriceViewModel(
    private val refugeId: String,
    initialDraft: String,
    private val staff: StaffRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RefugePriceUiState(priceCents = parseEuroToCents(initialDraft), draft = initialDraft))
    val state: StateFlow<RefugePriceUiState> = _state.asStateFlow()

    /** Aggiorna il testo del campo tenendo solo cifre e un separatore decimale. */
    fun onDraftChange(text: String) = _state.update {
        it.copy(draft = text.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(MaxChars), saved = false, error = null)
    }

    /** Invia il nuovo prezzo (o la sua rimozione); se riesce, il campo prende il valore confermato dal server. */
    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val price = current.parsed

        _state.update { it.copy(busy = true, error = null, saved = false) }
        viewModelScope.launch {
            val result = safely { staff.updateRefugePrice(refugeId, price) }
            _state.update { state ->
                result.fold(
                    onSuccess = { refuge ->
                        state.copy(busy = false, priceCents = refuge.pricePerNightCents, draft = centsToEuroInput(refuge.pricePerNightCents), saved = true)
                    },
                    onFailure = { failure -> state.copy(busy = false, error = failure.toUiMessage()) },
                )
            }
        }
    }
}

// "1000,00" è il testo più lungo di un prezzo valido.
private const val MaxChars = 7
