package de.bolzwerk.tagwerk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Ruhige, warme Palette: Salbeigrün als Hauptfarbe, Terrakotta als Akzent, sandige Hintergründe.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4F6F52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E8D0),
    onPrimaryContainer = Color(0xFF0E2011),
    secondary = Color(0xFF9C6B4E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6DCCB),
    onSecondaryContainer = Color(0xFF3A1F0F),
    tertiary = Color(0xFF5B6B8C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE2F7),
    onTertiaryContainer = Color(0xFF161E33),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1E1C19),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1E1C19),
    surfaceVariant = Color(0xFFEDE6DC),
    onSurfaceVariant = Color(0xFF4C463E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1EA),
    surfaceContainer = Color(0xFFF1EBE3),
    surfaceContainerHigh = Color(0xFFEBE5DC),
    surfaceContainerHighest = Color(0xFFE5DED5),
    outline = Color(0xFF7D766D),
    outlineVariant = Color(0xFFCFC6BA),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C8A6),
    onPrimary = Color(0xFF1A3520),
    primaryContainer = Color(0xFF37563B),
    onPrimaryContainer = Color(0xFFD3E8D0),
    secondary = Color(0xFFE6B89C),
    onSecondary = Color(0xFF4A2814),
    secondaryContainer = Color(0xFF6B4630),
    onSecondaryContainer = Color(0xFFF6DCCB),
    tertiary = Color(0xFFB9C6E6),
    onTertiary = Color(0xFF243049),
    tertiaryContainer = Color(0xFF3B4763),
    onTertiaryContainer = Color(0xFFDCE2F7),
    background = Color(0xFF181715),
    onBackground = Color(0xFFE8E2DA),
    surface = Color(0xFF181715),
    onSurface = Color(0xFFE8E2DA),
    surfaceVariant = Color(0xFF3A3631),
    onSurfaceVariant = Color(0xFFCFC6BA),
    surfaceContainerLowest = Color(0xFF12110F),
    surfaceContainerLow = Color(0xFF201E1B),
    surfaceContainer = Color(0xFF24221F),
    surfaceContainerHigh = Color(0xFF2E2C28),
    surfaceContainerHighest = Color(0xFF393632),
    outline = Color(0xFF989086),
    outlineVariant = Color(0xFF4C463E),
    error = Color(0xFFF2B8B5),
)

/** Serifenschrift für längere Texte (Notizen, später Tagebuch-Prosa). */
val ProseTextStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp)

@Composable
fun TagwerkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
