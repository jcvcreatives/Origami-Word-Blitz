package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CrosswordOrigamiPaperScheme =
  lightColorScheme(
    primary = SkyCyan,
    onPrimary = Color(0xFFFFFCF5),
    primaryContainer = IndigoCard,
    onPrimaryContainer = WashiCream,
    secondary = KyotoGold,
    onSecondary = Color(0xFFFFFCF5),
    secondaryContainer = Color(0xFFF7E9C8),
    onSecondaryContainer = WashiCream,
    tertiary = CeladonJade,
    onTertiary = Color(0xFFFFFCF5),
    background = IndigoNight,
    onBackground = WashiCream,
    surface = IndigoSurface,
    onSurface = WashiCream,
    surfaceVariant = IndigoCard,
    onSurfaceVariant = WashiMuted,
    outline = IndigoElevated,
    error = VermilionRed,
    onError = Color.White,
  )

@Composable
fun LexiFillTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = CrosswordOrigamiPaperScheme,
    typography = Typography,
    content = content,
  )
}

@Composable
fun OrigamiWordBlitzTheme(content: @Composable () -> Unit) {
  LexiFillTheme(content = content)
}
