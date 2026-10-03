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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.atkinson_next_400
import geosentinel_mobile.shared.generated.resources.atkinson_next_500
import geosentinel_mobile.shared.generated.resources.atkinson_next_600
import geosentinel_mobile.shared.generated.resources.barlow_semicondensed_bold
import geosentinel_mobile.shared.generated.resources.barlow_semicondensed_medium
import geosentinel_mobile.shared.generated.resources.barlow_semicondensed_semibold
import org.jetbrains.compose.resources.Font

// Come sono usati i ruoli di Material 3 nell'app:
// background = sfondo, surfaceContainerLowest = card, surfaceContainer = barra in basso,
// surfaceContainerHigh = menu, surfaceContainerHighest = binari (selettore del tema),
// primaryContainer = muschio, onSurfaceVariant = testo secondario, outlineVariant = bordi e curve di livello.
private val LightColorScheme = lightColorScheme(
    primary = FirLight,
    onPrimary = OnFirLight,
    primaryContainer = MossLight,
    onPrimaryContainer = OnMossLight,
    inversePrimary = LichenDark,
    secondary = InkSecondaryLight,
    onSecondary = CardLight,
    secondaryContainer = MossLight,
    onSecondaryContainer = OnMossLight,
    tertiary = FirLight,
    onTertiary = OnFirLight,
    error = ErrorPlumLight,
    onError = OnErrorPlumLight,
    errorContainer = ErrorPlumContainerLight,
    onErrorContainer = OnErrorPlumContainerLight,
    background = StoneLight,
    onBackground = InkLight,
    surface = StoneLight,
    onSurface = InkLight,
    surfaceVariant = TrackLight,
    onSurfaceVariant = InkSecondaryLight,
    surfaceTint = FirLight,
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
    primary = LichenDark,
    onPrimary = OnLichenDark,
    primaryContainer = MossDark,
    onPrimaryContainer = OnMossDark,
    inversePrimary = FirLight,
    secondary = InkSecondaryDark,
    onSecondary = CardDark,
    secondaryContainer = MossDark,
    onSecondaryContainer = OnMossDark,
    tertiary = LichenDark,
    onTertiary = OnLichenDark,
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
    surfaceTint = LichenDark,
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

// Due voci, come su una carta dei sentieri:
// - titoli in Barlow Semi Condensed, il semi-condensato dei cartelli e dei segnavia;
// - tutto il resto in Atkinson Hyperlegible Next, disegnato per leggersi bene in condizioni difficili (sole, fretta).
// Stessi file su Android e iOS (composeResources/font, licenze OFL in composeResources/files).
// Tutti i ruoli sono definiti: uno lasciato al default ricadrebbe su Roboto/SF con un'altra scala.
@Composable
private fun appTypography(): Typography {
    val sign = FontFamily(
        Font(Res.font.barlow_semicondensed_medium, FontWeight.Medium),
        Font(Res.font.barlow_semicondensed_semibold, FontWeight.SemiBold),
        Font(Res.font.barlow_semicondensed_bold, FontWeight.Bold),
    )
    val text = FontFamily(
        Font(Res.font.atkinson_next_400, FontWeight.Normal),
        Font(Res.font.atkinson_next_500, FontWeight.Medium),
        Font(Res.font.atkinson_next_600, FontWeight.SemiBold),
    )
    return Typography(
        displaySmall = TextStyle(fontFamily = sign, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = TextStyle(fontFamily = sign, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        headlineMedium = TextStyle(fontFamily = sign, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        headlineSmall = TextStyle(fontFamily = sign, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = text, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontFamily = text, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = text, fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = TextStyle(fontFamily = text, fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontFamily = text, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily = text, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = text, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    )
}

// Forme morbide, come ciottoli: 20 per le card, 28 per la barra
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
        typography = appTypography(),
        shapes = AppShapes,
        content = content,
    )
}
