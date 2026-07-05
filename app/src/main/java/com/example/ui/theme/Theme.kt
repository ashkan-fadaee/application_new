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

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GeoDarkPrimary,
    secondary = GeoDarkSecondary,
    background = GeoDarkBg,
    surface = GeoDarkSurface,
    onPrimary = GeoDarkOnPrimary,
    onSecondary = GeoDarkOnSecondary,
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    outline = GeoDarkOutline,
    surfaceVariant = GeoDarkSearchBg,
    onSurfaceVariant = GeoDarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = GeoLightPrimary,
    secondary = GeoLightSecondary,
    background = GeoLightBg,
    surface = GeoLightSurface,
    onPrimary = GeoLightOnPrimary,
    onSecondary = GeoLightOnSecondary,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    outline = GeoLightOutline,
    surfaceVariant = GeoLightSearchBg,
    onSurfaceVariant = GeoLightOnSurfaceVariant
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
