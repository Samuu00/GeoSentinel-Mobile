// Componenti per la struttura comune delle pagine.
// In cima c'è il cielo dell'ora con il titolo grande (Sky.kt), poi il foglio su cui sta il contenuto: gruppi di righe
// su card bianche, tessere con i dati, chip di stato, barre fisse con le azioni. Nelle schede principali il contenuto
// scorre sotto la barra di navigazione in basso, che galleggia sopra la pagina come un'isola.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.unical.geosentinel_mobile.core.ui.theme.StatusBarOverDark
import it.unical.geosentinel_mobile.core.ui.theme.isDarkTheme

/** Larghezza massima del contenuto delle pagine, perché su tablet e in orizzontale le righe non diventino troppo lunghe. */
internal val PageMaxWidth = 600.dp

/**
 * L'altezza occupata dalla barra di navigazione in basso (0 dove non c'è). La fornisce App; le schede la aggiungono
 * come margine in fondo al loro contenuto, che così scorre sotto la barra fino al bordo dello schermo ma può
 * comunque arrivare a essere tutto visibile.
 */
internal val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Lo spazio da lasciare in fondo a una pagina che scorre: l'altezza della barra in basso nelle schede, 0 nelle altre schermate. */
@Composable
internal fun pageBottomInset(): Dp = LocalBottomBarInset.current

/**
 * La testata con il cielo e il titolo ([SkyHeader]) per una pagina che costruisce da sé la propria lista, dove va
 * messa come primo elemento. Finché il cielo sta sotto la barra di stato, le icone di sistema prendono il colore
 * adatto al cielo.
 *
 * @param hasBack vero se la pagina ha il pulsante Indietro in alto, che richiede spazio sopra il titolo.
 */
@Composable
internal fun PageHeader(
    title: String,
    intro: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    hasBack: Boolean = false,
) {
    val daylight = rememberDaylight()
    StatusBarOverDark(skyPalette(daylight.phase).dark)
    SkyHeader(title = title, intro = intro, trailing = trailing, below = below, topBar = hasBack, daylight = daylight)
}

/** Titolo di un gruppo di righe, in maiuscolo con spaziatura larga, appena sopra la card; annunciato come intestazione. */
@Composable
internal fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 6.dp, top = 28.dp, bottom = 10.dp).semantics { heading() },
    )
}

/**
 * Ombra morbida e leggermente verde per le card sul fondo chiaro. Nel tema scuro non c'è ombra: le card si
 * distinguono dal fondo per il tono più chiaro.
 */
@Composable
internal fun Modifier.cardShadow(shape: Shape = MaterialTheme.shapes.medium, elevation: Dp = 10.dp): Modifier {
    if (isDarkTheme()) return this
    val tint = Color(0xFF0E2A1E).copy(alpha = 0.10f)
    return shadow(elevation, shape, clip = false, ambientColor = tint, spotColor = tint)
}

/** Card che raccoglie un gruppo di righe separate da linee sottili, così l'occhio le legge come un insieme. */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().cardShadow(shape),
    ) {
        Column { content() }
    }
}

/** Linea sottile tra due righe di un gruppo, rientrata di [indent] per allinearsi al testo (dopo l'eventuale icona). */
@Composable
internal fun GroupDivider(indent: Dp = 16.dp) = HorizontalDivider(Modifier.padding(start = indent), color = MaterialTheme.colorScheme.outlineVariant)

/** Rientro della linea dopo una riga con l'icona: 16 dp di margine, 36 dp di icona e 14 dp di spazio. */
internal val GroupIconIndent = 66.dp

/**
 * Tessera quadrata con angoli arrotondati che contiene un'icona, su un fondo tenue dello stesso colore dell'icona.
 * Il colore predefinito è il verde felce; le righe che eseguono un'azione (Esci, Elimina) ne passano uno proprio.
 * È decorativa e non ha descrizione per i lettori di schermo.
 */
@Composable
internal fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 36.dp) {
    Box(
        modifier.size(size).background(tint.copy(alpha = if (isDarkTheme()) 0.18f else 0.12f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/**
 * Riga di un gruppo, alta almeno 60 dp: icona facoltativa, titolo, sottotitolo, valore a destra e freccia quando
 * apre un'altra schermata.
 *
 * @param color colore per le righe che eseguono un'azione (Esci, Elimina): il titolo diventa in grassetto e di quel
 *   colore. Se non è specificato la riga è una normale voce di navigazione.
 * @param chevron se vero mostra la freccia a destra.
 * @param onClickLabel descrizione dell'azione per i lettori di schermo.
 */
@Composable
internal fun GroupRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    color: Color = Color.Unspecified,
    enabled: Boolean = true,
    chevron: Boolean = true,
    onClickLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val action = color != Color.Unspecified
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = if (chevron) 10.dp else 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, tint = if (action) color else colors.primary)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (action) FontWeight.SemiBold else FontWeight.Normal,
                color = if (action) color else colors.onSurface,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        }
        if (chevron) {
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(20.dp))
        }
    }
}

// Raggio degli angoli dei gruppi, uguale a quello delle card (shapes.medium).
private val GroupRadius = 24.dp

/**
 * Fa apparire una riga di una LazyColumn come parte di una card di gruppo. Serve per le liste paginate (allerte,
 * ticket, prenotazioni), dove ogni riga è un elemento separato e non si può usare una sola card. Ogni riga disegna
 * il proprio pezzo di card: angoli arrotondati in alto se è la prima ([first]), in basso se è l'ultima ([last]).
 */
@Composable
internal fun Modifier.groupedItem(first: Boolean, last: Boolean): Modifier {
    val fill = MaterialTheme.colorScheme.surfaceContainerLowest
    val shape = RoundedCornerShape(
        topStart = if (first) GroupRadius else 0.dp,
        topEnd = if (first) GroupRadius else 0.dp,
        bottomStart = if (last) GroupRadius else 0.dp,
        bottomEnd = if (last) GroupRadius else 0.dp,
    )
    return this.clip(shape).background(fill)
}

/**
 * Ombra per i controlli che galleggiano sopra il contenuto (barra in basso, pulsante Indietro, controlli della mappa).
 * Nel tema scuro è più intensa, altrimenti non si vedrebbe.
 */
@Composable
internal fun Modifier.islandShadow(shape: Shape, elevation: Dp = 12.dp): Modifier {
    val tint = Color(0xFF06140E).copy(alpha = if (isDarkTheme()) 0.5f else 0.18f)
    return shadow(elevation, shape, clip = false, ambientColor = tint, spotColor = tint)
}

// Altezza della barra compatta in cima alle pagine (Indietro, titolo piccolo, azioni).
private val CompactBarHeight = 52.dp

/**
 * Pagina con il cielo e il titolo grande, basata su una LazyColumn. La testata ([SkyHeader]) è il primo elemento e
 * scorre con il contenuto; quando il cielo passa sotto la barra in alto, nella barra compare il titolo piccolo su
 * fondo pieno.
 *
 * @param onBack se presente, la barra mostra il pulsante Indietro.
 * @param actions azioni mostrate sempre a destra nella barra.
 * @param below contenuto sotto il titolo grande, già sul foglio (segmenti, link).
 * @param onRefresh se presente, la pagina si aggiorna trascinando verso il basso; [refreshing] indica l'aggiornamento in corso.
 * @param content gli elementi della lista dopo la testata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LargeTitlePage(
    title: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val hasBar = onBack != null || actions != null
    val daylight = rememberDaylight()
    val palette = skyPalette(daylight.phase)
    // Di quanti pixel deve scorrere la lista perché il cielo passi sotto la barra; si calcola dall'altezza della
    // testata appena è nota.
    var threshold by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > threshold }
    }
    // Icone della barra di stato: finché si vede il cielo seguono il cielo, sulla barra piena seguono il tema.
    StatusBarOverDark(if (collapsed) null else palette.dark)
    val list = @Composable {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 24.dp + pageBottomInset()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "large-title") {
                SkyHeader(
                    title = title,
                    intro = subtitle,
                    below = below,
                    topBar = hasBar,
                    daylight = daylight,
                    modifier = Modifier.onSizeChanged { size ->
                        threshold = size.height - with(density) { (topInset + CompactBarHeight + RidgeBand).roundToPx() }
                    },
                )
            }
            content()
        }
    }
    Box(modifier.fillMaxSize().background(colors.background)) {
        if (onRefresh != null) {
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { list() }
        } else {
            list()
        }
        CompactBar(title, collapsed, onBack, actions, overSky = if (collapsed) null else palette.content)
    }
}

/**
 * La barra in cima alla pagina: trasparente sopra il cielo o la foto, piena con il titolo piccolo e una linea sotto
 * quando questi sono scorsi via ([collapsed]). Il passaggio avviene in dissolvenza.
 *
 * @param overSky il colore dei controlli finché la barra sta sopra il cielo; null per usare quelli del tema.
 */
@Composable
internal fun CompactBar(
    title: String,
    collapsed: Boolean,
    onBack: (() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    overSky: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (collapsed) colors.background else colors.background.copy(alpha = 0f), tween(180))
    Column(Modifier.fillMaxWidth().background(fill)) {
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .height(CompactBarHeight),
        ) {
            if (onBack != null) BackButton(onBack, Modifier.align(Alignment.CenterStart).padding(start = 8.dp), floating = !collapsed, inBar = true)
            val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(180))
            if (titleAlpha > 0f) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp).graphicsLayer { alpha = titleAlpha },
                )
            }
            if (actions != null) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides (overSky ?: colors.onSurface),
                ) {
                    Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
        if (collapsed) HorizontalDivider(color = colors.outlineVariant)
    }
}

/**
 * Selettore tra due o tre viste della stessa pagina (per esempio Esplora | Prenotazioni, Segnalazioni | Ticket): un
 * binario in rilievo su cui la voce scelta è evidenziata da una card bianca che si sposta con un'animazione a molla.
 * Le voci sono annunciate come schede.
 *
 * @param badges per ogni indice di voce, la descrizione letta dai lettori di schermo di un pallino mostrato accanto
 *   all'etichetta (per esempio prenotazioni da controllare).
 */
@Composable
internal fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<Int, String> = emptyMap(),
) {
    val colors = MaterialTheme.colorScheme
    // Bianco di giorno; di notte un tono appena più chiaro del binario.
    val thumbColor = colors.surfaceContainerHigh
    val dark = isDarkTheme()
    val position by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest)
            .padding(4.dp)
            .drawBehind {
                val width = size.width / options.size
                val corner = CornerRadius(size.height / 2)
                val topLeft = Offset(width * position, 0f)
                if (!dark) {
                    // Un'ombra appena accennata sotto la voce selezionata, solo nel tema chiaro.
                    drawRoundRect(Color.Black.copy(alpha = 0.06f), topLeft + Offset(0f, 2.dp.toPx()), Size(width, size.height), corner)
                }
                drawRoundRect(thumbColor, topLeft, Size(width, size.height), corner)
            }
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            val badge = badges[index]
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.Tab) { onSelect(index) }
                    .semantics { if (badge != null) stateDescription = badge }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(8.dp).background(colors.primary, CircleShape))
                }
            }
        }
    }
}

/**
 * Barra fissa in fondo ai dettagli e ai form, con l'azione principale nella zona raggiungibile dal pollice. Sopra la
 * barra c'è una sfumatura di 24 dp verso il colore del fondo, così il contenuto che scorre sotto sfuma invece di
 * terminare contro una linea netta.
 */
@Composable
internal fun StickyActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val fade = 24.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg), startY = -fade, endY = 0f),
                    topLeft = Offset(0f, -fade),
                    size = Size(size.width, fade),
                )
            }
            .background(bg)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth(), content = content)
    }
}

/**
 * I dati di un luogo su una riga, separati da un punto mediano (per esempio "1 760 m · 24 posti · giu-set").
 * Le parti null o vuote vengono saltate; se non resta nulla non si mostra niente.
 */
@Composable
internal fun MetaLine(parts: List<String?>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val text = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
    if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
}

/**
 * Titolo di una sezione dentro una pagina (per esempio Disponibilità o Servizi), in Archivo da 20 sp con 32 dp di
 * spazio sopra e 12 sotto. [trailing] è un elemento facoltativo a destra.
 */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 32.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/** Azione secondaria sotto il titolo della pagina, mostrata come link (per esempio "Nuova squadra di soccorso"). */
@Composable
internal fun HeaderLink(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Un dato in evidenza, come "Quota 1 760 m": etichetta, valore e unità di misura facoltativa. */
internal data class Stat(val label: String, val value: String, val unit: String? = null)

/**
 * I dati di un luogo affiancati in un'unica card, separati da linee verticali (per esempio Lunghezza, Dislivello,
 * Tempo per un sentiero). I lettori di schermo leggono ogni dato come una frase sola ("Quota 1 760 m").
 * Con una lista vuota non mostra nulla.
 */
@Composable
internal fun StatStrip(stats: List<Stat>, modifier: Modifier = Modifier) {
    if (stats.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val single = stats.size == 1
    val shape = MaterialTheme.shapes.medium
    // Con un solo dato la card è larga quanto il contenuto, invece di diventare una fascia quasi vuota.
    Surface(
        shape = shape,
        color = colors.surfaceContainerLowest,
        modifier = modifier.then(if (single) Modifier else Modifier.fillMaxWidth()).cardShadow(shape),
    ) {
        Row(Modifier.padding(vertical = 16.dp)) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) Box(Modifier.width(1.dp).height(48.dp).background(colors.outlineVariant).align(Alignment.CenterVertically))
                Column(
                    Modifier
                        .then(if (single) Modifier.widthIn(min = 120.dp) else Modifier.weight(1f))
                        .padding(horizontal = 16.dp)
                        .semantics(mergeDescendants = true) { },
                ) {
                    Text(stat.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stat.value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (stat.unit != null) {
                            Text(
                                " ${stat.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * I dati di un luogo in tessere separate e affiancate, una card per dato (per esempio Quota, Posti, Stagione).
 * Con una lista vuota non mostra nulla.
 */
@Composable
internal fun StatTiles(stats: List<Stat>, modifier: Modifier = Modifier) {
    if (stats.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.forEach { stat ->
            Surface(shape = shape, color = colors.surfaceContainerLowest, modifier = Modifier.weight(1f).cardShadow(shape)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp).semantics(mergeDescendants = true) { }) {
                    Text(stat.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stat.value + (stat.unit?.let { " $it" } ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // La riga ha sempre tre posti: con meno dati le tessere restano larghe un terzo invece di allungarsi.
        repeat(3 - stats.size.coerceAtMost(3)) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * Capsula di stato (Aperto, Attenzione, Chiuso, Confermata): icona facoltativa e testo nel colore [color], su un
 * fondo tenue dello stesso colore oppure su [container] se indicato.
 */
@Composable
internal fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, container: Color? = null) {
    Surface(
        color = container ?: color.copy(alpha = if (isDarkTheme()) 0.18f else 0.12f),
        contentColor = color,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(Modifier.heightIn(min = 30.dp).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Card toccabile delle liste: bianca, angoli da 24 dp e ombra morbida. Si rimpicciolisce leggermente sotto il dito
 * ed entra in sequenza con le altre in base alla posizione [index] nella lista.
 *
 * @param onClickLabel descrizione dell'azione per i lettori di schermo.
 */
@Composable
internal fun TapCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
            .enterStagger(index)
            .pressScale(interaction)
            .cardShadow(shape)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
    ) {
        Column(content = content)
    }
}

/**
 * Formatta una posizione come "39,3121 N · 16,4211 E": quattro decimali (circa 10 metri di precisione), virgola
 * decimale ed emisfero indicato con N/S ed E/O, come la leggono i soccorritori.
 */
internal fun coordinatesLabel(lat: Double, lon: Double): String {
    /** Valore assoluto con quattro decimali e la virgola come separatore. */
    fun Double.four(): String {
        val scaled = kotlin.math.round(kotlin.math.abs(this) * 10_000).toLong()
        return "${scaled / 10_000},${(scaled % 10_000).toString().padStart(4, '0')}"
    }
    return "${lat.four()} ${if (lat >= 0) "N" else "S"} · ${lon.four()} ${if (lon >= 0) "E" else "O"}"
}
