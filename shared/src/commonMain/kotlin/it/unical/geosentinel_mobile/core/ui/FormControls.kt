// Controlli condivisi dei form: campo di testo, campo password, pulsanti a capsula, riquadro d'errore, link
// alternativo e la struttura comune delle schermate con un form. Tutti i form dell'app li usano, così hanno lo
// stesso aspetto e lo stesso comportamento (accessibilità, tastiera, safe area).
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import it.unical.geosentinel_mobile.core.ui.theme.StatusBarOverDark
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Campo di testo dei form: una card alta almeno 56 dp con angoli da 16 dp e un bordo che diventa verde felce (2 dp)
 * quando il campo ha il focus e prugna quando c'è un errore. L'etichetta resta sempre visibile e sale sopra il testo.
 * I colori vengono tutti dal tema.
 *
 * @param error messaggio d'errore mostrato sotto il campo, che spiega cosa correggere; colora anche il bordo.
 * @param supporting testo di aiuto mostrato sotto il campo quando non c'è un errore.
 * @param trailing elemento facoltativo a destra dentro il campo (per esempio "Mostra" nel campo password).
 * @param interactionSource sorgente delle interazioni, da cui si legge il focus; si può passare dall'esterno.
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
    // Falso per i testi lunghi, come la descrizione di una segnalazione; gli altri campi restano su una riga.
    singleLine: Boolean = true,
    minLines: Int = 1,
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
    val shape = MaterialTheme.shapes.small
    Column(modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            isError = error != null,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            trailingIcon = trailing,
            interactionSource = interactionSource,
            shape = shape,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLowest,
                unfocusedContainerColor = colors.surfaceContainerLowest,
                errorContainerColor = colors.surfaceContainerLowest,
                disabledContainerColor = colors.surfaceContainerHighest,
                // Nessuna linea sotto il campo: a indicare focus ed errore è il bordo intero.
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
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
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp),
            )
        }
    }
}

/**
 * Pulsante dell'azione principale della schermata: una capsula color pino (menta nel tema scuro) alta almeno 56 dp,
 * che si rimpicciolisce leggermente sotto il dito.
 *
 * @param loading se vero il pulsante è disabilitato e mostra un indicatore di avanzamento al posto del testo.
 * @param fill se vero (predefinito) occupa tutta la larghezza; se falso è largo quanto il testo, per stare accanto
 *   a un'altra informazione (come "Prenota" nella barra della pagina del rifugio).
 * @param icon icona facoltativa prima del testo.
 */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fill: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.tertiary,
            contentColor = colors.onTertiary,
            disabledContainerColor = colors.surfaceContainerHighest,
            disabledContentColor = colors.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 56.dp)
            .pressScale(interaction),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.onSurfaceVariant)
        } else {
            if (icon != null) {
                // Icona decorativa: l'azione è già scritta accanto, quindi i lettori di schermo la ignorano.
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Riquadro con l'errore del form, mostrato sopra il pulsante d'invio: color prugna (mai rosso, riservato
 * all'emergenza) e con un'icona. Il testo è una live region, quindi TalkBack e VoiceOver lo annunciano.
 * Compare e scompare con una breve animazione; con [message] null il riquadro si chiude.
 */
@Composable
fun FormErrorBanner(message: String?) {
    // Durante la dissolvenza d'uscita resta visibile l'ultimo testo, invece di un riquadro vuoto.
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
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = shown.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * Pulsante a capsula per le azioni secondarie (Chiama, Indicazioni, Riprova in una sezione): fondo felce tenue,
 * alto almeno 48 dp. Si riconosce come pulsante, ma l'unica azione piena della schermata resta [PrimaryPillButton].
 */
@Composable
internal fun TonalPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        interactionSource = interaction,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = 48.dp).pressScale(interaction),
    ) {
        if (icon != null) {
            // Icona decorativa: l'azione è già scritta accanto.
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * La struttura comune delle schermate con un form, con il contenuto largo al massimo 480 dp.
 *
 * - Con [brand] (accesso, registrazione, password) in alto c'è il cielo dell'ora con il titolo grande, e il foglio
 *   con i campi sale sopra le creste.
 * - Altrimenti ci sono il pulsante Indietro, il titolo in Archivo e una riga di testo, su fondo pieno.
 * - In fondo, fissa, c'è l'area delle azioni ([actions]): l'azione principale con l'eventuale link alternativo
 *   sotto, sempre visibile e a portata di pollice. [error], la risposta del server all'invio, compare lì sopra il
 *   pulsante. Con la tastiera aperta l'area sale sopra la tastiera, perché lo Scaffold di App applica safeDrawing,
 *   che comprende WindowInsets.ime.
 *
 * @param title titolo della schermata.
 * @param body riga di spiegazione sotto il titolo.
 * @param content i campi del form, distanziati di 18 dp; scorrono se non entrano nello schermo.
 */
@Composable
internal fun FormScaffold(
    onBack: () -> Unit,
    title: StringResource,
    body: StringResource,
    error: String? = null,
    brand: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val daylight = rememberDaylight()
    val palette = skyPalette(daylight.phase)
    if (brand) StatusBarOverDark(palette.dark)
    Box(Modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (brand) {
                    Column(
                        Modifier.fillMaxWidth().skyBackdrop(palette, sheet = background),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(title),
                            style = MaterialTheme.typography.displaySmall,
                            color = palette.content,
                            modifier = Modifier
                                .widthIn(max = 480.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = topInset + 72.dp)
                                .semantics { heading() },
                        )
                        Spacer(Modifier.height(RidgeBand))
                    }
                } else {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
                    // Spazio per il pulsante Indietro.
                    Spacer(Modifier.height(60.dp))
                }
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                    if (!brand) {
                        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { content() }
                    Spacer(Modifier.size(24.dp))
                }
            }
            StickyActionBar {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
                    FormErrorBanner(error)
                    if (error != null) Spacer(Modifier.size(12.dp))
                    actions()
                }
            }
        }
        // Fondo pieno sotto la barra di stato, così titolo e campi che scorrono non passano sotto l'ora. Con il cielo
        // ha il colore della cima del cielo, quindi a pagina ferma non si nota.
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.safeDrawing).background(if (brand) palette.top else background))
        BackButton(onBack, Modifier.align(Alignment.TopStart))
    }
}

/**
 * Link alternativo sotto l'azione principale, del tipo "Non hai un account? Registrati": la domanda in grigio e il
 * link in felce. Se il testo è ingrandito va a capo. Si usa nei form e nel Profilo da ospite.
 */
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

/**
 * Campo password con il pulsante "Mostra"/"Nascondi" a destra. Si usa un testo e non l'icona dell'occhio perché
 * dice esplicitamente cosa fa. La scelta sopravvive alla rotazione dello schermo.
 *
 * @param onDone chiamata quando l'utente preme il tasto di invio della tastiera (Fatto o Avanti).
 * @param hint testo di aiuto sotto il campo, per esempio i requisiti della password.
 * @param imeAction il tasto d'azione della tastiera.
 */
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
