// Schermata per creare un nuovo punto di interesse, scegliendo tipo, nome e posizione sulla mappa.
package it.unical.geosentinel_mobile.staff.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.poi_create_body
import geosentinel_mobile.shared.generated.resources.poi_create_location
import geosentinel_mobile.shared.generated.resources.poi_create_name
import geosentinel_mobile.shared.generated.resources.poi_create_name_count
import geosentinel_mobile.shared.generated.resources.poi_create_save
import geosentinel_mobile.shared.generated.resources.poi_create_title
import geosentinel_mobile.shared.generated.resources.poi_create_type
import geosentinel_mobile.shared.generated.resources.poi_danger_zone
import geosentinel_mobile.shared.generated.resources.poi_equipment_point
import geosentinel_mobile.shared.generated.resources.poi_information
import geosentinel_mobile.shared.generated.resources.poi_location_hint
import geosentinel_mobile.shared.generated.resources.poi_shelter
import geosentinel_mobile.shared.generated.resources.poi_viewpoint
import geosentinel_mobile.shared.generated.resources.poi_water_source
import it.unical.geosentinel_mobile.api.model.CreatePoiRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.hazard.ui.Choices
import it.unical.geosentinel_mobile.hazard.ui.Field
import it.unical.geosentinel_mobile.hazard.ui.LocationPicker
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Form del nuovo POI: tipo, nome e posizione, che è il centro della mappa come per le segnalazioni. La mappa parte da
 * [start] allo zoom [zoom]. Con [trailId] il POI si lega a quel sentiero, senza è un punto libero. Quando il server lo
 * ha salvato si chiama [onCreated].
 */
@Composable
fun PoiCreateScreen(trailId: String?, start: GeoPoint, zoom: Double, onBack: () -> Unit, onCreated: () -> Unit) {
    val viewModel = koinViewModel<PoiCreateViewModel>(key = trailId ?: "free") { parametersOf(trailId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.created) {
        if (state.created) onCreated()
    }

    FormScaffold(
        onBack = onBack,
        title = Res.string.poi_create_title,
        body = Res.string.poi_create_body,
        error = state.error?.resolve(),
        actions = {
            // Senza un nome valido il pulsante è disattivato: il contatore sotto il campo spiega il perché.
            PrimaryPillButton(
                text = stringResource(Res.string.poi_create_save),
                onClick = viewModel::save,
                enabled = state.canSave,
                loading = state.busy,
            )
        },
    ) {
        Field(title = Res.string.poi_create_type, error = null) {
            Choices(PoiTypes, selected = state.type, label = { it.label() }, onSelect = viewModel::onTypeChange)
        }
        Field(title = Res.string.poi_create_location, error = null) {
            LocationPicker(
                start = start,
                zoom = zoom,
                hint = Res.string.poi_location_hint,
                onLocationChange = viewModel::onLocationPicked,
            )
        }
        FormTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = stringResource(Res.string.poi_create_name),
            supporting = stringResource(Res.string.poi_create_name_count, state.name.trim().length, POI_NAME_MAX, POI_NAME_MIN),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

// Tipi di POI proposti nel form: quelli del contratto, tranne il caso riservato ai valori sconosciuti, che l'app non
// invia mai. Nessun tipo usa il rosso: anche la zona pericolosa è un luogo, non un'emergenza.
private val PoiTypes = CreatePoiRequest.Type.entries - CreatePoiRequest.Type.unknown_default_open_api

/** Etichetta del tipo di POI da mostrare nel form. */
private fun CreatePoiRequest.Type.label(): StringResource = when (this) {
    CreatePoiRequest.Type.SHELTER -> Res.string.poi_shelter
    CreatePoiRequest.Type.WATER_SOURCE -> Res.string.poi_water_source
    CreatePoiRequest.Type.VIEWPOINT -> Res.string.poi_viewpoint
    CreatePoiRequest.Type.DANGER_ZONE -> Res.string.poi_danger_zone
    CreatePoiRequest.Type.EQUIPMENT_POINT -> Res.string.poi_equipment_point
    CreatePoiRequest.Type.INFORMATION, CreatePoiRequest.Type.unknown_default_open_api -> Res.string.poi_information
}
