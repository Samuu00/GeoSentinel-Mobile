// Tema Material 3 dell'app: schemi di colore chiaro e scuro, tipografia, forme, tema d'emergenza per l'SOS e
// coordinamento del colore delle icone nella barra di stato. `App` avvolge tutta l'interfaccia in GeoSentinelTheme,
// così i componenti leggono i colori da `MaterialTheme.colorScheme` e non usano valori esadecimali.
package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.archivo_bold
import geosentinel_mobile.shared.generated.resources.archivo_semibold
import org.jetbrains.compose.resources.Font

// Come la palette dell'app è assegnata ai ruoli di Material 3:
// background = fondo (salvia di giorno, bosco di notte); surfaceContainerLowest, Low e surfaceContainer = card;
// surfaceContainerHigh = menu e superfici flottanti; surfaceContainerHighest = superfici in rilievo (binari,
// tessere); primary = accento felce (selezione, link, interruttori); primaryContainer = felce tenue; tertiary =
// azione principale piena (pino o menta, usata da PrimaryPillButton); onSurfaceVariant = testo secondario.

/** Schema dei colori per il tema chiaro. */
private val LightColorScheme = lightColorScheme(
    primary = FernLight,
    onPrimary = CardLight,
    primaryContainer = FernSoftLight,
    onPrimaryContainer = FernLight,
    inversePrimary = FernDark,
    secondary = Ink2Light,
    onSecondary = CardLight,
    secondaryContainer = RaisedLight,
    onSecondaryContainer = InkLight,
    tertiary = PineLight,
    onTertiary = CardLight,
    tertiaryContainer = RaisedLight,
    onTertiaryContainer = InkLight,
    error = ErrorPlumLight,
    onError = OnErrorPlumLight,
    errorContainer = ErrorPlumContainerLight,
    onErrorContainer = OnErrorPlumContainerLight,
    background = CanvasLight,
    onBackground = InkLight,
    surface = CanvasLight,
    onSurface = InkLight,
    surfaceVariant = RaisedLight,
    onSurfaceVariant = Ink2Light,
    surfaceTint = FernLight,
    inverseSurface = CardDark,
    inverseOnSurface = InkDark,
    outline = Ink2Light,
    outlineVariant = LineLight,
    surfaceBright = CardLight,
    surfaceDim = RaisedLight,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = RaisedLight,
)

/** Schema dei colori per il tema scuro, usato anche dalla schermata SOS. */
private val DarkColorScheme = darkColorScheme(
    primary = FernDark,
    onPrimary = OnPineDark,
    primaryContainer = FernSoftDark,
    onPrimaryContainer = FernDark,
    inversePrimary = FernLight,
    secondary = Ink2Dark,
    onSecondary = CardDark,
    secondaryContainer = RaisedDark,
    onSecondaryContainer = InkDark,
    tertiary = PineDark,
    onTertiary = OnPineDark,
    tertiaryContainer = RaisedDark,
    onTertiaryContainer = InkDark,
    error = ErrorPlumDark,
    onError = OnErrorPlumDark,
    errorContainer = ErrorPlumContainerDark,
    onErrorContainer = OnErrorPlumContainerDark,
    background = CanvasDark,
    onBackground = InkDark,
    surface = CanvasDark,
    onSurface = InkDark,
    surfaceVariant = RaisedDark,
    onSurfaceVariant = Ink2Dark,
    surfaceTint = FernDark,
    inverseSurface = CardLight,
    inverseOnSurface = InkLight,
    outline = Ink2Dark,
    outlineVariant = LineDark,
    surfaceBright = MenuDark,
    surfaceDim = CanvasDark,
    surfaceContainerLowest = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = MenuDark,
    surfaceContainerHighest = RaisedDark,
)

/**
 * La tipografia dell'app, con due caratteri:
 * - Archivo, un grottesco robusto, per titoli, numeri e dati;
 * - il carattere di sistema (SF Pro su iPhone, Roboto su Android) per tutto il resto, così il testo segue le
 *   impostazioni del telefono.
 * Le dimensioni seguono la scala di iOS: 17 sp il testo normale, 34 sp il titolo grande. È un Composable perché
 * i font si caricano dalle risorse di Compose.
 */
@Composable
private fun appTypography(): Typography {
    val display = FontFamily(
        Font(Res.font.archivo_semibold, FontWeight.SemiBold),
        Font(Res.font.archivo_bold, FontWeight.Bold),
    )
    val text = FontFamily.Default
    return Typography(
        displayLarge = TextStyle(fontFamily = display, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        displayMedium = TextStyle(fontFamily = display, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        displaySmall = TextStyle(fontFamily = display, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineLarge = TextStyle(fontFamily = display, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = display, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = TextStyle(fontFamily = display, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = TextStyle(fontFamily = display, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = TextStyle(fontFamily = text, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = text, fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = text, fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = text, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily = text, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        // Occhielli sopra i titoli (per esempio "SEZIONE" o "SENTIERO 401"): maiuscolo con spaziatura larga.
        labelSmall = TextStyle(fontFamily = text, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

/** Stile per numeri e dati in evidenza (quota, chilometri, posti liberi): Archivo SemiBold. */
val MaterialTheme.numberStyle: TextStyle
    @Composable get() = typography.headlineSmall

// Raggi degli angoli: 16 dp per campi e tessere piccole, 24 dp per le card, 32 dp per i fogli che salgono dal basso.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/**
 * Raccoglie le richieste sul colore delle icone della barra di stato. Le schermate che disegnano sotto la barra
 * (mappa, cielo, foto) chiedono icone chiare (`overDark = true`) o scure (`false`), a seconda di cosa c'è dietro.
 *
 * Vale l'ultima richiesta ancora attiva. Ogni schermata rimuove solo la propria quando esce, così durante una
 * transizione la schermata che esce non cancella la richiesta di quella che entra. Senza richieste le icone seguono
 * il tema. La lista è osservabile da Compose, quindi un cambiamento aggiorna subito la barra.
 */
@Stable
class StatusBarRequests {
    /** Le richieste attive, in ordine di arrivo: proprietario e valore richiesto. */
    private val requests = mutableStateListOf<Pair<Any, Boolean>>()
    /** Il valore dell'ultima richiesta attiva; null se non ce ne sono. */
    val overDark: Boolean? get() = requests.lastOrNull()?.second
    /** Registra (o sostituisce) la richiesta di [owner], portandola in cima. */
    fun request(owner: Any, overDark: Boolean) {
        requests.removeAll { it.first === owner }
        requests.add(owner to overDark)
    }
    /** Rimuove la richiesta di [owner], se c'è. */
    fun release(owner: Any) {
        requests.removeAll { it.first === owner }
    }
}

/** Le richieste sulla barra di stato, fornite da [GeoSentinelTheme]. */
val LocalStatusBarOverDark = staticCompositionLocalOf { StatusBarRequests() }

/**
 * Chiede icone della barra di stato chiare (se [overDark] è vero) o scure, finché questo composable resta nella
 * composizione. Con null la richiesta viene ritirata e le icone seguono il tema.
 */
@Composable
fun StatusBarOverDark(overDark: Boolean?) {
    val owner = remember { Any() }
    val requests = LocalStatusBarOverDark.current
    DisposableEffect(overDark) {
        if (overDark == null) requests.release(owner) else requests.request(owner, overDark)
        onDispose { requests.release(owner) }
    }
}

/**
 * Il tema dell'app. [mode] è la scelta dell'utente (Sistema, Chiaro, Scuro). Non si usano i colori dinamici di
 * Android 12 e successivi, perché il verde dell'app e il rosso dell'SOS devono restare sempre gli stessi.
 */
@Composable
fun GeoSentinelTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val statusBarOverDark = remember { StatusBarRequests() }
    SystemBarsAppearance(mode, statusBarOverDark.overDark ?: darkTheme)
    CompositionLocalProvider(LocalStatusBarOverDark provides statusBarOverDark) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Applica il tema scuro anche quando l'app è in chiaro. Lo usa la schermata SOS, che resta sempre scura e
 * concentrata, come l'SOS di sistema dei telefoni. Tipografia e forme restano quelle dell'app; le icone chiare
 * nella barra di stato le deve chiedere chi usa questo tema.
 */
@Composable
fun EmergencyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        // Il colore del contenuto di solito lo impostano le superfici; qui, prima di qualunque superficie, sarebbe
        // ancora quello del tema chiaro, quindi lo si imposta a mano.
        CompositionLocalProvider(LocalContentColor provides DarkColorScheme.onBackground, content = content)
    }
}
