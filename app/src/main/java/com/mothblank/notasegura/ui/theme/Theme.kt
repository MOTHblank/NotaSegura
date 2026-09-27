package com.mothblank.notasegura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SecureBlue,
    onPrimary = Color.White,
    primaryContainer = SecureBlueContainer,
    onPrimaryContainer = Color(0xFF082E68),
    secondary = AccessibleGreen,
    onSecondary = Color.White,
    secondaryContainer = AccessibleGreenContainer,
    onSecondaryContainer = Color(0xFF123B16),
    tertiary = WarningOrange,
    onTertiary = Color.White,
    tertiaryContainer = WarningOrangeContainer,
    onTertiaryContainer = Color(0xFF3B1900),
    error = ExpiredRed,
    onError = Color.White,
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF5F636B)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = SecureBlue,
    onPrimaryContainer = Color.White,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = Color(0xFF245A29),
    onSecondaryContainer = Color(0xFFD7F2D4),
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = Color(0xFF743500),
    onTertiaryContainer = Color(0xFFFFDCC1),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFFAEB1BA)
)

@Composable
fun NotaSeguraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
