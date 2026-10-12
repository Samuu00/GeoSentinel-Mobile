package it.unical.geosentinel_mobile.staff.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.staff_capacity_body
import geosentinel_mobile.shared.generated.resources.staff_capacity_label
import geosentinel_mobile.shared.generated.resources.staff_capacity_save
import geosentinel_mobile.shared.generated.resources.staff_capacity_saved
import geosentinel_mobile.shared.generated.resources.staff_capacity_title
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// Capacità del rifugio, aperta dal dettaglio del rifugio quando il rifugio è del gestore o l'utente è ADMIN.
@Composable
fun RefugeCapacityScreen(refugeId: String, capacity: Int, onBack: () -> Unit) {
    val viewModel = koinViewModel<RefugeCapacityViewModel>(key = refugeId) { parametersOf(refugeId, capacity) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    FormScaffold(
        onBack = onBack,
        title = Res.string.staff_capacity_title,
        body = Res.string.staff_capacity_body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(
                text = stringResource(Res.string.staff_capacity_save),
                onClick = viewModel::save,
                enabled = state.canSave,
                loading = state.busy,
            )
        },
    ) {
        FormTextField(
            value = state.draft,
            onValueChange = viewModel::onDraftChange,
            label = stringResource(Res.string.staff_capacity_label),
            supporting = if (state.saved) stringResource(Res.string.staff_capacity_saved) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}