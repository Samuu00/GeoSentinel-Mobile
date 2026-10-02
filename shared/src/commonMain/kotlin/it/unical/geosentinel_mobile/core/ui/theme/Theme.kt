package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Come sono usati i ruoli di Material 3 nell'app:
// background = sfondo, surfaceContainerLowest = card, surfaceContainer = barra in basso,
// surfaceContainerHigh = menu, surfaceContainerHighest = binari (selettore del tema),
// primaryContainer = muschio, onSurfaceVariant = testo secondario, outlineVariant = separatori.
private val LightColorScheme = lightColorScheme(
    primary = PineLight,
    onPrimary = OnPineLight,
    primaryContainer = MossLight,
    onPrimaryContainer = OnMossLight,
    inversePrimary = SageDark,
    secondary = InkSecondaryLight,
    onSecondary = CardLight,
    secondaryContainer = MossLight,
    onSecondaryContainer = OnMossLight,
    tertiary = PineLight,
    onTertiary = OnPineLight,
    error = ErrorPlumLight,
    onError = OnErrorPlumLight,
    errorContainer = ErrorPlumContainerLight,
    onErrorContainer = OnErrorPlumContainerLight,
    background = MistLight,
    onBackground = InkLight,
    surface = MistLight,
    onSurface = InkLight,
    surfaceVariant = TrackLight,
    onSurfaceVariant = InkSecondaryLight,
    surfaceTint = PineLight,
    inverseSurface = CardDark,
    inverseOnSurface = InkDark,
    outline = InkSecondaryLight,
    outlineVariant = LineLight,
    surfaceBright = CardLight,
    surfaceDim = TrackLight,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = MenuLight,
    surfaceContainerHighest = TrackLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = SageDark,
    onPrimary = OnSageDark,
    primaryContainer = MossDark,
    onPrimaryContainer = OnMossDark,
    inversePrimary = PineLight,
    secondary = InkSecondaryDark,
    onSecondary = CardDark,
    secondaryContainer = MossDark,
    onSecondaryContainer = OnMossDark,
    tertiary = SageDark,
    onTertiary = OnSageDark,
    error = ErrorPlumDark,
    onError = OnErrorPlumDark,
    errorContainer = ErrorPlumContainerDark,
    onErrorContainer = OnErrorPlumContainerDark,
    background = NightForestDark,
    onBackground = InkDark,
    surface = NightForestDark,
    onSurface = InkDark,
    surfaceVariant = TrackDark,
    onSurfaceVariant = InkSecondaryDark,
    surfaceTint = SageDark,
    inverseSurface = CardLight,
    inverseOnSurface = InkLight,
    outline = InkSecondaryDark,
    outlineVariant = LineDark,
    surfaceBright = MenuDark,
    surfaceDim = NightForestDark,
    surfaceContainerLowest = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = MenuDark,
    surfaceContainerHighest = TrackDark,
)

// Scala sobria: titoli SemiBold con un filo di spaziatura negativa, corpo 16-17.
// In sp: segue la dimensione del testo scelta dall'utente.
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

// Forme morbide, come ciottoli: 20 per le card, 28 per illustrazioni e barra
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

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
    SystemBarsAppearance(mode, darkTheme)
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
