package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
  primary = Color(0xFF252420),
  onPrimary = Color(0xFFFCFAF5),
  primaryContainer = Color(0xFFEEEBE3),
  onPrimaryContainer = Color(0xFF252420),
  secondary = Color(0xFF875B19),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFF3E5CC),
  onSecondaryContainer = Color(0xFF49310F),
  background = Color(0xFFFAF8F3),
  onBackground = Color(0xFF22211E),
  surface = Color(0xFFFAF8F3),
  onSurface = Color(0xFF22211E),
  surfaceVariant = Color(0xFFEEEBE3),
  onSurfaceVariant = Color(0xFF655F55),
  surfaceTint = Color(0xFF252420),
  surfaceContainerLowest = Color(0xFFFFFDFA),
  surfaceContainerLow = Color(0xFFF7F4ED),
  surfaceContainer = Color(0xFFF1EEE6),
  surfaceContainerHigh = Color(0xFFEEEBE3),
  surfaceContainerHighest = Color(0xFFEAE6DC),
  outline = Color(0xFF817A6D),
  outlineVariant = Color(0xFFE4E0D6)
)

private val DarkColors = darkColorScheme(
  primary = Color(0xFFE9E4D9),
  onPrimary = Color(0xFF211F1A),
  primaryContainer = Color(0xFF332F28),
  onPrimaryContainer = Color(0xFFF4F0E7),
  secondary = Color(0xFFE0B568),
  onSecondary = Color(0xFF38290F),
  secondaryContainer = Color(0xFF4B3A1D),
  onSecondaryContainer = Color(0xFFF2DCA8),
  background = Color(0xFF211F1A),
  onBackground = Color(0xFFF4F0E7),
  surface = Color(0xFF211F1A),
  onSurface = Color(0xFFF4F0E7),
  surfaceVariant = Color(0xFF332F28),
  onSurfaceVariant = Color(0xFFBDB6A8),
  surfaceTint = Color(0xFFE9E4D9),
  surfaceContainerLowest = Color(0xFF191713),
  surfaceContainerLow = Color(0xFF26231E),
  surfaceContainer = Color(0xFF2B2822),
  surfaceContainerHigh = Color(0xFF332F28),
  surfaceContainerHighest = Color(0xFF3D382F),
  outline = Color(0xFF938B7D),
  outlineVariant = Color(0xFF494339)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = Typography,
    shapes = Shapes(
      extraSmall = RoundedCornerShape(6.dp),
      small = RoundedCornerShape(12.dp),
      medium = RoundedCornerShape(18.dp),
      large = RoundedCornerShape(24.dp),
      extraLarge = RoundedCornerShape(28.dp)
    ),
    content = content
  )
}
