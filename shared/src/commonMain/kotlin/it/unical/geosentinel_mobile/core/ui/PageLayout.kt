package it.unical.geosentinel_mobile.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

/** Altezza della testata con il paesaggio nelle schede e nelle liste: abbastanza per il carattere, non per il vuoto. */
internal val PageHeaderHeight = 148.dp

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
 * Testata della pagina: il paesaggio, poi il titolo grande con, a destra, un'azione secondaria facoltativa
 * ([trailing], es. "Prenotazioni") e sotto una riga di contesto ([intro]), breve.
 */
@Composable
internal fun PageHeader(
    title: String,
    intro: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    // sotto il titolo e l'introduzione: le schede (Segnalazioni | Ticket) o un link d'azione
    below: (@Composable () -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ForestHeader(Modifier.fillMaxWidth().height(PageHeaderHeight))
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp)) {
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
                Spacer(Modifier.height(12.dp))
                below()
            }
        }
    }
}

/** Capsula tonale per l'azione secondaria della testata: si vede, ma non compete con il contenuto. */
@Composable
internal fun HeaderPill(text: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(start = 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // decorativa: il testo dice già cosa apre
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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

/** Card che raccoglie righe separate da un filo: un gruppo, un oggetto solo per l'occhio. */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

/** Filo tra due righe, rientrato quanto il testo (dopo l'icona, se c'è). */
@Composable
internal fun GroupDivider(indent: Dp = 16.dp) = HorizontalDivider(Modifier.padding(start = indent), color = MaterialTheme.colorScheme.outlineVariant)

/** Rientro del filo dopo una riga con l'icona: 16 + 40 + 14. */
internal val GroupIconIndent = 70.dp

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
            .padding(start = 16.dp, end = if (chevron) 8.dp else 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // decorativa: il titolo dice già cos'è
            Box(Modifier.size(40.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (color != Color.Unspecified || subtitle != null) FontWeight.SemiBold else FontWeight.Normal,
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
    val line = MaterialTheme.colorScheme.outlineVariant
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
            clipRect {
                drawPath(path, fill)
                drawPath(path, line, style = Stroke(stroke))
            }
        }
        .clip(shape)
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
