// Schermata per impostare o togliere il prezzo a persona per notte di un rifugio.
package it.unical.geosentinel_mobile.staff.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.staff_price_body
import geosentinel_mobile.shared.generated.resources.staff_price_invalid
import geosentinel_mobile.shared.generated.resources.staff_price_label
import geosentinel_mobile.shared.generated.resources.staff_price_save
import geosentinel_mobile.shared.generated.resources.staff_price_saved
import geosentinel_mobile.shared.generated.resources.staff_price_title
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Form del prezzo del rifugio [refugeId], aperto dal dettaglio del rifugio quando l'utente ne è il gestore o è un
 * amministratore. [priceDraft] è il prezzo attuale nel formato del campo ("" se non c'è).
 */
@Composable
fun RefugePriceScreen(refugeId: String, priceDraft: String, onBack: () -> Unit) {
    val viewModel = koinViewModel<RefugePriceViewModel>(key = "price-$refugeId") { parametersOf(refugeId, priceDraft) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    FormScaffold(
        onBack = onBack,
        title = Res.string.staff_price_title,
        body = Res.string.staff_price_body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(
                text = stringResource(Res.string.staff_price_save),
                onClick = viewModel::save,
                enabled = state.canSave,
                loading = state.busy,
            )
        },
    ) {
        FormTextField(
            value = state.draft,
            onValueChange = viewModel::onDraftChange,
            label = stringResource(Res.string.staff_price_label),
            error = if (state.valid) null else stringResource(Res.string.staff_price_invalid),
            supporting = if (state.saved) stringResource(Res.string.staff_price_saved) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}
