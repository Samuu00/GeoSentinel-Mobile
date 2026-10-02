package it.unical.geosentinel_mobile.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.back
import geosentinel_mobile.shared.generated.resources.profile_account_section
import geosentinel_mobile.shared.generated.resources.profile_guest_body
import geosentinel_mobile.shared.generated.resources.profile_login
import geosentinel_mobile.shared.generated.resources.profile_logout
import geosentinel_mobile.shared.generated.resources.profile_register
import geosentinel_mobile.shared.generated.resources.profile_session_expired
import geosentinel_mobile.shared.generated.resources.role_admin
import geosentinel_mobile.shared.generated.resources.role_guest
import geosentinel_mobile.shared.generated.resources.role_hiker
import geosentinel_mobile.shared.generated.resources.role_park_manager
import geosentinel_mobile.shared.generated.resources.role_rescue_operator
import geosentinel_mobile.shared.generated.resources.theme_dark
import geosentinel_mobile.shared.generated.resources.theme_light
import geosentinel_mobile.shared.generated.resources.theme_section
import geosentinel_mobile.shared.generated.resources.theme_system
import geosentinel_mobile.shared.generated.resources.theme_title
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.session.UserRole
import it.unical.geosentinel_mobile.core.session.role
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import it.unical.geosentinel_mobile.core.ui.theme.ThemeMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Schermate provvisorie di M0.4: ognuna sparisce con la milestone che la implementa.
// Ogni schermata ha uno sfondo pieno: durante le transizioni non deve trasparire quella sotto.

private val ContentMaxWidth = 600.dp

// Stato vuoto: paesaggio con il simbolo della sezione, titolo, testo secondario.
// emergency = true solo per la schermata SOS: niente paesaggio, simbolo rosso (l'unico rosso oltre al pulsante).
@Composable
fun PlaceholderScreen(
    icon: ImageVector,
    title: StringResource,
    body: StringResource,
    emergency: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (onBack != null) BackButton(onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (emergency) {
                Icon(icon, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(64.dp))
            } else {
                Box(contentAlignment = Alignment.BottomCenter) {
                    ForestIllustration(
                        Modifier.padding(bottom = 28.dp).size(width = 240.dp, height = 140.dp).clip(MaterialTheme.shapes.large),
                    )
                    // simbolo della sezione su una "medaglia" a cavallo del bordo; il titolo lo dice già
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                            .border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Spacer(Modifier.size(20.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }
}

// Le schermate "spinte" (es. SOS, accesso) hanno il loro Indietro: su iPhone non c'è un tasto di sistema
@Composable
internal fun BackButton(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Text(stringResource(Res.string.back), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ProfilePlaceholderScreen(
    session: SessionState,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    val profile = (session as? SessionState.LoggedIn)?.profile
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
            Spacer(Modifier.size(8.dp))
            // Copertina con l'avatar a cavallo del bordo inferiore
            Box {
                ForestIllustration(Modifier.fillMaxWidth().height(148.dp).clip(MaterialTheme.shapes.large))
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 20.dp, y = 36.dp)
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                        .border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.size(48.dp))
            // Loggato: nome, poi ruolo ed email. Ospite: "Ospite" e cosa si guadagna accedendo.
            Text(
                text = profile?.displayName ?: stringResource(session.role.label()),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
            )
            Text(
                text = when {
                    profile != null -> stringResource(profile.role.label()) + " · " + profile.email
                    session is SessionState.LoggedIn -> "" // profilo non ancora letto (offline)
                    session is SessionState.Expired -> stringResource(Res.string.profile_session_expired)
                    else -> stringResource(Res.string.profile_guest_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )

            if (session is SessionState.LoggedIn) {
                SectionHeader(Res.string.profile_account_section)
                Card {
                    // Riga come quella del tema; "Esci" è un'uscita, non un errore: verde pino, non prugna
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClick = onLogout)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.profile_logout),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                // Ospite: due capsule compatte affiancate, l'azione principale piena e la secondaria solo bordo.
                // FlowRow: con il testo ingrandito la seconda va a capo invece di tagliarsi.
                Spacer(Modifier.size(20.dp))
                FlowRow(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onLogin,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 28.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(Res.string.profile_login), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onRegister,
                        shape = CircleShape,
                        // outline e non outlineVariant: il bordo di un pulsante deve vedersi (3:1)
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = 28.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(Res.string.profile_register), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            SectionHeader(Res.string.theme_section)
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.theme_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    ThemeSwitch(themeMode, onThemeModeSelected)
                }
            }

            Spacer(Modifier.size(32.dp))
        }
    }
}

// ---- Mattoncini ----

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier) { content() }
    }
}

@Composable
private fun SectionHeader(text: StringResource) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp).semantics { heading() },
    )
}

// ---- Selettore del tema: capsula con tre simboli e un cursore che scorre ----

// 48 dp per segmento: è il bersaglio minimo; il cursore visibile è un cerchio da 40 al centro
private val SegmentSize = 48.dp

@Composable
private fun ThemeSwitch(current: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    val thumbOffset by animateDpAsState(
        targetValue = SegmentSize * options.indexOf(current),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
    )
    Box(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .selectableGroup(),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(SegmentSize)
                .padding(4.dp)
                .shadow(1.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
        )
        Row {
            options.forEach { mode ->
                val label = stringResource(mode.label())
                val selected = mode == current
                Box(
                    modifier = Modifier
                        .size(SegmentSize)
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { onSelected(mode) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    ThemeGlyph(
                        mode = mode,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// Simboli disegnati a mano: auto (cerchio mezzo pieno), sole, luna. Le icone di base non li hanno.
@Composable
private fun ThemeGlyph(mode: ThemeMode, tint: Color, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val r = size.minDimension / 2
        val c = center
        when (mode) {
            ThemeMode.SYSTEM -> {
                val radius = r * 0.8f
                drawCircle(tint, radius, c, style = Stroke(width = r * 0.16f))
                drawArc(
                    color = tint,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = Size(radius * 2, radius * 2),
                )
            }
            ThemeMode.LIGHT -> {
                drawCircle(tint, r * 0.36f, c)
                repeat(8) { i ->
                    val angle = i * PI / 4
                    val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(tint, c + dir * (r * 0.62f), c + dir * (r * 0.92f), strokeWidth = r * 0.15f, cap = StrokeCap.Round)
                }
            }
            ThemeMode.DARK -> {
                val moon = Path().apply { addOval(Rect(c, r * 0.8f)) }
                val bite = Path().apply { addOval(Rect(Offset(c.x + r * 0.42f, c.y - r * 0.34f), r * 0.66f)) }
                drawPath(Path.combine(PathOperation.Difference, moon, bite), tint)
            }
        }
    }
}

private fun UserRole.label(): StringResource = when (this) {
    UserRole.GUEST -> Res.string.role_guest
    UserRole.HIKER -> Res.string.role_hiker
    UserRole.PARK_MANAGER -> Res.string.role_park_manager
    UserRole.RESCUE_OPERATOR -> Res.string.role_rescue_operator
    UserRole.ADMIN -> Res.string.role_admin
}

private fun ThemeMode.label(): StringResource = when (this) {
    ThemeMode.SYSTEM -> Res.string.theme_system
    ThemeMode.LIGHT -> Res.string.theme_light
    ThemeMode.DARK -> Res.string.theme_dark
}
