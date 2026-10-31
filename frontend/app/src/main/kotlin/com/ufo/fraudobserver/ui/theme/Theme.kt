package com.ufo.fraudobserver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AlertRed,
    secondary = WarningAmber,
    tertiary = SafeGreen,
    background = DeepBackground,
    surface = SurfaceDark,
    surfaceVariant = SurfaceElevated,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun UfoFraudObserverTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

