package com.starrow.epgtimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.starrow.epgtimer.ui.UiSettings

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF00696D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF6FF6FB),
    onSecondaryContainer = Color(0xFF002021),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF004784),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF4CDADF),
    onSecondary = Color(0xFF003739),
    secondaryContainer = Color(0xFF004F52),
    onSecondaryContainer = Color(0xFF6FF6FB),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF121416),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    error = Color(0xFFF2B8B5),
)

@Composable
fun EpgTimerTheme(themeMode: Int, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        UiSettings.THEME_LIGHT -> false
        UiSettings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
