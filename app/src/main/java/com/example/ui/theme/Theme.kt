package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VintageDarkColorScheme = darkColorScheme(
  primary = VintageBrassPrimary,
  onPrimary = VintageLeatherDark,
  primaryContainer = VintageBrassDark,
  onPrimaryContainer = VintageBrassBright,
  secondary = VintageWitnessAmber,
  onSecondary = VintageLeatherDark,
  secondaryContainer = VintageWoodWalnut,
  onSecondaryContainer = VintageIvoryText,
  tertiary = VintageWitnessRed,
  onTertiary = VintageIvoryText,
  background = VintageLeatherDark,
  onBackground = VintageIvoryText,
  surface = VintageLeatherSurface,
  onSurface = VintageIvoryText,
  surfaceVariant = VintageLeatherElevated,
  onSurfaceVariant = VintageIvoryMuted,
  outline = VintageBrassDark,
  outlineVariant = VintageWoodBorder
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  // Always dark mode for vintage analog camera aesthetic
  MaterialTheme(
    colorScheme = VintageDarkColorScheme,
    typography = Typography,
    content = content
  )
}
