package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CyberPrimary,
    onPrimary = CyberOnPrimary,
    secondary = CyberSubtext,
    tertiary = CyanAccent,
    background = CyberBg,
    surface = CyberCard,
    onBackground = CyberText,
    onSurface = CyberText,
    outline = CyberBorder
  )

private val LightColorScheme = DarkColorScheme // Keep it dark for cyber consistency

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for professional cyber dashboard feel
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve branding colors
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
