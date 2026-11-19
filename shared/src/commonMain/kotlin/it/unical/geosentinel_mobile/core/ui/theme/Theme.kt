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

// Ruoli di Material 3 nel redesign "Luce di quota":
// background = fondo salvia / bosco, surfaceContainerLowest..surfaceContainer = card, surfaceContainerHigh = menu e
// isole, surfaceContainerHighest = rilievo (binari, tessere), primary = accento felce (selezione, link, interruttori),
// primaryContainer = felce tenue, tertiary = azione piena (pino / menta: PrimaryPillButton), onSurfaceVariant = testo
// secondario.
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

// Due voci:
// - Archivo (grottesco robusto, da attrezzatura da montagna) per titoli, numeri e dati: la voce dell'app;
// - il carattere di sistema (SF Pro su iPhone, Roboto su Android) per tutto il resto: segue le impostazioni del
//   telefono. Scala di iOS: 17 il testo, 34 il titolo grande.
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
        // occhielli (SEZIONE, SENTIERO 401): maiuscolo spaziato
        labelSmall = TextStyle(fontFamily = text, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

/** Numeri e dati (quota, km, posti): Archivo SemiBold, cifre della voce dell'app. */
val MaterialTheme.numberStyle: TextStyle
    @Composable get() = typography.headlineSmall

// Forme: tondeggianti ma non molli. 16 campi e tessere piccole, 24 card, 32 fogli
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/**
 * Chi disegna sotto la barra di stato (mappa, cielo, foto) chiede icone chiare (overDark = true) o scure (false).
 * Vale l'ultima richiesta ancora attiva: ogni schermata toglie solo la sua quando esce, così durante una transizione
 * quella che esce non cancella la richiesta di quella che entra. Nessuna richiesta = le icone seguono il tema.
 */
@Stable
class StatusBarRequests {
    private val requests = mutableStateListOf<Pair<Any, Boolean>>()
    val overDark: Boolean? get() = requests.lastOrNull()?.second
    fun request(owner: Any, overDark: Boolean) {
        requests.removeAll { it.first === owner }
        requests.add(owner to overDark)
    }
    fun release(owner: Any) {
        requests.removeAll { it.first === owner }
    }
}

val LocalStatusBarOverDark = staticCompositionLocalOf { StatusBarRequests() }

/** Icone della barra di stato chiare ([overDark] = true) o scure finché questo composable resta; null = come il tema. */
@Composable
fun StatusBarOverDark(overDark: Boolean?) {
    val owner = remember { Any() }
    val requests = LocalStatusBarOverDark.current
    DisposableEffect(overDark) {
        if (overDark == null) requests.release(owner) else requests.request(owner, overDark)
        onDispose { requests.release(owner) }
    }
}

// Niente colori dinamici di Android 12+: il verde e il rosso dell'SOS devono restare questi
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
 * Il tema scuro anche con l'app in chiaro: per la schermata SOS, che resta una sola, concentrata, al buio (come
 * l'SOS di sistema). Stessa tipografia e forme; le icone di stato chiare le chiede chi lo usa.
 */
@Composable
fun EmergencyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        // il colore del testo lo danno le superfici: qui sopra c'è ancora quello del tema chiaro
        CompositionLocalProvider(LocalContentColor provides DarkColorScheme.onBackground, content = content)
    }
}
