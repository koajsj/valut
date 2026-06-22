package com.offlinevault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SafetyBlue,
    onPrimary = Color.White,
    primaryContainer = SafetyBlueDark,
    onPrimaryContainer = Color.White,
    background = VaultBackground,
    onBackground = TextPrimary,
    surface = VaultSurface,
    onSurface = TextPrimary,
    surfaceVariant = VaultSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = VaultOutline,
    error = RiskHigh,
    onError = Color.White
)

@Composable
fun OfflineVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = VaultTypography,
        content = content
    )
}
