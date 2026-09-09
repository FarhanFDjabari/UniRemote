package dev.djabari.uniremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF24272B),
    onSurfaceVariant = Color(0xFFC4C7C5),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFE0E0E0),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
)

@Composable
fun UniRemoteTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
