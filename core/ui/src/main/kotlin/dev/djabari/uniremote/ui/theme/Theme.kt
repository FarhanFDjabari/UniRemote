package dev.djabari.uniremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Pure black surfaces: AMOLED pixels showing black are switched off, which saves power and
 * keeps the phone from lighting up a dark room. Controls sit on slightly raised greys so
 * they stay distinct from the background.
 */
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF81D4FA),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF1A3B4D),
    onSecondaryContainer = Color(0xFFC2E8FF),
    tertiary = Color(0xFFA5D6A7),
    onTertiary = Color(0xFF1B5E20),
    tertiaryContainer = Color(0xFF1F3D22),
    onTertiaryContainer = Color(0xFFC8E6C9),
    background = Color.Black,
    onBackground = Color(0xFFE0E0E0),
    surface = Color.Black,
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF24272B),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2A2C30),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0B),
    surfaceContainer = Color(0xFF111214),
    surfaceContainerHigh = Color(0xFF18191B),
    surfaceContainerHighest = Color(0xFF1F2124),
    outline = Color(0xFF8D9196),
    outlineVariant = Color(0xFF3A3D41),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF0277BD),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2E8FF),
    onSecondaryContainer = Color(0xFF001E2C),
    tertiary = Color(0xFF2E7D32),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8E6C9),
    onTertiaryContainer = Color(0xFF0A2E0D),
    background = Color(0xFFF8F9FB),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF8F9FB),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDDE1E6),
    onSurfaceVariant = Color(0xFF43474B),
    surfaceDim = Color(0xFFD8DADD),
    surfaceBright = Color(0xFFF8F9FB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F3F5),
    surfaceContainer = Color(0xFFECEEF0),
    surfaceContainerHigh = Color(0xFFE6E8EB),
    surfaceContainerHighest = Color(0xFFE0E2E5),
    outline = Color(0xFF73777B),
    outlineVariant = Color(0xFFC3C7CB),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

@Composable
fun UniRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
