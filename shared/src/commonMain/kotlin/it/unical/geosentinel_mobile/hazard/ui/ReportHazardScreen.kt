// Schermata "Segnala un pericolo": il form con tipo, gravità, posizione sulla mappa e descrizione e, dopo l'invio, la
// schermata "Segnalazione inviata" da cui allegare le foto. Contiene anche componenti di form riusati altrove
// (Field, Choices, LocationPicker), per esempio nella creazione di un POI.
package it.unical.geosentinel_mobile.hazard.ui

import geosentinel_mobile.shared.generated.resources.report_leave_uploading_title
import geosentinel_mobile.shared.generated.resources.report_leave_uploading_body
import geosentinel_mobile.shared.generated.resources.report_leave_stay
import geosentinel_mobile.shared.generated.resources.report_leave_anyway
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import geosentinel_mobile.shared.generated.resources.report_photos
import geosentinel_mobile.shared.generated.resources.report_photos_hint
import it.unical.geosentinel_mobile.media.data.ImageEncoder
import it.unical.geosentinel_mobile.media.ui.rememberCameraCapture
import it.unical.geosentinel_mobile.media.ui.rememberPhotoPicker
import org.koin.compose.koinInject
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.hazard_fallen_tree
import geosentinel_mobile.shared.generated.resources.hazard_flooding
import geosentinel_mobile.shared.generated.resources.hazard_landslide
import geosentinel_mobile.shared.generated.resources.hazard_other
import geosentinel_mobile.shared.generated.resources.hazard_snow_ice
import geosentinel_mobile.shared.generated.resources.hazard_trail_erosion
import geosentinel_mobile.shared.generated.resources.hazard_wildlife
import geosentinel_mobile.shared.generated.resources.report_body
import geosentinel_mobile.shared.generated.resources.report_description
import geosentinel_mobile.shared.generated.resources.report_description_count
import geosentinel_mobile.shared.generated.resources.report_locating
import geosentinel_mobile.shared.generated.resources.report_location
import geosentinel_mobile.shared.generated.resources.report_location_denied
import geosentinel_mobile.shared.generated.resources.report_location_hint
import geosentinel_mobile.shared.generated.resources.report_location_unavailable
import geosentinel_mobile.shared.generated.resources.report_send
import geosentinel_mobile.shared.generated.resources.report_sent_body
import geosentinel_mobile.shared.generated.resources.report_sent_done
import geosentinel_mobile.shared.generated.resources.report_sent_title
import geosentinel_mobile.shared.generated.resources.report_severity
import geosentinel_mobile.shared.generated.resources.report_severity_critical
import geosentinel_mobile.shared.generated.resources.report_severity_high
import geosentinel_mobile.shared.generated.resources.report_severity_low
import geosentinel_mobile.shared.generated.resources.report_severity_medium
import geosentinel_mobile.shared.generated.resources.report_title
import geosentinel_mobile.shared.generated.resources.report_type
import geosentinel_mobile.shared.generated.resources.report_use_my_location
import it.unical.geosentinel_mobile.api.model.CreateHazardRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.location.dragged
import it.unical.geosentinel_mobile.core.location.toGeoPoint
import it.unical.geosentinel_mobile.core.location.toPosition
import it.unical.geosentinel_mobile.core.ui.FormErrorBanner
import it.unical.geosentinel_mobile.core.ui.FormScaffold
import it.unical.geosentinel_mobile.core.ui.FormTextField
import it.unical.geosentinel_mobile.core.ui.PrimaryPillButton
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.resolve
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyOnRed
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import it.unical.geosentinel_mobile.trail.ui.AttributionButton
import it.unical.geosentinel_mobile.trail.ui.recentFix
import it.unical.geosentinel_mobile.trail.ui.rememberMapStyle
import it.unical.geosentinel_mobile.trail.ui.rememberSavedMapType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import kotlin.math.log2
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import it.unical.geosentinel_mobile.core.ui.AppIcons

/** Zoom minimo dopo "Usa la mia posizione": abbastanza vicino da poter spostare il segnaposto sul punto esatto. */
private const val PickZoom = 16.0

/** Zoom minimo raggiungibile con il pizzico nella mappa del form (tutta la regione). */
private const val MinZoom = 5.0

/** Zoom massimo raggiungibile con il pizzico (il singolo tornante). */
private const val MaxZoom = 19.0

/** Tempo massimo di attesa di una posizione dopo "Usa la mia posizione", come per "La mia posizione" sulla mappa. */
private val FixWait = 15.seconds

/**
 * Schermata di segnalazione di un pericolo: tipo, gravità, posizione (il centro della mappa, che si sposta con le dita
 * o con "Usa la mia posizione") e descrizione facoltativa. È una schermata "spinta", con il proprio pulsante Indietro.
 * Dopo l'invio riuscito la stessa schermata mostra "Segnalazione inviata" con la sezione delle foto.
 *
 * @param trailId sentiero da cui si è partiti, oppure null.
 * @param start punto iniziale della mappa: il centro della mappa principale o l'inizio del sentiero.
 * @param zoom zoom iniziale della mappa.
 * @param onBack chiude la schermata.
 */
@Composable
fun ReportHazardScreen(trailId: String?, start: GeoPoint, zoom: Double, onBack: () -> Unit) {
    val viewModel = koinViewModel<ReportHazardViewModel> { parametersOf(trailId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.sent) {
        val encoder = koinInject<ImageEncoder>()
        val pickPhoto = rememberPhotoPicker { picked -> viewModel.addPhoto { encoder.toJpeg(picked) } }
        val takePhoto = rememberCameraCapture { captured -> viewModel.addPhoto { encoder.toJpeg(captured) } }
        // Se ci sono foto in caricamento si chiede conferma prima di uscire (con "Fatto", con Indietro o con il gesto
        // di sistema): l'upload continua comunque nello scope dell'app, ma fuori da qui l'utente non vedrebbe se va a
        // buon fine.
        val uploading = state.photos.any { it.status == PhotoStatus.UPLOADING }
        var confirmLeave by rememberSaveable { mutableStateOf(false) }
        val leave = { if (uploading) confirmLeave = true else onBack() }
        // BackHandler è deprecato a favore di NavigationEventHandler (libreria navigationevent-compose), su cui in
        // Compose Multiplatform 1.12 si appoggia internamente. Si potrà passare a quello quando la sua API sarà
        // stabile sia su Android sia su iOS.
        @Suppress("DEPRECATION")
        @OptIn(ExperimentalComposeUiApi::class)
        BackHandler(enabled = uploading) { confirmLeave = true }
        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                title = { Text(stringResource(Res.string.report_leave_uploading_title)) },
                text = { Text(stringResource(Res.string.report_leave_uploading_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLeave = false
                        onBack()
                    }) { Text(stringResource(Res.string.report_leave_anyway)) }
                },
                dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(Res.string.report_leave_stay)) } },
            )
        }
        FormScaffold(
            onBack = leave,
            title = Res.string.report_sent_title,
            body = Res.string.report_sent_body,
            error = state.photoError?.resolve(),
            actions = {
                PrimaryPillButton(text = stringResource(Res.string.report_sent_done), onClick = leave, loading = uploading)
            },
        ) {
            Field(Res.string.report_photos, error = null) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        stringResource(Res.string.report_photos_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HazardPhotos(state.photos, onTakePhoto = takePhoto, onPickPhoto = pickPhoto, onRetry = viewModel::retryPhoto, onRemove = viewModel::removePhoto)
                }
            }
        }
        return
    }

    FormScaffold(
        onBack = onBack,
        title = Res.string.report_title,
        body = Res.string.report_body,
        error = state.error?.resolve(),
        actions = {
            PrimaryPillButton(text = stringResource(Res.string.report_send), onClick = viewModel::submit, loading = state.submitting)
        },
    ) {
        Field(Res.string.report_type, state.fieldErrors[HazardField.TYPE]) {
            Choices(HazardTypes, selected = state.type, label = { it.label() }, onSelect = viewModel::onTypeChange)
        }
        Field(Res.string.report_severity, state.fieldErrors[HazardField.SEVERITY]) {
            Choices(
                HazardSeverities,
                selected = state.severity,
                label = { it.label() },
                onSelect = viewModel::onSeverityChange,
                // Il rosso è riservato alla gravità CRITICAL, come nel resto dell'app.
                emergency = { it == CreateHazardRequest.Severity.CRITICAL },
            )
        }
        Field(Res.string.report_location, state.fieldErrors[HazardField.LOCATION]) {
            LocationPicker(start, zoom, onLocationChange = viewModel::onLocationChange)
        }
        FormTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            label = stringResource(Res.string.report_description),
            error = state.fieldErrors[HazardField.DESCRIPTION]?.resolve(),
            supporting = stringResource(Res.string.report_description_count, state.description.trim().length, HAZARD_DESCRIPTION_MAX),
            singleLine = false,
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/**
 * Sezione di un form: titolo (marcato come intestazione per l'accessibilità), contenuto e, sotto, l'eventuale errore del
 * campo, nel colore `error` del tema e annunciato dai lettori di schermo.
 */
@Composable
internal fun Field(title: StringResource, error: UiMessage?, content: @Composable () -> Unit) {
    Column {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        content()
        if (error != null) {
            Text(
                error.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * Scelta singola tra poche voci, mostrata come capsule (FilterChip) che vanno a capo. Il bersaglio toccabile di 48 dp
 * è garantito da Material anche se la capsula è alta 40 dp. Le voci per cui [emergency] restituisce true, quando
 * selezionate, sono colorate di rosso.
 */
@Composable
internal fun <T> Choices(
    options: List<T>,
    selected: T?,
    label: (T) -> StringResource,
    onSelect: (T) -> Unit,
    emergency: (T) -> Boolean = { false },
) {
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val red = emergency(option)
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(label(option)), style = MaterialTheme.typography.bodyMedium) },
                colors = if (red) {
                    FilterChipDefaults.filterChipColors(selectedContainerColor = EmergencyRed, selectedLabelColor = EmergencyOnRed)
                } else {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
                modifier = Modifier.heightIn(min = 40.dp),
            )
        }
    }
}

/**
 * Mappa con un segnaposto fisso al centro, usata per scegliere un punto (posizione di un pericolo o di un POI). La
 * posizione scelta è il centro della mappa quando si ferma e viene comunicata con [onLocationChange]. "Usa la mia
 * posizione" chiede il permesso di localizzazione solo in quel momento (l'utente ha appena chiesto la propria
 * posizione, quindi il tocco stesso fa da spiegazione) e sposta la mappa sulla posizione, se è recente. [hint] è il
 * testo di aiuto sotto la mappa.
 */
@Composable
internal fun LocationPicker(
    start: GeoPoint,
    zoom: Double,
    onLocationChange: (GeoPoint) -> Unit,
    hint: StringResource = Res.string.report_location_hint,
) {
    val location = rememberLocationState()
    val style = rememberMapStyle(rememberSavedMapType())
    val mapState = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(target = start.toPosition(), zoom = zoom),
    ) {
        LocationIndicatorLayer(id = "posizione", locationState = location)
    }
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<StringResource?>(null) }

    // Ogni volta che la mappa si ferma il suo centro diventa la posizione scelta, anche all'apertura prima di qualsiasi
    // gesto.
    LaunchedEffect(mapState) {
        snapshotFlow { if (mapState.isCameraMoving) null else mapState.cameraPosition.target }
            .filterNotNull()
            .collect { onLocationChange(it.toGeoPoint()) }
    }
    LaunchedEffect(locating, location.lastLocation) {
        if (!locating) return@LaunchedEffect
        val fix = location.recentFix() ?: return@LaunchedEffect
        // L'animazione parte nello scope della schermata e non in quello dell'effetto: locating = false cambia la
        // chiave dell'effetto, che verrebbe annullato insieme all'animazione.
        scope.launch { mapState.animateCamera(CameraUpdate(target = fix.toPosition(), zoom = max(mapState.cameraPosition.zoom, PickZoom))) }
        locating = false
    }
    LaunchedEffect(locating) {
        if (!locating) return@LaunchedEffect
        delay(FixWait)
        locating = false
        note = Res.string.report_location_unavailable
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(MaterialTheme.shapes.medium),
    ) {
        MaplibreMap(
            state = mapState,
            // I gesti nativi della mappa sono disattivati e li gestisce la Box qui sotto, in Compose: dentro una pagina
            // che scorre i gesti nativi venivano catturati dallo scroll, che spostava la pagina invece della mappa.
            interactions = MapInteractions.None,
            overlay = {
                Box(
                    Modifier.fillMaxSize().pointerInput(mapState) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            val metersPerDp = mapState.viewport?.metersPerDpAtTarget ?: return@detectTransformGestures
                            val camera = mapState.cameraPosition
                            mapState.setCameraPosition(
                                camera.copy(
                                    target = camera.target.dragged(pan.x.toDp().value.toDouble(), pan.y.toDp().value.toDouble(), metersPerDp),
                                    zoom = (camera.zoom + log2(zoomChange.toDouble())).coerceIn(MinZoom, MaxZoom),
                                ),
                            )
                        }
                    },
                )
                // Solo il pulsante dell'attribuzione, obbligatoria per le licenze delle mappe; lo zoom si fa con le dita.
                AttributionButton(mapState, Modifier.align(Alignment.BottomStart).padding(8.dp))
            },
        )
        // Segnaposto con la punta sul centro della mappa (da qui lo spostamento verso l'alto di metà altezza). È
        // decorativo: il testo sotto spiega cosa fare.
        Icon(
            AppIcons.MapPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center).size(44.dp).offset(y = (-22).dp),
        )
    }
    Text(
        stringResource(hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    TextButton(
        onClick = {
            note = null
            when (val permission = location.permission) {
                is LocationPermission.Granted -> locating = true
                // Permesso negato in modo definitivo: il sistema non mostrerebbe più il dialogo, quindi si spiega
                // all'utente con un messaggio.
                is LocationPermission.NotGranted if permission.canRequest == false -> note = Res.string.report_location_denied
                else -> {
                    locating = true
                    location.requestPermission()
                }
            }
        },
        enabled = !locating,
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(
            stringResource(if (locating) Res.string.report_locating else Res.string.report_use_my_location),
            fontWeight = FontWeight.SemiBold,
        )
    }
    note?.let {
        Text(
            stringResource(it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Tipi selezionabili: tutti quelli del contratto, tranne il caso dei valori sconosciuti, che l'app non invia mai. */
private val HazardTypes = CreateHazardRequest.Type.entries - CreateHazardRequest.Type.unknown_default_open_api
/** Gravità selezionabili, con lo stesso criterio dei tipi. */
private val HazardSeverities = CreateHazardRequest.Severity.entries - CreateHazardRequest.Severity.unknown_default_open_api

/** Testo di un tipo di pericolo nel form. */
private fun CreateHazardRequest.Type.label(): StringResource = when (this) {
    CreateHazardRequest.Type.LANDSLIDE -> Res.string.hazard_landslide
    CreateHazardRequest.Type.FALLEN_TREE -> Res.string.hazard_fallen_tree
    CreateHazardRequest.Type.FLOODING -> Res.string.hazard_flooding
    CreateHazardRequest.Type.TRAIL_EROSION -> Res.string.hazard_trail_erosion
    CreateHazardRequest.Type.SNOW_ICE -> Res.string.hazard_snow_ice
    CreateHazardRequest.Type.WILDLIFE -> Res.string.hazard_wildlife
    CreateHazardRequest.Type.OTHER, CreateHazardRequest.Type.unknown_default_open_api -> Res.string.hazard_other
}

/** Testo di una gravità nel form. */
private fun CreateHazardRequest.Severity.label(): StringResource = when (this) {
    CreateHazardRequest.Severity.LOW -> Res.string.report_severity_low
    CreateHazardRequest.Severity.MEDIUM, CreateHazardRequest.Severity.unknown_default_open_api -> Res.string.report_severity_medium
    CreateHazardRequest.Severity.HIGH -> Res.string.report_severity_high
    CreateHazardRequest.Severity.CRITICAL -> Res.string.report_severity_critical
}
