package it.unical.geosentinel_mobile.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.auth_hide_password
import geosentinel_mobile.shared.generated.resources.auth_password
import geosentinel_mobile.shared.generated.resources.auth_show_password
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Controlli dei form, nello stile "foresta": campi come piccole card morbide sul fondo nebbia, pulsanti a capsula
// come la barra in basso e il pulsante SOS. Solo colori del tema.

/**
 * Campo di testo: superficie da card, bordo sottile che diventa verde pino (2 dp) col focus e prugna con un errore.
 * L'etichetta resta sempre visibile (sale sopra il testo), il messaggio sotto il campo spiega cosa correggere.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        when {
            error != null -> colors.error
            focused -> colors.primary
            else -> colors.outlineVariant
        },
        tween(150),
    )
    val shape = MaterialTheme.shapes.medium
    Column(modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            readOnly = readOnly,
            isError = error != null,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            trailingIcon = trailing,
            interactionSource = interactionSource,
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLowest,
                unfocusedContainerColor = colors.surfaceContainerLowest,
                errorContainerColor = colors.surfaceContainerLowest,
                // niente linea sotto: il bordo intero fa da indicatore
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedLabelColor = colors.primary,
                unfocusedLabelColor = colors.onSurfaceVariant,
                errorLabelColor = colors.error,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .border(if (focused || error != null) 2.dp else 1.dp, borderColor, shape),
        )
        val below = error ?: supporting
        if (below != null) {
            Text(
                text = below,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
            )
        }
    }
}

// Azione principale della schermata: capsula verde pino, piena larghezza
@Composable
fun PrimaryPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Errore del form: riquadro prugna (ruolo errorContainer, mai rosso), annunciato da TalkBack e VoiceOver
@Composable
fun FormErrorBanner(message: String?) {
    // durante la dissolvenza d'uscita resta il testo di prima, invece di un riquadro vuoto
    var shown by remember { mutableStateOf(message) }
    if (message != null) shown = message
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(150)) + expandVertically(tween(150)),
        exit = fadeOut(tween(100)) + shrinkVertically(tween(100)),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = shown.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

// Struttura dei form, secondo gli standard per mobile:
// - in alto la testata con il paesaggio (sotto la barra di stato) e il tondo Indietro (su iPhone non c'è quello di sistema);
// - al centro titolo, testo e campi, che scorrono;
// - in fondo, fissa, l'azione principale con il link alternativo sotto: sempre visibile e a portata di pollice.
//   Con la tastiera aperta sale sopra la tastiera: lo Scaffold di App applica safeDrawing, che comprende WindowInsets.ime.
// Contenuto largo al massimo 480 dp.
@Composable
internal fun FormScaffold(
    onBack: () -> Unit,
    title: StringResource,
    body: StringResource,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ForestHeader(Modifier.fillMaxWidth().height(200.dp))
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
                    Spacer(Modifier.size(24.dp))
                }
            }
            // azioni in fondo, su un filo che le separa dal contenuto che scorre
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                content = actions,
            )
        }
        BackButton(onBack, Modifier.align(Alignment.TopStart))
    }
}

// "Non hai un account? Registrati": la domanda in grigio, il link in verde. Va a capo con il testo grande.
// È l'azione alternativa sotto quella principale, qui e nel Profilo da ospite.
@Composable
internal fun FooterLink(question: StringResource, link: StringResource, onClick: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(question), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(link), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Password con "Mostra"/"Nascondi" (testo e non l'occhio, che in material-icons-core non c'è)
@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    error: String? = null,
    hint: String? = null,
    label: String = stringResource(Res.string.auth_password),
    imeAction: ImeAction = ImeAction.Done,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        error = error,
        supporting = hint,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        // testo e non l'icona dell'occhio, che in material-icons-core non c'è; dice anche cosa fa
        trailing = {
            TextButton(onClick = { visible = !visible }, modifier = Modifier.heightIn(min = 48.dp).padding(end = 4.dp)) {
                Text(
                    stringResource(if (visible) Res.string.auth_hide_password else Res.string.auth_show_password),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
    )
}
