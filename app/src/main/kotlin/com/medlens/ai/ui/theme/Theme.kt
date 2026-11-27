package com.medlens.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MedLensColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealLightContainer,
    onPrimaryContainer = TealDark,
    secondary = Color(0xFF2B6CB0),
    onSecondary = Color.White,
    secondaryContainer = InfoBlueBg,
    onSecondaryContainer = InfoBlue,
    tertiary = NormalGreen,
    onTertiary = Color.White,
    tertiaryContainer = NormalGreenBg,
    onTertiaryContainer = NormalGreen,
    background = MintBackground,
    onBackground = SlateText,
    surface = CardWhite,
    onSurface = SlateText,
    surfaceVariant = Color(0xFFE9F2F2),
    onSurfaceVariant = MutedText,
    outline = DividerSubtle,
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertRedBg,
    onErrorContainer = AlertRed
)

@Composable
fun MedLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MedLensColorScheme,
        typography = Typography,
        content = content
    )
}
