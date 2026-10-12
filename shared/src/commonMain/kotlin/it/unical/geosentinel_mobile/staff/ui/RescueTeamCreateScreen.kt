package it.unical.geosentinel_mobile.staff.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.poi_location_hint
import geosentinel_mobile.shared.generated.resources.staff_team_base
import geosentinel_mobile.shared.generated.resources.staff_team_body
import geosentinel_mobile.shared.generated.resources.staff_team_call_sign
import geosentinel_mobile.shared.generated.resources.staff_team_contact
import geosentinel_mobile.shared.generated.resources.staff_team_name
import geosentinel_mobile.shared.generated.resources.staff_team_save
import geosentinel_mobile.shared.generated.resources.staff_team_title
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.hazard.ui.Field
import it.unical.geosentinel_mobile.hazard.ui.LocationPicker
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Nuova squadra di soccorso (M8.3), solo ADMIN. La base è il centro della mappa, come per i POI. */
@Composable
fun RescueTeamCreateScreen(start: GeoPoint, zoom: Double, onBack: () -> Unit, onCreated: () -> Unit) {
    val viewModel = koinViewModel<RescueTeamCreateViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.created) {
        if (state.created) onCreated()
    }

    FormScaffold(
        onBack = onBack,
        title = Res.string.staff_team_title,
        body = Res.string.staff_team_body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(
                text = stringResource(Res.string.staff_team_save),
                onClick = viewModel::save,
                enabled = state.canSave,
                loading = state.busy,
            )
        },
    ) {
        FormTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = stringResource(Res.string.staff_team_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        FormTextField(
            value = state.callSign,
            onValueChange = viewModel::onCallSignChange,
            label = stringResource(Res.string.staff_team_call_sign),
        )
        FormTextField(
            value = state.contactNumber,
            onValueChange = viewModel::onContactChange,
            label = stringResource(Res.string.staff_team_contact),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Field(title = Res.string.staff_team_base, error = null) {
            LocationPicker(
                start = start,
                zoom = zoom,
                hint = Res.string.poi_location_hint,
                onLocationChange = viewModel::onLocationPicked,
            )
        }
    }
}