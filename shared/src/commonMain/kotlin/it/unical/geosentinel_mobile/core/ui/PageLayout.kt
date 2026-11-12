package it.unical.geosentinel_mobile.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

// Struttura comune delle pagine (12/10): testata più bassa con il titolo subito sotto, gruppi di righe in una card sola
// (lo stile "raggruppato" delle impostazioni) invece di una card per riga, e il contenuto che scorre sotto la barra a
// isola. Prima ogni scheda apriva con 200 dp di paesaggio, titolo e due righe di introduzione: il contenuto partiva a
// metà schermo.

/** Larghezza massima del contenuto delle pagine (tablet, orizzontale). */
internal val PageMaxWidth = 600.dp


/**
 * Quanto occupa la barra a isola sotto il contenuto di una scheda (0 dove non c'è): App la fornisce, le schede la
 * aggiungono in fondo al loro contenuto, che così le scorre sotto fino al bordo.
 */
internal val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Spazio da lasciare in fondo a una pagina che scorre: la barra a isola (che comprende già la safe area) nelle schede;
 * 0 nelle schermate spinte, dove il NavHost ha già il margine della safe area (prima si sommava due volte).
 */
@Composable
internal fun pageBottomInset(): Dp = LocalBottomBarInset.current

/**
 * Testata della pagina (redesign 11/2026: niente più paesaggio): il titolo grande sopra le curve di livello, con, a
 * destra, un'azione secondaria facoltativa ([trailing]) e sotto una riga di contesto ([intro]), breve. [hasBack]: la
 * pagina ha il tondo Indietro in alto, che vuole il suo spazio sopra il titolo.
 * Per le pagine nuove c'è [LargeTitlePage], che in più comprime il titolo in una barra scorrendo.
 */
@Composable
internal fun PageHeader(
    title: String,
    intro: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    // sotto il titolo e l'introduzione: le schede (Segnalazioni | Ticket) o un link d'azione
    below: (@Composable () -> Unit)? = null,
    hasBack: Boolean = false,
) {
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().alpineBackdrop(landscapeHeight(topInset, hasBack)),
    ) {
        Spacer(Modifier.height(landscapeHeight(topInset, hasBack)))
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    trailing()
                }
            }
            if (intro != null) {
                Text(
                    intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (below != null) {
                Spacer(Modifier.height(16.dp))
                below()
            }
        }
    }
}

/** Titolo di un gruppo di righe, piccolo e grigio, appena sopra la card. */
@Composable
internal fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

/**
 * Card che raccoglie righe separate da un filo: un gruppo, un oggetto solo per l'occhio. Senza bordo (redesign): si
 * stacca dal fondo con il passo di tono, come le liste raggruppate di iOS.
 */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

/** Filo tra due righe, rientrato quanto il testo (dopo l'icona, se c'è). */
@Composable
internal fun GroupDivider(indent: Dp = 16.dp) = HorizontalDivider(Modifier.padding(start = indent), color = MaterialTheme.colorScheme.outlineVariant)

/** Rientro del filo dopo una riga con l'icona: 16 + 32 + 12. */
internal val GroupIconIndent = 60.dp

/**
 * Riga di un gruppo, alta almeno 56 dp: icona facoltativa su un tondo muschio, titolo, sottotitolo, valore a destra e
 * freccia se apre un'altra schermata. [color] per le righe-azione (Esci, Elimina): niente freccia, testo in grassetto.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = if (chevron) 8.dp else 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // decorativa: il titolo dice già cos'è. Tessera piena come nelle Impostazioni di iOS, nel verde abete
            Box(Modifier.size(32.dp).background(colors.primary, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                // come nelle liste di iOS: titolo normale, il peso lo danno le righe-azione
                fontWeight = if (color != Color.Unspecified) FontWeight.SemiBold else FontWeight.Normal,
                color = if (color != Color.Unspecified) color else colors.onSurface,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        }
        if (chevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

// Raggio dei gruppi: quello delle card (shapes.medium)
private val GroupRadius = 20.dp

/**
 * Una riga di un gruppo in una lista pigra (allerte, ticket, prenotazioni: paginate, quindi una riga per item e non una
 * card sola). Disegna il suo pezzo di card: angoli in alto sulla prima, in basso sull'ultima, solo i lati in mezzo.
 * Senza spazio tra gli item, le righe formano un gruppo come [GroupCard]; il filo tra le righe lo mette la riga
 * ([GroupDivider] in cima se non è la prima).
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
    return this
        .drawBehind {
            val r = GroupRadius.toPx()
            val stroke = 1.dp.toPx()
            // il contorno di una card più alta, tagliato ai bordi della riga: restano solo i pezzi di questa riga
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = stroke / 2,
                        top = stroke / 2 - if (first) 0f else 2 * r,
                        right = size.width - stroke / 2,
                        bottom = size.height - stroke / 2 + if (last) 0f else 2 * r,
                        cornerRadius = CornerRadius(r),
                    ),
                )
            }
            // solo il fondo, senza bordo, come GroupCard
            clipRect { drawPath(path, fill) }
        }
        .clip(shape)
}

// ---------------------------------------------------------------------------------------------------------------------
// Redesign (11/2026, docs/UI_REDESIGN_PLAN.md §2.1)
// ---------------------------------------------------------------------------------------------------------------------

// Altezza della barra compatta in cima alle pagine (Indietro, titolo piccolo, azioni)
private val CompactBarHeight = 52.dp

// Il paesaggio sopra il titolo delle pagine: tanto basta per due creste e gli abeti (sotto la barra, se c'è)
private val LandscapeBand = 56.dp

private fun landscapeHeight(topInset: Dp, hasBar: Boolean): Dp =
    topInset + if (hasBar) CompactBarHeight + LandscapeBand else LandscapeBand + 24.dp

/**
 * Ombra delle "isole" (controlli che galleggiano sopra il contenuto: barra in basso, Indietro sopra una foto, barra
 * fissa): morbida e tinta del verde del tema invece del grigio, così sul bosco e sulla pietra non sporca.
 */
@Composable
internal fun Modifier.islandShadow(shape: Shape, elevation: Dp = 6.dp): Modifier {
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    return shadow(elevation, shape, clip = false, ambientColor = tint, spotColor = tint)
}

/**
 * Pagina con il titolo grande (stile iOS): il titolo in Barlow da 34 sta in cima alla lista, sopra le curve di livello,
 * e scorre con il contenuto; quando passa sotto la barra in alto lì compare il titolo piccolo, centrato, su fondo pieno.
 * [onBack] e [actions] stanno sempre nella barra; [below] sotto il titolo grande (segmenti, link).
 * Con [onRefresh] la lista si aggiorna tirandola in giù.
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
    // quanti px deve scorrere la lista perché il titolo grande passi sotto la barra
    var threshold by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > threshold }
    }
    val list = @Composable {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 24.dp + pageBottomInset()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "large-title") {
                // il cielo e le creste in una fascia sopra il titolo (terza passata): il titolo poggia sul fondo pulito
                val landscape = landscapeHeight(topInset, hasBar)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().alpineBackdrop(landscape),
                ) {
                    Column(
                        Modifier.fillMaxWidth().onSizeChanged { size ->
                            threshold = size.height - with(density) { (topInset + CompactBarHeight).roundToPx() }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(landscape))
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).semantics { heading() },
                        )
                    }
                    Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (below != null) {
                            Spacer(Modifier.height(16.dp))
                            below()
                        }
                    }
                }
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
        CompactBar(title, collapsed, onBack, actions)
    }
}

// La barra in cima: trasparente sopra il titolo grande, piena (con il titolo piccolo e un filo) quando il titolo è scorso via.
// La usano anche le pagine con la foto in testata (rifugio, sentiero), quando la foto è scorsa via
@Composable
internal fun CompactBar(title: String, collapsed: Boolean, onBack: (() -> Unit)?, actions: (@Composable RowScope.() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (collapsed) colors.background else colors.background.copy(alpha = 0f), tween(150))
    Column(Modifier.fillMaxWidth().background(fill)) {
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .height(CompactBarHeight),
        ) {
            if (onBack != null) BackButton(onBack, Modifier.align(Alignment.CenterStart), floating = false)
            val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(150))
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
                Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        if (collapsed) HorizontalDivider(color = colors.outlineVariant)
    }
}

/**
 * Due o tre viste della stessa pagina (Esplora | Prenotazioni, Segnalazioni | Ticket): binario a capsula, la voce
 * scelta su un "pollice" chiaro che scorre. [badges]: voce → descrizione per gli screen reader di un pallino accanto
 * all'etichetta (es. prenotazioni da controllare).
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
    val thumbColor = colors.surfaceContainerLowest
    val position by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest)
            .padding(4.dp)
            .drawBehind {
                // il pollice si disegna dietro alle voci, così segue l'altezza anche con il testo grande
                val width = size.width / options.size
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(width * position, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
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
 * Barra fissa in fondo alle schermate spinte e ai form: l'azione principale nella zona del pollice. Sopra, una
 * sfumatura di 24 dp verso il fondo: il contenuto che scorre sotto sfuma invece di finire contro un filo.
 * Le schermate la mettono in fondo a un Box (sopra il contenuto, che lascia spazio in basso) o in fondo a una Column.
 * Il margine della safe area lo dà già il NavHost.
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

/** "1 760 m · 24 posti · giu–set": i dati di un luogo in una riga, separati da un punto mediano. I null si saltano. */
@Composable
internal fun MetaLine(parts: List<String?>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val text = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
    if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
}

/** Titolo di una sezione dentro una pagina (Disponibilità, Servizi): 17 SemiBold, 32 dp sopra, 8 sotto. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 32.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/** Azione secondaria sotto il titolo della pagina, come link verde (es. "Nuova squadra di soccorso"). */
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
