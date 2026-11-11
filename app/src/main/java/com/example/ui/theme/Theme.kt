package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LexiFillColorScheme =
  darkColorScheme(
    primary = SkyCyan,
    onPrimary = IndigoNight,
    primaryContainer = IndigoElevated,
    onPrimaryContainer = WashiCream,
    secondary = KyotoGold,
    onSecondary = IndigoNight,
    secondaryContainer = Color(0xFF3D3118),
    onSecondaryContainer = KyotoGoldSoft,
    tertiary = CeladonJade,
    onTertiary = IndigoNight,
    background = IndigoNight,
    onBackground = WashiCream,
    surface = IndigoSurface,
    onSurface = WashiCream,
    surfaceVariant = IndigoCard,
    onSurfaceVariant = WashiMuted,
    error = Color(0xFFFF6B6B),
    onError = Color.White,
  )

@Composable
fun LexiFillTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = LexiFillColorScheme,
    typography = Typography,
    content = content,
  )
}

@Composable
fun OrigamiWordBlitzTheme(content: @Composable () -> Unit) {
  LexiFillTheme(content = content)
}
