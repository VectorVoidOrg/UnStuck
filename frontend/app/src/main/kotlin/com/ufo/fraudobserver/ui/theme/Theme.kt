package com.ufo.fraudobserver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UfoLightColorScheme = lightColorScheme(
    primary = UfoPrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = UfoIndigoSoft,
    onPrimaryContainer = UfoIndigoDark,
    secondary = UfoTextPrimary,
    onSecondary = Color.White,
    tertiary = UfoSafeText,
    background = UfoBg,
    onBackground = UfoTextPrimary,
    surface = Color.White,
    onSurface = UfoTextPrimary,
    surfaceVariant = UfoIndigoSoft,
    onSurfaceVariant = UfoTextSecondary,
    outline = UfoBorder,
    error = UfoDangerText,
    onError = Color.White
)

@Composable
fun UfoFraudObserverTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = UfoLightColorScheme,
        typography = Typography,
        content = content
    )
}
