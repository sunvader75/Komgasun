package com.sunvader.komgasun.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SlateBlue80,
    onPrimary = SlateBlue20,
    primaryContainer = SlateBlue30,
    onPrimaryContainer = SlateBlue90,
    secondary = MutedSlate80,
    onSecondary = MutedSlate20,
    secondaryContainer = MutedSlate30,
    onSecondaryContainer = MutedSlate90,
    tertiary = SoftMauve80,
    onTertiary = SoftMauve20,
    tertiaryContainer = SoftMauve30,
    onTertiaryContainer = SoftMauve90,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SlateBlue40,
    onPrimary = Color.White,
    primaryContainer = SlateBlue90,
    onPrimaryContainer = SlateBlue10,
    secondary = MutedSlate40,
    onSecondary = Color.White,
    secondaryContainer = MutedSlate90,
    onSecondaryContainer = MutedSlate10,
    tertiary = SoftMauve40,
    onTertiary = Color.White,
    tertiaryContainer = SoftMauve90,
    onTertiaryContainer = SoftMauve10,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
  )

/**
 * Aplica al contenido la paleta de la aplicación y su tipografía Material.
 *
 * [darkTheme] sigue el modo del sistema por defecto. Si [dynamicColor] está activado,
 * usa los colores del dispositivo en Android 12 o posterior; en versiones anteriores
 * mantiene la paleta propia clara u oscura. Por defecto no usa colores dinámicos.
 * [content] se compone dentro de este tema, sin modificar el modo del sistema.
 */
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set to false to ensure a consistent, branded theme across phones and tablets
  dynamicColor: Boolean = false,
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
